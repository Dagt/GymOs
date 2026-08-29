package com.gymos.visitpackages.api.operations;

import com.gymos.catalog.api.VisitPackageBenefitUsageMode;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Public boundary used by access to consume a paid visit package. */
public interface VisitPackageAccessOperations {
	Optional<VisitPackageAccessResult> authorizeForAccess(
			UUID organizationId,
			UUID branchId,
			UUID clientId,
			List<UUID> requiredBenefitIds,
			UUID accessDeviceId,
			String idempotencyKey,
			OffsetDateTime occurredAt
	);

	void linkAccessLog(
			UUID organizationId,
			UUID usageId,
			VisitPackageBenefitUsageMode usageMode,
			UUID accessLogId
	);

	record VisitPackageAccessResult(
			UUID usageId,
			UUID baseConsumptionId,
			UUID benefitId,
			VisitPackageBenefitUsageMode usageMode,
			UUID entitlementId,
			UUID clientId,
			UUID branchId,
			UUID accessDeviceId,
			String packageName,
			int remainingAfter,
			boolean granted,
			UUID accessLogId
	) {
		public boolean consumedVisit() {
			return usageMode == VisitPackageBenefitUsageMode.CONSUMES_VISIT;
		}
	}
}
