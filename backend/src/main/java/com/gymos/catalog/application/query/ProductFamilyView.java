package com.gymos.catalog.application.query;

import java.util.List;
import java.util.UUID;

public record ProductFamilyView(
		UUID id,
		String name,
		String brand,
		List<ProductSummaryView> variants
) {
}
