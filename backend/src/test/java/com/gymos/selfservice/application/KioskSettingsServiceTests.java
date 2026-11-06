package com.gymos.selfservice.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymos.audit.api.AuditService;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.payments.api.PaymentConfigurationOperations;
import com.gymos.payments.api.PaymentConfigurationView;
import com.gymos.selfservice.infrastructure.SelfServiceRepository;
import com.gymos.selfservice.infrastructure.SelfServiceRepository.BranchKioskConfigurationData;
import com.gymos.selfservice.infrastructure.SelfServiceRepository.BranchKioskConfigurationRecord;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class KioskSettingsServiceTests {
	private final SelfServiceRepository repository = mock(SelfServiceRepository.class);
	private final PaymentConfigurationOperations paymentConfiguration = mock(PaymentConfigurationOperations.class);
	private final KioskSettingsService service = new KioskSettingsService(
			repository,
			new SelfServiceCapabilityPolicy(new ObjectMapper()),
			paymentConfiguration,
			mock(AuditService.class)
	);
	private final UUID organizationId = UUID.randomUUID();
	private final UUID branchId = UUID.randomUUID();
	private final GymOsPrincipal principal = new GymOsPrincipal(
			UUID.randomUUID(), organizationId, branchId, "owner", "Owner", Set.of("selfservice.settings.manage")
	);

	@Test
	void configuredPaymentCapabilitiesArePersistedWhileStripeIsPending() {
		when(paymentConfiguration.configuration(organizationId, branchId)).thenReturn(
				new PaymentConfigurationView(false, false, null, null, false, false, false, null, false)
		);
		when(repository.configureBranch(eq(organizationId), eq(branchId), eq(principal.userId()), eq(false), any()))
				.thenReturn(true);
		when(repository.branchConfigurations(organizationId, principal.userId(), false)).thenReturn(List.of(
				new BranchKioskConfigurationRecord(
						branchId,
						"Centro",
						"ACTIVE",
						"""
						{"clientRegistration":true,"membershipEnrollment":true,"visitPackagePurchase":true,"membershipRenewal":true,
						 "membershipPlanChange":true,"resourceBooking":true,"wellnessBooking":false,
						 "profileUpdate":true,"autopay":true}
						""",
						OffsetDateTime.now()
				)
		));

		var result = service.configure(
				principal,
				branchId,
				new KioskSettingsService.UpdateKioskSettings(true, true, true, true, true, true, false, true, true),
				"correlation"
		);

		ArgumentCaptor<BranchKioskConfigurationData> persisted =
				ArgumentCaptor.forClass(BranchKioskConfigurationData.class);
		verify(repository).configureBranch(
				eq(organizationId), eq(branchId), eq(principal.userId()), eq(false), persisted.capture());
		assertThat(persisted.getValue().clientRegistration()).isTrue();
		assertThat(persisted.getValue().membershipEnrollment()).isTrue();
		assertThat(persisted.getValue().visitPackagePurchase()).isTrue();
		assertThat(persisted.getValue().membershipRenewal()).isTrue();
		assertThat(persisted.getValue().membershipPlanChange()).isTrue();
		assertThat(persisted.getValue().autopay()).isTrue();
		assertThat(result.capabilities().membershipEnrollmentAllowed()).isTrue();
		assertThat(result.capabilities().visitPackagePurchaseAllowed()).isTrue();
		assertThat(result.capabilities().autopayAllowed()).isTrue();
		assertThat(result.capabilities().onlineCardPaymentsReady()).isFalse();
		assertThat(result.capabilities().recurringCardPaymentsReady()).isFalse();
	}
}
