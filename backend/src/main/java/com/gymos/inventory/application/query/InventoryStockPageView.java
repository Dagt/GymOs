package com.gymos.inventory.application.query;

import java.util.List;

public record InventoryStockPageView(
		List<InventoryStockView> items,
		int page,
		int size,
		long totalElements,
		int totalPages
) {
	public static InventoryStockPageView of(List<InventoryStockView> items, int page, int size, long total) {
		int pages = total == 0 ? 0 : (int) Math.ceil((double) total / size);
		return new InventoryStockPageView(items, page, size, total, pages);
	}
}
