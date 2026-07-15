package com.gymos.selfservice.application.result;

import java.util.UUID;

public record SelfServiceMembershipSaleResult(
		UUID saleId,
		String saleNumber,
		String saleStatus,
		long totalCents,
		long paidCents,
		long balanceCents,
		String currency,
		SelfServiceMembershipSummaryResult membership
) {
}
