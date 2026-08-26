package com.gymos.inventory.application.query;

public record InventoryStockSearchCriteria(
		String query,
		int page,
		int size,
		String sort,
		boolean lowStockOnly
) {
	public InventoryStockSearchCriteria(String query, int page, int size, String sort) {
		this(query, page, size, sort, false);
	}

	public InventoryStockSearchCriteria {
		page = Math.max(page, 0);
		size = Math.min(Math.max(size, 1), 100);
		sort = sort == null || sort.isBlank() ? "name,asc" : sort;
	}
}
