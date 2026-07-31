package com.gymos.sales.model;

import java.time.OffsetDateTime;
import java.util.UUID;

public record SaleHeader(
		UUID id,
		String saleNumber,
		UUID branchId,
		UUID clientId,
		String clientName,
		String status,
		String businessContext,
		long subtotalCents,
		long discountCents,
		long totalCents,
		long paidCents,
		long balanceCents,
		String currency,
		String notes,
		OffsetDateTime soldAt,
		OffsetDateTime createdAt,
		OffsetDateTime updatedAt
) {
}
