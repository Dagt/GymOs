package com.gymos.inventory.application.query;

public record InventoryHistorySearchCriteria(int page, int size, boolean activeOnly) {
	public InventoryHistorySearchCriteria {
		page = Math.max(0, page);
		size = Math.min(100, Math.max(1, size));
	}
}
