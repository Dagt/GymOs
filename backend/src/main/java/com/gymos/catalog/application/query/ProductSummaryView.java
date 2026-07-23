package com.gymos.catalog.application.query;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ProductSummaryView(
		UUID id,
		String sku,
		String name,
		String description,
		String productType,
		long priceCents,
		String currency,
		boolean taxable,
		String status,
		String barcode,
		String brand,
		String presentation,
		String unitOfMeasure,
		boolean inventoryTracked,
		int displayOrder,
		String imageObjectKey,
		UUID categoryId,
		String categoryName,
		OffsetDateTime createdAt,
		OffsetDateTime updatedAt
) {
}
