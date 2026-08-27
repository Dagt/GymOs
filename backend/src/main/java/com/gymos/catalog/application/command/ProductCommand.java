package com.gymos.catalog.application.command;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record ProductCommand(
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
		String supplierName,
		Long referenceCostCents,
		Long promotionalPriceCents,
		OffsetDateTime promotionStartsAt,
		OffsetDateTime promotionEndsAt,
		UUID categoryId,
		List<String> tags,
		Long version,
		UUID familyId,
		String variantName,
		List<String> barcodes
) {
	public ProductCommand(
			String sku, String name, String description, String productType, String commercialSubtype,
			long priceCents, String currency, boolean taxable, String status, String barcode,
			String brand, String presentation, String unitOfMeasure, boolean inventoryTracked,
			int displayOrder, String supplierName, Long referenceCostCents, Long promotionalPriceCents,
			OffsetDateTime promotionStartsAt, OffsetDateTime promotionEndsAt, UUID categoryId,
			List<String> tags, Long version
	) {
		this(sku, name, description, productType, commercialSubtype, priceCents, currency, taxable,
				status, barcode, brand, presentation, unitOfMeasure, inventoryTracked, displayOrder,
				supplierName, referenceCostCents, promotionalPriceCents, promotionStartsAt,
				promotionEndsAt, categoryId, tags, version, null, null, List.of());
	}

	public ProductCommand(
			String sku, String name, String description, String productType, String commercialSubtype,
			long priceCents, String currency, boolean taxable, String status, String barcode,
			String brand, String presentation, String unitOfMeasure, boolean inventoryTracked,
			int displayOrder, String supplierName, Long referenceCostCents, Long promotionalPriceCents,
			OffsetDateTime promotionStartsAt, OffsetDateTime promotionEndsAt, UUID categoryId, List<String> tags
	) {
		this(sku, name, description, productType, commercialSubtype, priceCents, currency, taxable, status,
				barcode, brand, presentation, unitOfMeasure, inventoryTracked, displayOrder, supplierName,
				referenceCostCents, promotionalPriceCents, promotionStartsAt, promotionEndsAt, categoryId, tags,
				null, null, null, List.of());
	}

	public ProductCommand(
			String sku, String name, String description, String productType, long priceCents,
			String currency, boolean taxable, String status, String barcode, String brand,
			String presentation, String unitOfMeasure, boolean inventoryTracked, int displayOrder,
			UUID categoryId, List<String> tags
	) {
		this(sku, name, description, productType, defaultSubtype(productType), priceCents, currency,
				taxable, status, barcode, brand, presentation, unitOfMeasure, inventoryTracked,
				displayOrder, null, null, null, null, null, categoryId, tags, null, null, null, List.of());
	}

	private static String defaultSubtype(String productType) {
		if ("MEMBERSHIP_PLAN".equalsIgnoreCase(productType)) return "MEMBERSHIP";
		if ("SERVICE".equalsIgnoreCase(productType)) return "GENERAL_SERVICE";
		return "GENERAL_PRODUCT";
	}
}
