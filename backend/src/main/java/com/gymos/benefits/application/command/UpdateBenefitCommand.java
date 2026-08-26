package com.gymos.benefits.application.command;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Identity, type and branch are immutable; updates preserve device and member links. */
public record UpdateBenefitCommand(
		String name, String discountType, Integer discountValueCents, Double discountPercent,
		boolean requiresEvidence, boolean combinable, int priority,
		LocalDate validFrom, LocalDate validUntil, String status, List<UUID> planIds, long expectedVersion
) {
	public UpdateBenefitCommand {
		planIds = planIds == null ? List.of() : List.copyOf(planIds);
	}
}
