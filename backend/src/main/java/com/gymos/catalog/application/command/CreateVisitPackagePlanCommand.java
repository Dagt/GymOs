package com.gymos.catalog.application.command;

import com.gymos.catalog.api.VisitPackageBenefitUsage;
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
		List<VisitPackageBenefitUsage> benefits,
		boolean selfServiceAcquisitionEnabled,
		String registrationFeePolicy,
		String status
) {
	public CreateVisitPackagePlanCommand {
		branchIds = branchIds == null ? List.of() : List.copyOf(branchIds);
		benefits = benefits == null ? List.of() : List.copyOf(benefits);
	}
}
