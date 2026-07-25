package com.gymos.inventory.application.query;

import java.util.UUID;

public record InventoryPhysicalCountItemView(
		UUID id,
		UUID productId,
		String productName,
		String sku,
		int systemQuantity,
		Integer countedQuantity,
		Integer varianceQuantity,
		Long unitCostCents,
		String notes
) {
}
