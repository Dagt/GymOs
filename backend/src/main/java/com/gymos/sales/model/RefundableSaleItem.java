package com.gymos.sales.model;

import java.util.UUID;

public record RefundableSaleItem(
		UUID saleItemId,
		UUID productId,
		String itemType,
		String name,
		int soldQuantity,
		int refundedQuantity
) {
	public int refundableQuantity() {
		return Math.max(0, soldQuantity - refundedQuantity);
	}
}
