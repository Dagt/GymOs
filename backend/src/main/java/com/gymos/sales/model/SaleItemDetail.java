package com.gymos.sales.model;

import java.time.OffsetDateTime;
import java.util.UUID;

public record SaleItemDetail(
		UUID id,
		UUID productId,
		String itemType,
		String name,
		int quantity,
		long unitPriceCents,
		long discountCents,
		long totalCents,
		int refundedQuantity,
		OffsetDateTime createdAt
) {
	public SaleItemDetail(UUID id, UUID productId, String itemType, String name, int quantity,
			long unitPriceCents, long discountCents, long totalCents, OffsetDateTime createdAt) {
		this(id, productId, itemType, name, quantity, unitPriceCents, discountCents, totalCents, 0, createdAt);
	}

	public int refundableQuantity() {
		return Math.max(0, quantity - refundedQuantity);
	}
}
