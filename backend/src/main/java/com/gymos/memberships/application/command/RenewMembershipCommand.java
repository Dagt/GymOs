package com.gymos.memberships.application.command;

public record RenewMembershipCommand(
		String method,
		String reference,
		boolean validateNow,
		String notes,
		String idempotencyKey
) {
}
