package com.gymos.memberships.application.command;

import java.util.UUID;

public record EnrollMembershipCommand(
		UUID clientId,
		UUID planId,
		String method,
		String reference,
		boolean validateNow,
		String notes,
		String idempotencyKey
) {
}
