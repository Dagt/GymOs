package com.gymos.catalog.application.port;

import com.gymos.catalog.application.query.ProductPageView;
import com.gymos.catalog.application.query.ProductSearchCriteria;
import com.gymos.catalog.application.query.ProductSummaryView;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CatalogProductQueryPort {
	List<ProductSummaryView> listProducts(UUID organizationId);

	Optional<ProductSummaryView> findProduct(UUID organizationId, UUID productId);

	Optional<ProductSummaryView> findProductIncludingArchived(UUID organizationId, UUID productId);

	Optional<ProductSummaryView> findActiveByBarcode(UUID organizationId, String barcode);

	boolean skuExists(UUID organizationId, String sku, UUID excludedProductId);

	boolean barcodeExists(UUID organizationId, String barcode, UUID excludedProductId);

	ProductPageView search(UUID organizationId, ProductSearchCriteria criteria);
}
