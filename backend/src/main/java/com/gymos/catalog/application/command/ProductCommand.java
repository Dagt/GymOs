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
		List<String> tags
) {
}
