package com.gymos.catalog.application.query;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
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
		OffsetDateTime updatedAt,
		long version
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
				categoryId, categoryName, tags, archived, archivedAt, createdAt, updatedAt, 0);
	}

	public ProductSummaryView visibleTo(Set<String> permissions) {
		if (permissions != null && (permissions.contains("catalog.cost.read")
				|| permissions.contains("catalog.manage") || permissions.contains("platform.admin"))) {
			return this;
		}
		return new ProductSummaryView(
				id, sku, name, description, productType, commercialSubtype, priceCents, currency,
				taxable, status, barcode, brand, presentation, unitOfMeasure, inventoryTracked,
				displayOrder, imageObjectKey, null, null, promotionalPriceCents,
				promotionStartsAt, promotionEndsAt, categoryId, categoryName, tags,
				archived, archivedAt, createdAt, updatedAt, version
		);
	}

	private static String defaultSubtype(String productType) {
		if ("MEMBERSHIP_PLAN".equalsIgnoreCase(productType)) return "MEMBERSHIP";
		if ("SERVICE".equalsIgnoreCase(productType)) return "GENERAL_SERVICE";
		return "GENERAL_PRODUCT";
	}
}
