package com.gymos.sales.api.operations;

import java.util.UUID;

public record VisitPackageSaleResult(
		UUID saleId,
		String saleNumber,
		String saleStatus,
		long totalCents,
		long paidCents,
		long balanceCents,
		String currency,
		boolean paymentCompleted,
		UUID paymentId
) {}
