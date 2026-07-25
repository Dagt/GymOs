package com.gymos.inventory.application.query;

import java.util.UUID;

public record InventoryTransferItemView(
		UUID id,
		UUID productId,
		String productName,
		String sku,
		int requestedQuantity,
		Integer shippedQuantity,
		Integer receivedQuantity,
		Long unitCostCents
) {
}
