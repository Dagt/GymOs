package com.gymos.access.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymos.access.application.command.ConsumeVisitPassCommand;
import com.gymos.access.application.command.CreateVisitPassCommand;
import com.gymos.access.infrastructure.AccessRepository;
import com.gymos.access.infrastructure.PhysicalAccessRepository;
import com.gymos.audit.api.AuditService;
import com.gymos.common.api.BusinessException;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.eligibility.api.EligibilityPolicy;
import com.gymos.visitpackages.api.operations.VisitPackageAccessOperations;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LegacyVisitPassRetirementTests {
	private final AccessRepository repository = mock(AccessRepository.class);
	private final AccessService service = new AccessService(
			repository, mock(AuditService.class), mock(EligibilityPolicy.class), new ObjectMapper(),
			mock(PhysicalAccessRepository.class), mock(PhysicalAccessSynchronizationService.class),
			mock(PhysicalAccessProviderCatalog.class), mock(PhysicalConnectorService.class),
			mock(VisitPackageAccessOperations.class));
	private final GymOsPrincipal principal = new GymOsPrincipal(
			UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "owner@gym.test", "Dueño", Set.of());

	@Test
	void legacyPassCreationIsReadOnly() {
		assertThatThrownBy(() -> service.createVisitPass(
				principal,
				new CreateVisitPassCommand(
						UUID.randomUUID(), null, "VISIT_PACK", 10, LocalDate.now(), LocalDate.now().plusDays(30)),
				"correlation"))
				.isInstanceOfSatisfying(BusinessException.class,
						error -> assertThat(error.code()).isEqualTo("VISIT_PASS_LEGACY_READ_ONLY"));
		verify(repository, never()).createVisitPass(
				org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
				org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
				org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt(),
				org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
	}

	@Test
	void legacyPassManualConsumptionIsReadOnly() {
		UUID passId = UUID.randomUUID();
		assertThatThrownBy(() -> service.consumeVisitPass(
				principal, passId, new ConsumeVisitPassCommand("manual"), "correlation"))
				.isInstanceOfSatisfying(BusinessException.class,
						error -> assertThat(error.code()).isEqualTo("VISIT_PASS_LEGACY_READ_ONLY"));
		verify(repository, never()).consumeVisitPass(
				org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
				org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
	}
}
