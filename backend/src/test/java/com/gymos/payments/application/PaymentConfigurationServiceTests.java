package com.gymos.payments.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gymos.audit.api.AuditService;
import com.gymos.common.api.BusinessException;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.payments.api.PaymentConfigurationView;
import com.gymos.payments.infrastructure.PaymentConfigurationRepository;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PaymentConfigurationServiceTests {
	private final PaymentConfigurationRepository repository = mock(PaymentConfigurationRepository.class);
	private final PaymentConfigurationService service = new PaymentConfigurationService(repository, mock(AuditService.class));
	private final UUID organizationId = UUID.randomUUID();

	@Test
	void cannotEnableMercadoPagoBeforeAccountIsConnected() {
		when(repository.configuration(organizationId)).thenReturn(configuration(true, false));
		var principal = new GymOsPrincipal(
				UUID.randomUUID(), organizationId, UUID.randomUUID(), "owner", "Owner", Set.of("payment.settings.manage")
		);

		assertThatThrownBy(() -> service.update(
				principal,
				new UpdatePaymentConfigurationCommand(true, true, "POINT__123", false, false),
				"correlation"
		)).isInstanceOfSatisfying(BusinessException.class, exception ->
				org.assertj.core.api.Assertions.assertThat(exception.code()).isEqualTo("MERCADO_PAGO_NOT_CONNECTED"));
	}

	@Test
	void disabledManualTerminalBlocksCardManualRegistration() {
		when(repository.configuration(organizationId)).thenReturn(configuration(false, false));

		assertThatThrownBy(() -> service.requireReceptionMethod(organizationId, "CARD_MANUAL"))
				.isInstanceOfSatisfying(BusinessException.class, exception ->
						org.assertj.core.api.Assertions.assertThat(exception.code()).isEqualTo("RECEPTION_MANUAL_CARD_DISABLED"));
	}

	@Test
	void recurringBillingCanBeEnabledAfterStripeCheckoutIsConnected() {
		when(repository.configuration(organizationId)).thenReturn(
				new PaymentConfigurationView(
						true, false, null, null, false, true, false, "acct_test", true
				),
				new PaymentConfigurationView(
						true, false, null, null, false, true, true, "acct_test", true
				)
		);
		var principal = new GymOsPrincipal(
				UUID.randomUUID(), organizationId, UUID.randomUUID(), "owner", "Owner", Set.of("payment.settings.manage")
		);

		PaymentConfigurationView result = service.update(
				principal,
				new UpdatePaymentConfigurationCommand(true, false, null, true, true),
				"correlation"
		);

		assertThat(result.stripeRecurringReady()).isTrue();
		verify(repository).update(
				organizationId, principal.userId(),
				new UpdatePaymentConfigurationCommand(true, false, null, true, true)
		);
	}

	private PaymentConfigurationView configuration(boolean manualEnabled, boolean mercadoPagoConnected) {
		return new PaymentConfigurationView(
				manualEnabled, false, null, null, mercadoPagoConnected,
				false, false, null, false
		);
	}
}
