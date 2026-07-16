package com.gymos.memberships.api.operations;

import java.util.UUID;

public record MembershipSaleOperationResult(
		UUID saleId,
		String saleNumber,
		String saleStatus,
		long totalCents,
		long paidCents,
		long balanceCents,
		String currency,
		MembershipSummaryOperationResult membership
) {
}
