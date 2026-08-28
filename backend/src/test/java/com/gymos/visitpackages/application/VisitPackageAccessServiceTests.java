package com.gymos.visitpackages.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gymos.common.api.BusinessException;
import com.gymos.visitpackages.api.operations.VisitPackageAccessOperations.VisitPackageAccessResult;
import com.gymos.visitpackages.application.port.VisitPackageEntitlementPort;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class VisitPackageAccessServiceTests {
	private final VisitPackageEntitlementPort repository = mock(VisitPackageEntitlementPort.class);
	private final VisitPackageAccessService service = new VisitPackageAccessService(repository);

	@Test
	void delegatesAnIdempotentConsumptionWithExactBranchAndBenefit() {
		UUID organizationId = UUID.randomUUID();
		UUID branchId = UUID.randomUUID();
		UUID clientId = UUID.randomUUID();
		UUID benefitId = UUID.randomUUID();
		UUID deviceId = UUID.randomUUID();
		LocalDate today = LocalDate.of(2026, 8, 28);
		OffsetDateTime now = OffsetDateTime.parse("2026-08-28T18:00:00Z");
		VisitPackageAccessResult expected = new VisitPackageAccessResult(
				UUID.randomUUID(), UUID.randomUUID(), clientId, branchId, deviceId, "10 visitas", 9, null);
		when(repository.consumeForAccess(
				organizationId, branchId, clientId, List.of(benefitId), deviceId, "door-event-1", today, now))
				.thenReturn(Optional.of(expected));

		assertThat(service.consumeForAccess(
				organizationId, branchId, clientId, List.of(benefitId), deviceId,
				" door-event-1 ", today, now)).contains(expected);
	}

	@Test
	void rejectsAConsumptionWithoutAnIdempotencyKey() {
		assertThatThrownBy(() -> service.consumeForAccess(
				UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), List.of(), UUID.randomUUID(),
				" ", LocalDate.now(), OffsetDateTime.now()))
				.isInstanceOfSatisfying(
						BusinessException.class,
						error -> assertThat(error.code()).isEqualTo("VISIT_PACKAGE_ACCESS_KEY_INVALID"));
	}

	@Test
	void linksTheConsumptionToItsAccessDecision() {
		UUID organizationId = UUID.randomUUID();
		UUID consumptionId = UUID.randomUUID();
		UUID accessLogId = UUID.randomUUID();
		when(repository.linkAccessLog(organizationId, consumptionId, accessLogId)).thenReturn(true);

		service.linkAccessLog(organizationId, consumptionId, accessLogId);

		verify(repository).linkAccessLog(organizationId, consumptionId, accessLogId);
	}
}
