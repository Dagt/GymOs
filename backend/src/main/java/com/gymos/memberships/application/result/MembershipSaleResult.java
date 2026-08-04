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
		MembershipSummaryResult membership,
		UUID paymentId
) {
	public MembershipSaleResult(UUID saleId, String saleNumber, String saleStatus, long totalCents,
			long paidCents, long balanceCents, String currency, MembershipSummaryResult membership) {
		this(saleId, saleNumber, saleStatus, totalCents, paidCents, balanceCents, currency, membership, null);
	}
}
