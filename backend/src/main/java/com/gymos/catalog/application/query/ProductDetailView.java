package com.gymos.catalog.application.query;

import java.util.List;

public record ProductDetailView(
		ProductSummaryView product,
		ProductDependencyView dependencies,
		List<ProductHistoryEntryView> history
) {
}
