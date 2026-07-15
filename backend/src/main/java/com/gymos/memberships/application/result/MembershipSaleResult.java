package com.gymos.memberships.application.result;

import java.util.UUID;

public record MembershipSaleResult(
		UUID saleId,
		String saleNumber,
		String saleStatus,
		long totalCents,
		long paidCents,
		long balanceCents,
		String currency,
		MembershipSummaryResult membership
) {
}
