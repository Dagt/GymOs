package com.gymos.catalog.application;

import com.gymos.audit.api.AuditService;
import com.gymos.catalog.application.command.ProductCommand;
import com.gymos.catalog.infrastructure.CatalogCommandRepository;
import com.gymos.catalog.infrastructure.CatalogCommandRepository.ProductRecord;
import com.gymos.catalog.infrastructure.CatalogRepository;
import com.gymos.catalog.infrastructure.CatalogRepository.ProductSummary;
import com.gymos.common.api.BusinessException;
import com.gymos.common.security.GymOsPrincipal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
class CatalogProductService {
	private final CatalogRepository catalogRepository;
	private final CatalogCommandRepository commandRepository;
	private final AuditService auditService;

	CatalogProductService(CatalogRepository catalogRepository, CatalogCommandRepository commandRepository, AuditService auditService) {
		this.catalogRepository = catalogRepository;
		this.commandRepository = commandRepository;
		this.auditService = auditService;
	}

	List<ProductSummary> products(GymOsPrincipal principal) {
		CatalogAccessGuard.requirePrincipal(principal);
		return catalogRepository.products(principal.organizationId());
	}

	ProductSummary createProduct(GymOsPrincipal principal, ProductCommand request, String correlationId) {
		CatalogAccessGuard.requirePrincipal(principal);
		String sku = request.sku().trim().toUpperCase();
		if (catalogRepository.skuExists(principal.organizationId(), sku, null)) {
			throw new BusinessException("PRODUCT_SKU_ALREADY_EXISTS", "Ya existe un producto con ese SKU.");
		}
		UUID productId = UUID.randomUUID();
		OffsetDateTime now = OffsetDateTime.now();
		commandRepository.createProduct(new ProductRecord(
				productId,
				principal.organizationId(),
				sku,
				request.name().trim(),
				CatalogBenefitsSerializer.blankToNull(request.description()),
				request.productType().trim().toUpperCase(),
				request.priceCents(),
				request.currency().trim().toUpperCase(),
				request.taxable(),
				request.status().trim().toUpperCase(),
				now,
				principal.userId()
		));
		auditService.record(principal, "PRODUCT_CREATED", "CATALOG_PRODUCT", productId, "BACKOFFICE", null, correlationId);
		return catalogRepository.product(principal.organizationId(), productId)
				.orElseThrow(() -> new BusinessException("PRODUCT_NOT_FOUND", "El producto no esta disponible."));
	}
}
