package com.gymos.memberships.api.operations;

public record RenewMembershipOperationCommand(
		String method,
		String reference,
		boolean validateNow,
		String notes,
		String idempotencyKey
) {
}
