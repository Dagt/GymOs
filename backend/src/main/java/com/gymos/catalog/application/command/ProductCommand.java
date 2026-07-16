package com.gymos.catalog.application.command;

public record ProductCommand(
		String sku,
		String name,
		String description,
		String productType,
		long priceCents,
		String currency,
		boolean taxable,
		String status
) {
}
