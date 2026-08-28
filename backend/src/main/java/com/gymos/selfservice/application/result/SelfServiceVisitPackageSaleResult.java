package com.gymos.selfservice.application.result;

import java.util.UUID;

public record SelfServiceVisitPackageSaleResult(
		UUID entitlementId,
		UUID saleId,
		UUID paymentId,
		String saleNumber,
		String saleStatus,
		String entitlementStatus,
		long totalCents,
		long paidCents,
		long balanceCents,
		String currency,
		UUID providerOperationId,
		String checkoutUrl,
		String checkoutStatus
) {}
