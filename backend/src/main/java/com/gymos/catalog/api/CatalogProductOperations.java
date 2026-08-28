package com.gymos.catalog.api;

import java.util.Optional;
import java.util.UUID;

public interface CatalogProductOperations {
	Optional<CatalogProductSnapshot> product(UUID organizationId, UUID productId);

	Optional<CatalogProductSnapshot> product(UUID organizationId, UUID branchId, UUID productId);

	void attachBranch(UUID organizationId, UUID branchId, UUID productId, UUID userId, java.time.OffsetDateTime now);

	record CatalogProductSnapshot(
			UUID id,
			String sku,
			String name,
			String productType,
			long priceCents,
			String currency,
			boolean inventoryTracked,
			String status,
			boolean managedVisitPackage
	) {
		public CatalogProductSnapshot(
				UUID id, String sku, String name, String productType, long priceCents,
				String currency, boolean inventoryTracked, String status
		) {
			this(id, sku, name, productType, priceCents, currency, inventoryTracked, status, false);
		}
		public boolean active() {
			return "ACTIVE".equals(status);
		}

		public boolean physicalInventoryTracked() {
			return active() && "PHYSICAL".equals(productType) && inventoryTracked;
		}
	}
}
