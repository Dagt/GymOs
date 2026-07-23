package com.gymos.sales.infrastructure;

import com.gymos.catalog.api.CatalogProductOperations;
import com.gymos.sales.model.ProductForSale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
class SalesCatalogLookupRepository {
	private final CatalogProductOperations catalog;

	SalesCatalogLookupRepository(CatalogProductOperations catalog) {
		this.catalog = catalog;
	}

	Optional<ProductForSale> productForSale(UUID organizationId, UUID productId) {
		return catalog.product(organizationId, productId)
				.filter(CatalogProductOperations.CatalogProductSnapshot::active)
				.map(product -> new ProductForSale(
						product.id(), product.name(), product.productType(), product.priceCents(), product.currency()
				));
	}
}
