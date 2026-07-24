package com.gymos.inventory.application.query;

import java.util.UUID;

public record InventoryMovementSearchCriteria(
		UUID productId,
		int page,
		int size
) {
	public InventoryMovementSearchCriteria {
		page = Math.max(page, 0);
		size = Math.min(Math.max(size, 1), 100);
	}
}
