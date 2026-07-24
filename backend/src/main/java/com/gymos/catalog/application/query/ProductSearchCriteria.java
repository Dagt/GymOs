package com.gymos.catalog.application.query;

import java.util.UUID;

public record ProductSearchCriteria(
		String query,
		String productType,
		UUID categoryId,
		String status,
		Boolean inventoryTracked,
		boolean includeArchived,
		int page,
		int size,
		String sort
) {
	public ProductSearchCriteria {
		page = Math.max(page, 0);
		size = Math.min(Math.max(size, 1), 100);
		sort = sort == null || sort.isBlank() ? "displayOrder,asc" : sort;
	}
}
