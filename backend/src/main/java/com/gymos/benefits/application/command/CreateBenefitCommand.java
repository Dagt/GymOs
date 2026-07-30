package com.gymos.benefits.application.command;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record CreateBenefitCommand(
		UUID branchId,
		String code,
		String name,
		String benefitType,
		String discountType,
		Integer discountValueCents,
		Double discountPercent,
		boolean requiresEvidence,
		boolean combinable,
		int priority,
		LocalDate validFrom,
		LocalDate validUntil,
		String status,
		List<UUID> planIds
) {
	public CreateBenefitCommand {
		planIds = planIds == null ? List.of() : List.copyOf(planIds);
	}
}
