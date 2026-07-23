package com.gymos.catalog.application.query;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record ProductSummaryView(
		UUID id,
		String sku,
		String name,
		String description,
		String productType,
		String commercialSubtype,
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
		String supplierName,
		Long referenceCostCents,
		Long promotionalPriceCents,
		OffsetDateTime promotionStartsAt,
		OffsetDateTime promotionEndsAt,
		UUID categoryId,
		String categoryName,
		List<String> tags,
		boolean archived,
		OffsetDateTime archivedAt,
		OffsetDateTime createdAt,
		OffsetDateTime updatedAt
) {
}
