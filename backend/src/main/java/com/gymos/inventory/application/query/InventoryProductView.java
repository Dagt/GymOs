package com.gymos.inventory.application.query;

import java.util.UUID;

public record InventoryProductView(
		UUID id,
		String sku,
		String name,
		String productType,
		boolean inventoryTracked,
		String status
) {
}
