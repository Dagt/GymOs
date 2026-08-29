package com.gymos.catalog.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VisitPackagePlanOperations {
	record BenefitCoverage(
			UUID benefitId,
			VisitPackageBenefitUsageMode usageMode,
			List<UUID> branchIds
	) {
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
			List<VisitPackageBenefitUsage> benefits,
			List<BenefitCoverage> benefitCoverages,
			boolean selfServiceAcquisitionEnabled,
			String registrationFeePolicy
	) {
		public VisitPackagePlanSnapshot {
			branchIds = branchIds == null ? List.of() : List.copyOf(branchIds);
			benefits = benefits == null ? List.of() : List.copyOf(benefits);
			benefitCoverages = benefitCoverages == null ? List.of() : List.copyOf(benefitCoverages);
		}

		@JsonProperty("benefitIds")
		public List<UUID> benefitIds() {
			return benefits.stream().map(VisitPackageBenefitUsage::benefitId).toList();
		}
	}
}
