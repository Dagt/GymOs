package com.gymos.catalog.infrastructure;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class CatalogRepository {
	private final CatalogProductQueryRepository products;
	private final CatalogPlanQueryRepository plans;
	private final CatalogSkuLookupRepository skus;

	public CatalogRepository(CatalogProductQueryRepository products, CatalogPlanQueryRepository plans,
			CatalogSkuLookupRepository skus) {
		this.products = products;
		this.plans = plans;
		this.skus = skus;
	}

	public List<ProductSummary> products(UUID organizationId) {
		return products.list(organizationId);
	}

	public Optional<ProductSummary> product(UUID organizationId, UUID productId) {
		return products.find(organizationId, productId);
	}

	public boolean skuExists(UUID organizationId, String sku, UUID excludedProductId) {
		return skus.exists(organizationId, sku, excludedProductId);
	}

	public List<PlanSummary> plans(UUID organizationId) {
		return plans.list(organizationId);
	}

	public Optional<PlanSummary> plan(UUID organizationId, UUID planId) {
		return plans.find(organizationId, planId);
	}

	public record ProductSummary(
			UUID id,
			String sku,
			String name,
			String description,
			String productType,
			long priceCents,
			String currency,
			boolean taxable,
			String status,
			String barcode,
			String brand,
			String presentation,
			String unitOfMeasure,
			boolean inventoryTracked,
			int displayOrder,
			String imageObjectKey,
			OffsetDateTime createdAt,
			OffsetDateTime updatedAt
	) {
	}

	public record PlanSummary(
			UUID id,
			UUID productId,
			String sku,
			String name,
			long priceCents,
			String currency,
			int durationDays,
			int graceDays,
			String benefitsJson,
			boolean multiBranchAccess,
			String status,
			OffsetDateTime createdAt,
			OffsetDateTime updatedAt
	) {
	}
}
