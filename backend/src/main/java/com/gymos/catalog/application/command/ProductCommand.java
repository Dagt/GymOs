package com.gymos.catalog.application.command;

import java.util.List;
import java.util.UUID;

public record ProductCommand(
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
		UUID categoryId,
		List<String> tags
) {
}
