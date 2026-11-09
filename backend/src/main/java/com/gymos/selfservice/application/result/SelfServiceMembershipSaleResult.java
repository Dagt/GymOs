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
		SelfServiceMembershipSummaryResult membership,
		UUID paymentId,
		UUID providerOperationId,
		String checkoutUrl,
		String checkoutStatus,
		String providerSessionId
) {
	public SelfServiceMembershipSaleResult(UUID saleId, String saleNumber, String saleStatus, long totalCents,
			long paidCents, long balanceCents, String currency, SelfServiceMembershipSummaryResult membership) {
		this(saleId, saleNumber, saleStatus, totalCents, paidCents, balanceCents, currency, membership, null, null, null, null, null);
	}
}
