package com.gymos.catalog.application.command;

public record UpdateMembershipPlanCommand(
		String name,
		String description,
		long priceCents,
		String currency,
		int durationDays,
		int graceDays,
		String benefits,
		boolean multiBranchAccess,
		String status
) {
}
