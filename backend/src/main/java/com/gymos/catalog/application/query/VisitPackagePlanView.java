package com.gymos.catalog.application.query;

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
		List<UUID> benefitIds,
		boolean selfServiceAcquisitionEnabled,
		String registrationFeePolicy,
		String status,
		long version,
		OffsetDateTime createdAt,
		OffsetDateTime updatedAt
) {
	public VisitPackagePlanView {
		branchIds = branchIds == null ? List.of() : List.copyOf(branchIds);
		benefitIds = benefitIds == null ? List.of() : List.copyOf(benefitIds);
	}
}
