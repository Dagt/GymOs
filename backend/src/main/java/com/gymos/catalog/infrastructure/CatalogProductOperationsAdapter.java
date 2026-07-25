package com.gymos.catalog.infrastructure;

import com.gymos.catalog.api.CatalogProductOperations;
import com.gymos.catalog.application.port.CatalogProductCommandPort;
import com.gymos.catalog.application.port.CatalogProductQueryPort;
import com.gymos.catalog.application.query.ProductSummaryView;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class CatalogProductOperationsAdapter implements CatalogProductOperations {
	private final CatalogProductQueryPort products;
	private final CatalogProductCommandPort commands;

	CatalogProductOperationsAdapter(CatalogProductQueryPort products, CatalogProductCommandPort commands) {
		this.products = products;
		this.commands = commands;
	}

	@Override
	public Optional<CatalogProductSnapshot> product(UUID organizationId, UUID productId) {
		return products.findProduct(organizationId, productId).map(this::snapshot);
	}

	@Override
	public Optional<CatalogProductSnapshot> product(UUID organizationId, UUID branchId, UUID productId) {
		return products.findProduct(organizationId, branchId, productId).map(this::snapshot);
	}

	@Override
	public void attachBranch(UUID organizationId, UUID branchId, UUID productId, UUID userId, OffsetDateTime now) {
		commands.attachBranch(organizationId, branchId, productId, userId, now);
	}

	private CatalogProductSnapshot snapshot(ProductSummaryView product) {
		return new CatalogProductSnapshot(
				product.id(), product.sku(), product.name(), product.productType(), product.priceCents(),
				product.currency(), product.inventoryTracked(), product.status()
		);
	}
}
