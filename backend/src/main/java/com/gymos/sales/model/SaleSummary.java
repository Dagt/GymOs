package com.gymos.sales.model;

import java.time.OffsetDateTime;
import java.util.UUID;

public record SaleSummary(
		UUID id,
		String saleNumber,
		UUID clientId,
		String clientName,
		String status,
		String businessContext,
		long totalCents,
		long paidCents,
		long balanceCents,
		String currency,
		OffsetDateTime soldAt
) {
}
