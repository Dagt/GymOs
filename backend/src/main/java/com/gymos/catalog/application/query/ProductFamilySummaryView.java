package com.gymos.catalog.application.query;

import java.util.UUID;

public record ProductFamilySummaryView(
		UUID id,
		String name,
		String brand,
		String description,
		long activeVariants,
		long totalVariants
) {
}
