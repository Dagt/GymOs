package com.gymos.catalog.application;

import com.gymos.catalog.application.port.CatalogInsightsQueryPort;
import com.gymos.catalog.application.port.CatalogProductQueryPort;
import com.gymos.catalog.application.query.CatalogSummaryView;
import com.gymos.catalog.application.query.ProductDetailView;
import com.gymos.catalog.application.query.ProductSummaryView;
import com.gymos.common.api.BusinessException;
import com.gymos.common.security.GymOsPrincipal;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
class CatalogInsightsService {
	private final CatalogInsightsQueryPort insights;
	private final CatalogProductQueryPort products;

	CatalogInsightsService(CatalogInsightsQueryPort insights, CatalogProductQueryPort products) {
		this.insights = insights;
		this.products = products;
	}

	CatalogSummaryView summary(GymOsPrincipal principal) {
		CatalogAccessGuard.requirePrincipal(principal);
		return insights.summary(principal.organizationId(), principal.branchId());
	}

	ProductDetailView detail(GymOsPrincipal principal, UUID productId, int historyLimit) {
		CatalogAccessGuard.requirePrincipal(principal);
		ProductSummaryView product = products.findProductIncludingArchived(principal.organizationId(), productId)
				.orElseThrow(() -> new BusinessException("PRODUCT_NOT_FOUND", "El producto no esta disponible."));
		return new ProductDetailView(
				product,
				insights.dependencies(principal.organizationId(), productId),
				insights.history(principal.organizationId(), productId, historyLimit)
		);
	}
}
