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
		MembershipSummaryOperationResult membership,
		UUID paymentId
) {
	public MembershipSaleOperationResult(UUID saleId, String saleNumber, String saleStatus, long totalCents,
			long paidCents, long balanceCents, String currency, MembershipSummaryOperationResult membership) {
		this(saleId, saleNumber, saleStatus, totalCents, paidCents, balanceCents, currency, membership, null);
	}
}
