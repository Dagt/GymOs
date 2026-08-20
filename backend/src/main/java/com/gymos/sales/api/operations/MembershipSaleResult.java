package com.gymos.sales.api.operations;

import java.util.UUID;

public record MembershipSaleResult(
		UUID saleId,
		String saleNumber,
		String saleStatus,
		long totalCents,
		long paidCents,
		long balanceCents,
		String currency,
		boolean paymentCompleted,
		UUID paymentId
) {
	public MembershipSaleResult(UUID saleId, String saleNumber, String saleStatus, long totalCents,
			long paidCents, long balanceCents, String currency, boolean paymentCompleted) {
		this(saleId, saleNumber, saleStatus, totalCents, paidCents, balanceCents, currency, paymentCompleted, null);
	}
}
