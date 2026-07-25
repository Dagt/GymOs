package com.gymos.inventory.application.query;

import java.time.OffsetDateTime;
import java.util.UUID;

public record InventoryStockView(
		UUID stockId,
		UUID productId,
		String sku,
		String productName,
		long priceCents,
		String currency,
		int quantityOnHand,
		int reorderPoint,
		Long averageCostCents,
		OffsetDateTime updatedAt
) {
	public boolean lowStock() {
		return reorderPoint > 0 && quantityOnHand <= reorderPoint;
	}

	public int suggestedReorderQuantity() {
		if (reorderPoint <= 0 || quantityOnHand > reorderPoint) {
			return 0;
		}
		return Math.max(0, (reorderPoint * 2) - quantityOnHand);
	}
}
