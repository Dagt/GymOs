package com.gymos.catalog.application.query;

import java.util.List;

public record ProductPageView(
		List<ProductSummaryView> items,
		int page,
		int size,
		long totalElements,
		int totalPages
) {
	public static ProductPageView of(List<ProductSummaryView> items, int page, int size, long totalElements) {
		int totalPages = totalElements == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
		return new ProductPageView(List.copyOf(items), page, size, totalElements, totalPages);
	}
}
