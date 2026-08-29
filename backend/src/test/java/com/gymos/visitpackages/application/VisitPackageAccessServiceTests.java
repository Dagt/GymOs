package com.gymos.visitpackages.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gymos.catalog.api.VisitPackageBenefitUsageMode;
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
		UUID usageId = UUID.randomUUID();
		UUID entitlementId = UUID.randomUUID();
		VisitPackageAccessResult expected = new VisitPackageAccessResult(
				usageId, usageId, benefitId, VisitPackageBenefitUsageMode.CONSUMES_VISIT,
				entitlementId, clientId, branchId, deviceId, "10 visitas", 9, true, null);
		when(repository.businessDate(organizationId, branchId, now)).thenReturn(today);
		when(repository.authorizeForAccess(
				organizationId, branchId, clientId, List.of(benefitId), deviceId, "door-event-1", today, now))
				.thenReturn(Optional.of(expected));

		assertThat(service.authorizeForAccess(
				organizationId, branchId, clientId, List.of(benefitId), deviceId,
				" door-event-1 ", now)).contains(expected);
		verify(repository).businessDate(organizationId, branchId, now);
	}

	@Test
	void rejectsAConsumptionWithoutAnIdempotencyKey() {
		assertThatThrownBy(() -> service.authorizeForAccess(
				UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), List.of(), UUID.randomUUID(),
				" ", OffsetDateTime.now()))
				.isInstanceOfSatisfying(
						BusinessException.class,
						error -> assertThat(error.code()).isEqualTo("VISIT_PACKAGE_ACCESS_KEY_INVALID"));
	}

	@Test
	void linksTheConsumptionToItsAccessDecision() {
		UUID organizationId = UUID.randomUUID();
		UUID consumptionId = UUID.randomUUID();
		UUID accessLogId = UUID.randomUUID();
		when(repository.linkAccessLog(
				organizationId, consumptionId, VisitPackageBenefitUsageMode.CONSUMES_VISIT, accessLogId))
				.thenReturn(true);

		service.linkAccessLog(
				organizationId, consumptionId, VisitPackageBenefitUsageMode.CONSUMES_VISIT, accessLogId);

		verify(repository).linkAccessLog(
				organizationId, consumptionId, VisitPackageBenefitUsageMode.CONSUMES_VISIT, accessLogId);
	}
}
