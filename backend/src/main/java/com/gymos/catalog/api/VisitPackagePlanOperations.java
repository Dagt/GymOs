package com.gymos.catalog.api;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VisitPackagePlanOperations {
	record BenefitCoverage(UUID benefitId, List<UUID> branchIds) {
		public BenefitCoverage {
			branchIds = branchIds == null ? List.of() : List.copyOf(branchIds);
		}
	}

	Optional<VisitPackagePlanSnapshot> activePlan(UUID organizationId, UUID branchId, UUID planId);
	List<VisitPackagePlanSnapshot> selfServicePlans(UUID organizationId, UUID branchId);

	record VisitPackagePlanSnapshot(
			UUID id,
			UUID productId,
			String sku,
			String name,
			long priceCents,
			String currency,
			int totalVisits,
			int validityDays,
			String branchScope,
			List<UUID> branchIds,
			List<UUID> benefitIds,
			List<BenefitCoverage> benefitCoverages,
			boolean selfServiceAcquisitionEnabled,
			String registrationFeePolicy
	) {
		public VisitPackagePlanSnapshot {
			branchIds = branchIds == null ? List.of() : List.copyOf(branchIds);
			benefitIds = benefitIds == null ? List.of() : List.copyOf(benefitIds);
			benefitCoverages = benefitCoverages == null ? List.of() : List.copyOf(benefitCoverages);
		}
	}
}
