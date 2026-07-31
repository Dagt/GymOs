package com.gymos.sales.api.operations;

import java.util.List;
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
		long listPriceCents,
		long discountCents,
		long totalCents,
		String currency,
		List<MembershipBenefitSnapshot> appliedBenefits,
		String method,
		String reference,
		boolean validateNow,
		String notes,
		String idempotencyKey,
		String correlationId
) {
}
