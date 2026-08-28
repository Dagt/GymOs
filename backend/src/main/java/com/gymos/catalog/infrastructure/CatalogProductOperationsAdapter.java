package com.gymos.catalog.infrastructure;

import com.gymos.catalog.api.CatalogProductOperations;
import com.gymos.catalog.application.port.CatalogProductCommandPort;
import com.gymos.catalog.application.port.CatalogProductQueryPort;
import com.gymos.catalog.application.port.VisitPackagePlanPort;
import com.gymos.catalog.application.query.ProductSummaryView;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class CatalogProductOperationsAdapter implements CatalogProductOperations {
	private final CatalogProductQueryPort products;
	private final CatalogProductCommandPort commands;
	private final VisitPackagePlanPort visitPackages;

	CatalogProductOperationsAdapter(
			CatalogProductQueryPort products, CatalogProductCommandPort commands, VisitPackagePlanPort visitPackages
	) {
		this.products = products;
		this.commands = commands;
		this.visitPackages = visitPackages;
	}

	@Override
	public Optional<CatalogProductSnapshot> product(UUID organizationId, UUID productId) {
		return products.findProduct(organizationId, productId).map(product -> snapshot(organizationId, product));
	}

	@Override
	public Optional<CatalogProductSnapshot> product(UUID organizationId, UUID branchId, UUID productId) {
		return products.findProduct(organizationId, branchId, productId).map(product -> snapshot(organizationId, product));
	}

	@Override
	public void attachBranch(UUID organizationId, UUID branchId, UUID productId, UUID userId, OffsetDateTime now) {
		commands.attachBranch(organizationId, branchId, productId, userId, now);
	}

	private CatalogProductSnapshot snapshot(UUID organizationId, ProductSummaryView product) {
		return new CatalogProductSnapshot(
				product.id(), product.sku(), product.name(), product.productType(), product.priceCents(),
				product.currency(), product.inventoryTracked(), product.status(),
				visitPackages.configuredProduct(organizationId, product.id())
		);
	}
}
