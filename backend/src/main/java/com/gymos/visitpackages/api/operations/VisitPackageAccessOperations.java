package com.gymos.visitpackages.api.operations;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Public boundary used by access to consume a paid visit package. */
public interface VisitPackageAccessOperations {
	Optional<VisitPackageAccessResult> consumeForAccess(
			UUID organizationId,
			UUID branchId,
			UUID clientId,
			List<UUID> requiredBenefitIds,
			UUID accessDeviceId,
			String idempotencyKey,
			LocalDate businessDate,
			OffsetDateTime occurredAt
	);

	void linkAccessLog(UUID organizationId, UUID consumptionId, UUID accessLogId);

	record VisitPackageAccessResult(
			UUID consumptionId,
			UUID entitlementId,
			UUID clientId,
			UUID branchId,
			UUID accessDeviceId,
			String packageName,
			int remainingAfter,
			UUID accessLogId
	) {}
}
