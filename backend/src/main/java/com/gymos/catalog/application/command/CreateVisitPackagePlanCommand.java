package com.gymos.catalog.application.command;

import java.util.List;
import java.util.UUID;

public record CreateVisitPackagePlanCommand(
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
		String status
) {
	public CreateVisitPackagePlanCommand {
		branchIds = branchIds == null ? List.of() : List.copyOf(branchIds);
		benefitIds = benefitIds == null ? List.of() : List.copyOf(benefitIds);
	}
}
