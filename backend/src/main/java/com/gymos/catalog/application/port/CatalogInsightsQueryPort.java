package com.gymos.catalog.application.port;

import com.gymos.catalog.application.query.CatalogSummaryView;
import com.gymos.catalog.application.query.ProductDependencyView;
import com.gymos.catalog.application.query.ProductHistoryEntryView;
import java.util.List;
import java.util.UUID;

public interface CatalogInsightsQueryPort {
	CatalogSummaryView summary(UUID organizationId, UUID branchId);

	ProductDependencyView dependencies(UUID organizationId, UUID productId);

	List<ProductHistoryEntryView> history(UUID organizationId, UUID productId, int limit);
}
