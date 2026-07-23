package com.gymos.catalog.application;

import com.gymos.audit.api.AuditService;
import com.gymos.catalog.application.command.ProductCommand;
import com.gymos.catalog.infrastructure.CatalogCommandRepository;
import com.gymos.catalog.infrastructure.CatalogCommandRepository.ProductRecord;
import com.gymos.catalog.infrastructure.CatalogCommandRepository.ProductUpdateRecord;
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

	ProductSummary product(GymOsPrincipal principal, UUID productId) {
		CatalogAccessGuard.requirePrincipal(principal);
		return requireProduct(principal.organizationId(), productId);
	}

	ProductSummary createProduct(GymOsPrincipal principal, ProductCommand request, String correlationId) {
		CatalogAccessGuard.requirePrincipal(principal);
		validate(request);
		String sku = normalizeRequired(request.sku());
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
				normalizeRequired(request.productType()),
				request.priceCents(),
				normalizeRequired(request.currency()),
				request.taxable(),
				normalizeRequired(request.status()),
				normalizeNullable(request.barcode()),
				trimToNull(request.brand()),
				trimToNull(request.presentation()),
				normalizeNullable(request.unitOfMeasure()),
				request.inventoryTracked(),
				request.displayOrder(),
				trimToNull(request.imageObjectKey()),
				now,
				principal.userId()
		));
		auditService.record(principal, "PRODUCT_CREATED", "CATALOG_PRODUCT", productId, "BACKOFFICE", null, correlationId);
		return requireProduct(principal.organizationId(), productId);
	}

	ProductSummary updateProduct(GymOsPrincipal principal, UUID productId, ProductCommand request, String correlationId) {
		CatalogAccessGuard.requirePrincipal(principal);
		validate(request);
		requireProduct(principal.organizationId(), productId);
		String sku = normalizeRequired(request.sku());
		if (catalogRepository.skuExists(principal.organizationId(), sku, productId)) {
			throw new BusinessException("PRODUCT_SKU_ALREADY_EXISTS", "Ya existe otro producto con ese SKU.");
		}
		int updated = commandRepository.updateProduct(new ProductUpdateRecord(
				principal.organizationId(),
				productId,
				sku,
				request.name().trim(),
				CatalogBenefitsSerializer.blankToNull(request.description()),
				normalizeRequired(request.productType()),
				request.priceCents(),
				normalizeRequired(request.currency()),
				request.taxable(),
				normalizeRequired(request.status()),
				normalizeNullable(request.barcode()),
				trimToNull(request.brand()),
				trimToNull(request.presentation()),
				normalizeNullable(request.unitOfMeasure()),
				request.inventoryTracked(),
				request.displayOrder(),
				trimToNull(request.imageObjectKey()),
				OffsetDateTime.now(),
				principal.userId()
		));
		if (updated != 1) {
			throw new BusinessException("PRODUCT_NOT_FOUND", "El producto no existe o fue archivado.");
		}
		auditService.record(principal, "PRODUCT_UPDATED", "CATALOG_PRODUCT", productId, "BACKOFFICE", null, correlationId);
		return requireProduct(principal.organizationId(), productId);
	}

	ProductSummary changeStatus(GymOsPrincipal principal, UUID productId, String status, String correlationId) {
		CatalogAccessGuard.requirePrincipal(principal);
		String normalizedStatus = normalizeRequired(status);
		if (!"ACTIVE".equals(normalizedStatus) && !"INACTIVE".equals(normalizedStatus)) {
			throw new BusinessException("PRODUCT_STATUS_INVALID", "El estado del producto no es valido.");
		}
		requireProduct(principal.organizationId(), productId);
		int updated = commandRepository.changeProductStatus(
				principal.organizationId(), productId, normalizedStatus, principal.userId(), OffsetDateTime.now());
		if (updated != 1) {
			throw new BusinessException("PRODUCT_NOT_FOUND", "El producto no existe o fue archivado.");
		}
		auditService.record(principal, "PRODUCT_STATUS_CHANGED", "CATALOG_PRODUCT", productId, "BACKOFFICE", normalizedStatus, correlationId);
		return requireProduct(principal.organizationId(), productId);
	}

	void archiveProduct(GymOsPrincipal principal, UUID productId, String correlationId) {
		CatalogAccessGuard.requirePrincipal(principal);
		ProductSummary product = requireProduct(principal.organizationId(), productId);
		if ("MEMBERSHIP_PLAN".equals(product.productType())) {
			throw new BusinessException("PRODUCT_ARCHIVE_REQUIRES_PLAN_WORKFLOW", "Los productos de membresia se administran desde planes.");
		}
		int updated = commandRepository.archiveProduct(
				principal.organizationId(), productId, principal.userId(), OffsetDateTime.now());
		if (updated != 1) {
			throw new BusinessException("PRODUCT_NOT_FOUND", "El producto no existe o ya fue archivado.");
		}
		auditService.record(principal, "PRODUCT_ARCHIVED", "CATALOG_PRODUCT", productId, "BACKOFFICE", null, correlationId);
	}

	private ProductSummary requireProduct(UUID organizationId, UUID productId) {
		return catalogRepository.product(organizationId, productId)
				.orElseThrow(() -> new BusinessException("PRODUCT_NOT_FOUND", "El producto no esta disponible."));
	}

	private void validate(ProductCommand request) {
		String productType = normalizeRequired(request.productType());
		if (request.inventoryTracked() && !"PHYSICAL".equals(productType)) {
			throw new BusinessException("PRODUCT_INVENTORY_TRACKING_INVALID", "Solo los productos fisicos pueden controlar inventario.");
		}
		if (request.displayOrder() < 0) {
			throw new BusinessException("PRODUCT_DISPLAY_ORDER_INVALID", "El orden de visualizacion no puede ser negativo.");
		}
	}

	private String normalizeRequired(String value) {
		return value == null ? "" : value.trim().toUpperCase();
	}

	private String normalizeNullable(String value) {
		String normalized = normalizeRequired(value);
		return normalized.isBlank() ? null : normalized;
	}

	private String trimToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}
}
