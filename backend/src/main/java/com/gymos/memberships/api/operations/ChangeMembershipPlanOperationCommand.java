package com.gymos.memberships.api.operations;

import java.util.UUID;

public record ChangeMembershipPlanOperationCommand(
		UUID targetPlanId,
		String method,
		String reference,
		boolean validateNow,
		String notes,
		String idempotencyKey
) {
}
