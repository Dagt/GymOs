package com.gymos.catalog.application.port;

import com.gymos.catalog.application.query.ProductPageView;
import com.gymos.catalog.application.query.ProductSearchCriteria;
import com.gymos.catalog.application.query.ProductSummaryView;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CatalogProductQueryPort {
	List<ProductSummaryView> listProducts(UUID organizationId);

	List<ProductSummaryView> listProducts(UUID organizationId, UUID branchId);

	Optional<ProductSummaryView> findProduct(UUID organizationId, UUID productId);

	Optional<ProductSummaryView> findProduct(UUID organizationId, UUID branchId, UUID productId);

	Optional<ProductSummaryView> findProductIncludingArchived(UUID organizationId, UUID productId);

	Optional<ProductSummaryView> findProductIncludingArchived(UUID organizationId, UUID branchId, UUID productId);

	Optional<ProductSummaryView> findActiveByBarcode(UUID organizationId, String barcode);

	Optional<ProductSummaryView> findActiveByBarcode(UUID organizationId, UUID branchId, String barcode);

	boolean skuExists(UUID organizationId, String sku, UUID excludedProductId);

	boolean barcodeExists(UUID organizationId, String barcode, UUID excludedProductId);

	ProductPageView search(UUID organizationId, ProductSearchCriteria criteria);

	ProductPageView search(UUID organizationId, UUID branchId, ProductSearchCriteria criteria);
}
