package com.gymos.inventory.application.query;

import java.util.List;

public record InventoryMovementPageView(
		List<InventoryMovementView> items,
		int page,
		int size,
		long totalElements,
		int totalPages
) {
	public static InventoryMovementPageView of(List<InventoryMovementView> items, int page, int size, long total) {
		int pages = total == 0 ? 0 : (int) Math.ceil((double) total / size);
		return new InventoryMovementPageView(items, page, size, total, pages);
	}
}
