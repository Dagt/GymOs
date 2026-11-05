package com.gymos.visitpackages.application.port;

import com.gymos.catalog.api.VisitPackageBenefitUsageMode;
import com.gymos.visitpackages.api.operations.VisitPackageOperations.VisitPackageEntitlementView;
import com.gymos.visitpackages.api.operations.VisitPackageAccessOperations.VisitPackageAccessResult;
import com.gymos.visitpackages.api.operations.VisitPackageAccessOperations.VisitRestoration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VisitPackageEntitlementPort {
	record BenefitCoverage(
			UUID benefitId,
			VisitPackageBenefitUsageMode usageMode,
			List<UUID> branchIds
	) {
		public BenefitCoverage {
			branchIds = branchIds == null ? List.of() : List.copyOf(branchIds);
		}
	}

	boolean lockActiveClient(UUID organizationId, UUID clientId);
	void expireDue(UUID organizationId, UUID clientId, LocalDate today, OffsetDateTime now);
	boolean hasActiveLegacyPass(UUID organizationId, UUID clientId, LocalDate today);
	Optional<VisitPackageEntitlementView> open(UUID organizationId, UUID clientId);
	List<VisitPackageEntitlementView> history(UUID organizationId, UUID clientId, int limit);
	Optional<VisitPackageEntitlementView> bySale(UUID organizationId, UUID saleId);
	Optional<VisitPackageEntitlementView> byIdempotencyKey(
			UUID organizationId, UUID clientId, String idempotencyKey);
	UUID createPending(
			UUID organizationId, UUID homeBranchId, UUID clientId, UUID packagePlanId,
			UUID saleId, String packageName, int totalVisits, int validityDays,
			OffsetDateTime pendingExpiresAt, List<UUID> branchIds, List<BenefitCoverage> benefitCoverages,
			OffsetDateTime now);
	boolean activateBySale(UUID organizationId, UUID saleId, LocalDate validFrom, OffsetDateTime now);
	Optional<ActivationDefinition> activationDefinition(UUID organizationId, UUID saleId);
	LocalDate businessDate(UUID organizationId, UUID branchId, OffsetDateTime occurredAt);
	Optional<VisitPackageAccessResult> authorizeForAccess(
			UUID organizationId, UUID branchId, UUID clientId, List<UUID> requiredBenefitIds,
			UUID accessDeviceId, String idempotencyKey, LocalDate businessDate, OffsetDateTime occurredAt);
	boolean linkAccessLog(
			UUID organizationId, UUID usageId, VisitPackageBenefitUsageMode usageMode, UUID accessLogId);

	Optional<VisitRestoration> reverseConsumption(
			UUID organizationId, UUID branchId, UUID clientId, UUID accessLogId,
			String reason, LocalDate businessDate, OffsetDateTime now);

	record ActivationDefinition(UUID entitlementId, int validityDays, String status) {}
}
