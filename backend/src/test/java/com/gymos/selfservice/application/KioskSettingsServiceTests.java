package com.gymos.selfservice.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.gymos.audit.api.AuditService;
import com.gymos.common.api.BusinessException;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.payments.api.PaymentConfigurationOperations;
import com.gymos.payments.api.PaymentConfigurationView;
import com.gymos.selfservice.infrastructure.SelfServiceRepository;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class KioskSettingsServiceTests {
	private final SelfServiceRepository repository = mock(SelfServiceRepository.class);
	private final PaymentConfigurationOperations paymentConfiguration = mock(PaymentConfigurationOperations.class);
	private final KioskSettingsService service = new KioskSettingsService(
			repository,
			mock(SelfServiceCapabilityPolicy.class),
			paymentConfiguration,
			mock(AuditService.class)
	);
	private final UUID organizationId = UUID.randomUUID();
	private final UUID branchId = UUID.randomUUID();
	private final GymOsPrincipal principal = new GymOsPrincipal(
			UUID.randomUUID(), organizationId, branchId, "owner", "Owner", Set.of("selfservice.settings.manage")
	);

	@Test
	void paymentCapabilitiesRequireStripeSelfCheckout() {
		when(paymentConfiguration.configuration(organizationId)).thenReturn(
				new PaymentConfigurationView(false, false, null, null, false, false, false, null, false)
		);

		assertThatThrownBy(() -> service.configure(
				principal,
				branchId,
				new KioskSettingsService.UpdateKioskSettings(false, true, false, false, false, false, false, false),
				"correlation"
		)).isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.code()).isEqualTo("KIOSK_STRIPE_CHECKOUT_REQUIRED"));
	}

	@Test
	void autopayCapabilityRequiresStripeRecurringBilling() {
		when(paymentConfiguration.configuration(organizationId)).thenReturn(
				new PaymentConfigurationView(false, false, null, null, false, true, false, "acct_test", true)
		);

		assertThatThrownBy(() -> service.configure(
				principal,
				branchId,
				new KioskSettingsService.UpdateKioskSettings(false, false, false, false, false, false, false, true),
				"correlation"
		)).isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.code()).isEqualTo("KIOSK_STRIPE_RECURRING_REQUIRED"));
	}
}
