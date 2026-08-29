package com.gymos.visitpackages.api.operations;

import com.gymos.common.security.GymOsPrincipal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VisitPackageOperations {
	VisitPackageQuoteView quote(GymOsPrincipal principal, UUID clientId, UUID packagePlanId);
	VisitPackagePurchaseResult purchase(
			GymOsPrincipal principal, PurchaseVisitPackageCommand command, String correlationId);
	Optional<VisitPackageEntitlementView> current(
			GymOsPrincipal principal, UUID clientId);
	List<VisitPackageEntitlementView> history(
			GymOsPrincipal principal, UUID clientId, int limit);

	record PurchaseVisitPackageCommand(
			UUID clientId, UUID packagePlanId, String method, String reference,
			boolean validateNow, String notes, String idempotencyKey) {}

	record VisitPackageQuoteView(
			UUID clientId, UUID packagePlanId, String packageName, int totalVisits,
			int validityDays, long packagePriceCents, long registrationFeeCents,
			String registrationFeeLabel, long totalCents, String currency,
			List<UUID> branchIds, List<UUID> benefitIds) {
		public VisitPackageQuoteView {
			branchIds = branchIds == null ? List.of() : List.copyOf(branchIds);
			benefitIds = benefitIds == null ? List.of() : List.copyOf(benefitIds);
		}
	}

	record VisitPackagePurchaseResult(
			UUID entitlementId, UUID saleId, UUID paymentId, String saleNumber,
			String saleStatus, String entitlementStatus, long totalCents,
			long paidCents, long balanceCents, String currency) {}

	record VisitPackageEntitlementView(
			UUID id, UUID clientId, UUID packagePlanId, UUID saleId, String packageName,
			String status, int totalVisits, int remainingVisits, LocalDate validFrom,
			LocalDate validUntil, OffsetDateTime pendingExpiresAt, List<UUID> branchIds,
			List<UUID> benefitIds, List<VisitPackageBenefitView> benefits,
			List<VisitPackageConsumptionView> consumptions,
			OffsetDateTime createdAt, OffsetDateTime updatedAt) {
		public VisitPackageEntitlementView {
			branchIds = branchIds == null ? List.of() : List.copyOf(branchIds);
			benefitIds = benefitIds == null ? List.of() : List.copyOf(benefitIds);
			benefits = benefits == null ? List.of() : List.copyOf(benefits);
			consumptions = consumptions == null ? List.of() : List.copyOf(consumptions);
		}
	}

	record VisitPackageBenefitView(
			UUID benefitId, String code, String name, String usageMode, List<UUID> branchIds) {
		public VisitPackageBenefitView {
			branchIds = branchIds == null ? List.of() : List.copyOf(branchIds);
		}
	}

	record VisitPackageConsumptionView(
			UUID id, String status, OffsetDateTime occurredAt, UUID branchId,
			String branchName, UUID accessDeviceId, String deviceName, int remainingAfter) {}
}
