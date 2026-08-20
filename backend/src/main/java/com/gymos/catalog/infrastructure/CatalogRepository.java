package com.gymos.catalog.infrastructure;

import com.gymos.catalog.application.port.CatalogPlanQueryPort;
import com.gymos.catalog.application.port.CatalogProductQueryPort;
import com.gymos.catalog.application.query.PlanView;
import com.gymos.catalog.application.query.ProductPageView;
import com.gymos.catalog.application.query.ProductSearchCriteria;
import com.gymos.catalog.application.query.ProductSummaryView;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class CatalogRepository implements CatalogProductQueryPort, CatalogPlanQueryPort {
	private final CatalogProductQueryRepository products;
	private final CatalogPlanQueryRepository plans;
	private final CatalogSkuLookupRepository skus;

	public CatalogRepository(CatalogProductQueryRepository products, CatalogPlanQueryRepository plans,
			CatalogSkuLookupRepository skus) {
		this.products = products;
		this.plans = plans;
		this.skus = skus;
	}

	@Override public List<ProductSummaryView> listProducts(UUID organizationId) { return products.list(organizationId); }
	@Override public List<ProductSummaryView> listProducts(UUID organizationId, UUID branchId) { return products.list(organizationId, branchId); }
	@Override public Optional<ProductSummaryView> findProduct(UUID organizationId, UUID productId) { return products.find(organizationId, productId); }
	@Override public Optional<ProductSummaryView> findProduct(UUID organizationId, UUID branchId, UUID productId) { return products.find(organizationId, branchId, productId); }
	@Override public Optional<ProductSummaryView> findProductIncludingArchived(UUID organizationId, UUID productId) { return products.findIncludingArchived(organizationId, productId); }
	@Override public Optional<ProductSummaryView> findProductIncludingArchived(UUID organizationId, UUID branchId, UUID productId) { return products.findIncludingArchived(organizationId, branchId, productId); }
	@Override public Optional<ProductSummaryView> findActiveByBarcode(UUID organizationId, String barcode) { return products.findActiveByBarcode(organizationId, barcode); }
	@Override public Optional<ProductSummaryView> findActiveByBarcode(UUID organizationId, UUID branchId, String barcode) { return products.findActiveByBarcode(organizationId, branchId, barcode); }
	@Override public Optional<ProductSummaryView> findBySku(UUID organizationId, String sku) { return products.findBySku(organizationId, sku); }
	@Override public boolean skuExists(UUID organizationId, String sku, UUID excludedProductId) { return skus.exists(organizationId, sku, excludedProductId); }
	@Override public boolean barcodeExists(UUID organizationId, String barcode, UUID excludedProductId) { return skus.barcodeExists(organizationId, barcode, excludedProductId); }
	@Override public ProductPageView search(UUID organizationId, ProductSearchCriteria criteria) { return products.search(organizationId, criteria); }
	@Override public ProductPageView search(UUID organizationId, UUID branchId, ProductSearchCriteria criteria) { return products.search(organizationId, branchId, criteria); }
	@Override public List<PlanView> listPlans(UUID organizationId) { return plans.list(organizationId); }
	@Override public List<PlanView> listPlans(UUID organizationId, UUID branchId) { return plans.list(organizationId, branchId); }
	@Override public Optional<PlanView> findPlan(UUID organizationId, UUID planId) { return plans.find(organizationId, planId); }
	@Override public Optional<PlanView> findPlan(UUID organizationId, UUID branchId, UUID planId) { return plans.find(organizationId, branchId, planId); }
	@Override public Optional<PlanView> findPlanByProduct(UUID organizationId, UUID productId) { return plans.findByProduct(organizationId, productId); }

	public List<ProductSummaryView> products(UUID organizationId) { return listProducts(organizationId); }
	public Optional<ProductSummaryView> product(UUID organizationId, UUID productId) { return findProduct(organizationId, productId); }
	public List<PlanView> plans(UUID organizationId) { return listPlans(organizationId); }
	public Optional<PlanView> plan(UUID organizationId, UUID planId) { return findPlan(organizationId, planId); }
}
