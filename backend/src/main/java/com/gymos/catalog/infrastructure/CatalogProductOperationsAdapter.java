package com.gymos.catalog.infrastructure;

import com.gymos.catalog.api.CatalogProductOperations;
import com.gymos.catalog.application.port.CatalogProductQueryPort;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class CatalogProductOperationsAdapter implements CatalogProductOperations {
	private final CatalogProductQueryPort products;

	CatalogProductOperationsAdapter(CatalogProductQueryPort products) {
		this.products = products;
	}

	@Override
	public Optional<CatalogProductSnapshot> product(UUID organizationId, UUID productId) {
		return products.findProduct(organizationId, productId).map(product -> new CatalogProductSnapshot(
				product.id(), product.sku(), product.name(), product.productType(), product.priceCents(),
				product.currency(), product.inventoryTracked(), product.status()
		));
	}
}
