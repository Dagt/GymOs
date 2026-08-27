package com.gymos.catalog.application.command;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record CreateProductVariantsCommand(
		UUID familyId,
		String familyName,
		String brand,
		String description,
		String currency,
		boolean taxable,
		String status,
		String unitOfMeasure,
		boolean inventoryTracked,
		int displayOrder,
		String supplierName,
		UUID categoryId,
		List<String> tags,
		List<Variant> variants
) {
	public record Variant(
			String variantName,
			String sku,
			String presentation,
			long priceCents,
			List<String> barcodes,
			Long referenceCostCents,
			Long promotionalPriceCents,
			OffsetDateTime promotionStartsAt,
			OffsetDateTime promotionEndsAt
	) {
	}
}
