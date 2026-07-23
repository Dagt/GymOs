package com.gymos.catalog.application.port;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface CatalogProductCommandPort {
	void create(ProductMutation mutation);
	int update(ProductMutation mutation);
	int changeStatus(UUID organizationId, UUID productId, String status, UUID userId, OffsetDateTime now);
	int archive(UUID organizationId, UUID productId, UUID userId, OffsetDateTime now);
	int restore(UUID organizationId, UUID productId, String status, UUID userId, OffsetDateTime now);
	void replaceCategory(UUID organizationId, UUID productId, UUID categoryId, UUID userId, OffsetDateTime now);
	int updateImageKey(UUID organizationId, UUID productId, String imageObjectKey, UUID userId, OffsetDateTime now);
	void replaceTags(UUID organizationId, UUID productId, List<String> tags, UUID userId, OffsetDateTime now);

	record ProductMutation(
			UUID organizationId, UUID productId, String sku, String name, String description,
			String productType, String commercialSubtype, long priceCents, String currency, boolean taxable, String status,
			String barcode, String brand, String presentation, String unitOfMeasure,
			boolean inventoryTracked, int displayOrder, String imageObjectKey,
			String supplierName, Long referenceCostCents, Long promotionalPriceCents,
			OffsetDateTime promotionStartsAt, OffsetDateTime promotionEndsAt,
			OffsetDateTime now, UUID userId
	) {
	}
}
