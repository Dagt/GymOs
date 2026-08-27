package com.gymos.clients.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.gymos.audit.api.AuditService;
import com.gymos.clients.infrastructure.ClientLifecycleRepository;
import com.gymos.clients.infrastructure.ClientLifecycleRepository.ClientLifecycleConfiguration;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ClientLifecycleServiceTests {
	private final ClientLifecycleRepository repository = mock(ClientLifecycleRepository.class);
	private final ClientLifecycleService service = new ClientLifecycleService(repository, mock(AuditService.class));
	private final UUID organizationId = UUID.randomUUID();
	private final UUID clientId = UUID.randomUUID();
	private final LocalDate effectiveOn = LocalDate.of(2026, 8, 26);

	@Test
	void firstMembershipIncludesConfiguredRegistrationFee() {
		givenEnabledConfiguration(true);
		when(repository.latestMembershipEnd(organizationId, clientId)).thenReturn(Optional.empty());

		var fee = service.registrationFee(organizationId, clientId, effectiveOn);

		assertThat(fee.due()).isTrue();
		assertThat(fee.amountCents()).isEqualTo(500_00);
		assertThat(fee.label()).isEqualTo("Inscripción");
	}

	@Test
	void registrationFeeReturnsAfterConfiguredMembershipGap() {
		givenEnabledConfiguration(true);
		when(repository.latestMembershipEnd(organizationId, clientId))
				.thenReturn(Optional.of(effectiveOn.minusMonths(6)));

		assertThat(service.registrationFee(organizationId, clientId, effectiveOn).due()).isTrue();
	}

	@Test
	void recentMembershipDoesNotPayRegistrationAgain() {
		givenEnabledConfiguration(true);
		when(repository.latestMembershipEnd(organizationId, clientId))
				.thenReturn(Optional.of(effectiveOn.minusMonths(5)));

		assertThat(service.registrationFee(organizationId, clientId, effectiveOn).due()).isFalse();
	}

	@Test
	void registrationFeeUsesInactivityPeriodEvenWhenAutomaticInactivationIsDisabled() {
		givenEnabledConfiguration(false);
		when(repository.latestMembershipEnd(organizationId, clientId))
				.thenReturn(Optional.of(effectiveOn.minusMonths(6)));

		var fee = service.registrationFee(organizationId, clientId, effectiveOn);

		assertThat(fee.due()).isTrue();
		assertThat(fee.inactivityMonths()).isEqualTo(6);
	}

	private void givenEnabledConfiguration(boolean autoInactivationEnabled) {
		when(repository.configuration(organizationId)).thenReturn(Optional.of(
				new ClientLifecycleConfiguration(
						organizationId, true, 500_00, "Inscripción", "MXN",
						autoInactivationEnabled, 6, null, 0
				)
		));
	}
}
