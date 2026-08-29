package com.gymos.catalog.application.query;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.gymos.catalog.api.VisitPackageBenefitUsage;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record VisitPackagePlanView(
		UUID id,
		UUID productId,
		String sku,
		String name,
		String description,
		long priceCents,
		String currency,
		int totalVisits,
		int validityDays,
		String branchScope,
		List<UUID> branchIds,
		List<VisitPackageBenefitUsage> benefits,
		boolean selfServiceAcquisitionEnabled,
		String registrationFeePolicy,
		String status,
		long version,
		OffsetDateTime createdAt,
		OffsetDateTime updatedAt
) {
	public VisitPackagePlanView {
		branchIds = branchIds == null ? List.of() : List.copyOf(branchIds);
		benefits = benefits == null ? List.of() : List.copyOf(benefits);
	}

	@JsonProperty("benefitIds")
	public List<UUID> benefitIds() {
		return benefits.stream().map(VisitPackageBenefitUsage::benefitId).toList();
	}
}
