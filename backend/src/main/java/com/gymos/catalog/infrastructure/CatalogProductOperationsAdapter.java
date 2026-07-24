package com.gymos.catalog.infrastructure;

import com.gymos.catalog.api.CatalogProductOperations;
import com.gymos.catalog.application.port.CatalogProductQueryPort;
import com.gymos.catalog.application.query.ProductSummaryView;
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
		return products.findProduct(organizationId, productId).map(this::snapshot);
	}

	@Override
	public Optional<CatalogProductSnapshot> product(UUID organizationId, UUID branchId, UUID productId) {
		return products.findProduct(organizationId, branchId, productId).map(this::snapshot);
	}

	private CatalogProductSnapshot snapshot(ProductSummaryView product) {
		return new CatalogProductSnapshot(
				product.id(), product.sku(), product.name(), product.productType(), product.priceCents(),
				product.currency(), product.inventoryTracked(), product.status()
		);
	}
}
