package com.gymos.sales.model;

import java.util.UUID;

public record ProductForSale(
		UUID id, String name, String productType, long priceCents, String currency,
		boolean managedVisitPackage
) {
	public ProductForSale(UUID id, String name, String productType, long priceCents, String currency) {
		this(id, name, productType, priceCents, currency, false);
	}
}
