package com.gymos.sales.api.operations;

import java.util.UUID;

public record CreateMembershipRenewalSaleCommand(
		UUID organizationId,
		UUID branchId,
		UUID actorUserId,
		UUID clientId,
		UUID sourceMembershipId,
		UUID planId,
		UUID productId,
		String planName,
		long priceCents,
		String currency,
		String method,
		String reference,
		boolean validateNow,
		String notes,
		String idempotencyKey,
		String correlationId
) {
}
