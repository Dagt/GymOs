package com.gymos.memberships.application.command;

import java.util.UUID;

public record ChangeMembershipPlanCommand(
		UUID targetPlanId,
		String method,
		String reference,
		boolean validateNow,
		String notes,
		String idempotencyKey
) {
}
