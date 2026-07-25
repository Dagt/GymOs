package com.gymos.inventory.application.query;

public record InventorySummaryView(
		long totalValuationCents,
		long totalTrackedProducts,
		long lowStockCount,
		long outOfStockCount,
		boolean allowNegativeStock
) {
}
