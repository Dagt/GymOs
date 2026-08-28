package com.gymos.visitpackages.application.port;

import com.gymos.visitpackages.api.operations.VisitPackageOperations.VisitPackageEntitlementView;
import com.gymos.visitpackages.api.operations.VisitPackageAccessOperations.VisitPackageAccessResult;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VisitPackageEntitlementPort {
	boolean lockActiveClient(UUID organizationId, UUID clientId);
	void expireDue(UUID organizationId, UUID clientId, LocalDate today, OffsetDateTime now);
	boolean hasActiveLegacyPass(UUID organizationId, UUID clientId, LocalDate today);
	Optional<VisitPackageEntitlementView> open(UUID organizationId, UUID clientId);
	Optional<VisitPackageEntitlementView> bySale(UUID organizationId, UUID saleId);
	Optional<VisitPackageEntitlementView> byIdempotencyKey(
			UUID organizationId, UUID clientId, String idempotencyKey);
	UUID createPending(
			UUID organizationId, UUID homeBranchId, UUID clientId, UUID packagePlanId,
			UUID saleId, String packageName, int totalVisits, int validityDays,
			OffsetDateTime pendingExpiresAt, List<UUID> branchIds, List<UUID> benefitIds,
			OffsetDateTime now);
	boolean activateBySale(UUID organizationId, UUID saleId, LocalDate validFrom, OffsetDateTime now);
	Optional<ActivationDefinition> activationDefinition(UUID organizationId, UUID saleId);
	LocalDate businessDate(UUID organizationId, UUID branchId, OffsetDateTime occurredAt);
	Optional<VisitPackageAccessResult> consumeForAccess(
			UUID organizationId, UUID branchId, UUID clientId, List<UUID> requiredBenefitIds,
			UUID accessDeviceId, String idempotencyKey, LocalDate businessDate, OffsetDateTime occurredAt);
	boolean linkAccessLog(UUID organizationId, UUID consumptionId, UUID accessLogId);

	record ActivationDefinition(UUID entitlementId, int validityDays, String status) {}
}
