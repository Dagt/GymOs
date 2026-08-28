package com.gymos.catalog.application.port;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface CatalogProductCommandPort {
	void create(ProductMutation mutation);
	int update(ProductMutation mutation);
	void attachBranch(UUID organizationId, UUID branchId, UUID productId, UUID userId, OffsetDateTime now);
	int changeStatus(UUID organizationId, UUID productId, String status, UUID userId, OffsetDateTime now);
	int archive(UUID organizationId, UUID productId, UUID userId, OffsetDateTime now);
	int restore(UUID organizationId, UUID productId, String status, UUID userId, OffsetDateTime now);
	void replaceCategory(UUID organizationId, UUID productId, UUID categoryId, UUID userId, OffsetDateTime now);
	int updateImageKey(UUID organizationId, UUID productId, String imageObjectKey, UUID userId, OffsetDateTime now);
	void replaceTags(UUID organizationId, UUID productId, List<String> tags, UUID userId, OffsetDateTime now);
	void replaceBarcodes(UUID organizationId, UUID productId, List<String> barcodes, boolean active,
			UUID userId, OffsetDateTime now);
	void setBarcodesActive(UUID organizationId, UUID productId, boolean active);

	record ProductMutation(
			UUID organizationId, UUID productId, String sku, String name, String description,
			String productType, String commercialSubtype, long priceCents, String currency, boolean taxable, String status,
			String barcode, String brand, String presentation, String unitOfMeasure,
			boolean inventoryTracked, int displayOrder, String imageObjectKey,
			String supplierName, Long referenceCostCents, Long promotionalPriceCents,
			OffsetDateTime promotionStartsAt, OffsetDateTime promotionEndsAt,
			Long expectedVersion, OffsetDateTime now, UUID userId,
			UUID familyId, String variantName
	) {
		public ProductMutation(
				UUID organizationId, UUID productId, String sku, String name, String description,
				String productType, String commercialSubtype, long priceCents, String currency, boolean taxable,
				String status, String barcode, String brand, String presentation, String unitOfMeasure,
				boolean inventoryTracked, int displayOrder, String imageObjectKey, String supplierName,
				Long referenceCostCents, Long promotionalPriceCents, OffsetDateTime promotionStartsAt,
				OffsetDateTime promotionEndsAt, Long expectedVersion, OffsetDateTime now, UUID userId
		) {
			this(organizationId, productId, sku, name, description, productType, commercialSubtype,
					priceCents, currency, taxable, status, barcode, brand, presentation, unitOfMeasure,
					inventoryTracked, displayOrder, imageObjectKey, supplierName, referenceCostCents,
					promotionalPriceCents, promotionStartsAt, promotionEndsAt, expectedVersion, now, userId,
					null, null);
		}

		public ProductMutation(
				UUID organizationId, UUID productId, String sku, String name, String description,
				String productType, String commercialSubtype, long priceCents, String currency,
				boolean taxable, String status, String barcode, String brand, String presentation,
				String unitOfMeasure, boolean inventoryTracked, int displayOrder, String imageObjectKey,
				String supplierName, Long referenceCostCents, Long promotionalPriceCents,
				OffsetDateTime promotionStartsAt, OffsetDateTime promotionEndsAt,
				OffsetDateTime now, UUID userId
		) {
			this(organizationId, productId, sku, name, description, productType, commercialSubtype,
					priceCents, currency, taxable, status, barcode, brand, presentation, unitOfMeasure,
					inventoryTracked, displayOrder, imageObjectKey, supplierName, referenceCostCents,
					promotionalPriceCents, promotionStartsAt, promotionEndsAt, null, now, userId, null, null);
		}

		public ProductMutation(
				UUID organizationId, UUID productId, String sku, String name, String description,
				String productType, long priceCents, String currency, boolean taxable, String status,
				String barcode, String brand, String presentation, String unitOfMeasure,
				boolean inventoryTracked, int displayOrder, String imageObjectKey,
				OffsetDateTime now, UUID userId
		) {
			this(organizationId, productId, sku, name, description, productType, defaultSubtype(productType),
					priceCents, currency, taxable, status, barcode, brand, presentation, unitOfMeasure,
					inventoryTracked, displayOrder, imageObjectKey, null, null, null, null, null, null, now, userId,
					null, null);
		}

		private static String defaultSubtype(String productType) {
			if ("MEMBERSHIP_PLAN".equalsIgnoreCase(productType)) return "MEMBERSHIP";
			if ("SERVICE".equalsIgnoreCase(productType)) return "GENERAL_SERVICE";
			return "GENERAL_PRODUCT";
		}
	}
}
