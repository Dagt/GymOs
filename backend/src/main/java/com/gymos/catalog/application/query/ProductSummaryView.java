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
	public ProductSummaryView(
			UUID id, String sku, String name, String description, String productType, long priceCents,
			String currency, boolean taxable, String status, String barcode, String brand,
			String presentation, String unitOfMeasure, boolean inventoryTracked, int displayOrder,
			String imageObjectKey, UUID categoryId, String categoryName, List<String> tags,
			boolean archived, OffsetDateTime archivedAt, OffsetDateTime createdAt, OffsetDateTime updatedAt
	) {
		this(id, sku, name, description, productType, defaultSubtype(productType), priceCents, currency,
				taxable, status, barcode, brand, presentation, unitOfMeasure, inventoryTracked,
				displayOrder, imageObjectKey, null, null, null, null, null,
				categoryId, categoryName, tags, archived, archivedAt, createdAt, updatedAt);
	}

	private static String defaultSubtype(String productType) {
		if ("MEMBERSHIP_PLAN".equalsIgnoreCase(productType)) return "MEMBERSHIP";
		if ("SERVICE".equalsIgnoreCase(productType)) return "GENERAL_SERVICE";
		return "GENERAL_PRODUCT";
	}
}
