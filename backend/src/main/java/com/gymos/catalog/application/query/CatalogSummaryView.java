package com.gymos.catalog.application.query;

public record CatalogSummaryView(
		long activeProducts,
		long activeServices,
		long activePlans,
		long inactiveProducts,
		long archivedProducts,
		long inventoryTrackedProducts,
		long uncategorizedProducts,
		long productsWithoutBarcode,
		long productsWithoutImage,
		long lowStockProducts
) {
}
