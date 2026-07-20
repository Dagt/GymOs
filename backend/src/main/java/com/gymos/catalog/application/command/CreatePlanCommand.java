package com.gymos.catalog.application.command;

import java.util.UUID;

public record CreatePlanCommand(
		UUID productId,
		int durationDays,
		int graceDays,
		String benefitsJson,
		boolean multiBranchAccess,
		String status
) {
}
