package com.gymos.sales.model;

public record SaleBusinessContextBreakdown(
		String businessContext,
		long saleCount,
		long subtotalCents,
		long discountCents,
		long totalCents,
		long paidCents,
		long balanceCents
) {
}
