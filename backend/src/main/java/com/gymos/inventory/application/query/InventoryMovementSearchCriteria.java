package com.gymos.inventory.application.query;

import java.util.UUID;

public record InventoryMovementSearchCriteria(
		UUID productId,
		int page,
		int size,
		String query,
		String type
) {
	public InventoryMovementSearchCriteria(UUID productId, int page, int size) {
		this(productId, page, size, null, null);
	}

	public InventoryMovementSearchCriteria {
		page = Math.max(page, 0);
		size = Math.min(Math.max(size, 1), 100);
		query = query == null || query.isBlank() ? null : query.trim().toLowerCase(java.util.Locale.ROOT);
		type = type == null || type.isBlank() ? null : type.trim().toUpperCase(java.util.Locale.ROOT);
	}
}
