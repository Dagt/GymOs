package com.gymos.catalog.application;

import com.gymos.audit.api.AuditService;
import com.gymos.catalog.application.command.ProductCommand;
import com.gymos.catalog.application.port.CatalogProductCommandPort;
import com.gymos.catalog.application.port.CatalogProductCommandPort.ProductMutation;
import com.gymos.catalog.application.port.CatalogProductQueryPort;
import com.gymos.catalog.application.port.CatalogDependencyQueryPort;
import com.gymos.catalog.application.port.CatalogCategoryQueryPort;
import com.gymos.catalog.application.query.ProductSummaryView;
import com.gymos.catalog.application.query.ProductPageView;
import com.gymos.catalog.application.query.ProductSearchCriteria;
import com.gymos.common.api.BusinessException;
import com.gymos.common.security.GymOsPrincipal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
class CatalogProductService {
	private final CatalogProductQueryPort products;
	private final CatalogProductCommandPort commands;
	private final CatalogDependencyQueryPort dependencies;
	private final CatalogCategoryQueryPort categories;
	private final AuditService auditService;

	CatalogProductService(CatalogProductQueryPort products, CatalogProductCommandPort commands,
			CatalogDependencyQueryPort dependencies, CatalogCategoryQueryPort categories, AuditService auditService) {
		this.products = products;
		this.commands = commands;
		this.dependencies = dependencies;
		this.categories = categories;
		this.auditService = auditService;
	}

	List<ProductSummaryView> products(GymOsPrincipal principal) {
		CatalogAccessGuard.requirePrincipal(principal);
		return products.listProducts(principal.organizationId());
	}

	ProductSummaryView product(GymOsPrincipal principal, UUID productId) {
		CatalogAccessGuard.requirePrincipal(principal);
		return requireProduct(principal.organizationId(), productId);
	}

	ProductPageView search(GymOsPrincipal principal, ProductSearchCriteria criteria) {
		CatalogAccessGuard.requirePrincipal(principal);
		return products.search(principal.organizationId(), criteria);
	}

	ProductSummaryView createProduct(GymOsPrincipal principal, ProductCommand request, String correlationId) {
		CatalogAccessGuard.requirePrincipal(principal);
		validate(request);
		String sku = normalizeRequired(request.sku());
		if (products.skuExists(principal.organizationId(), sku, null)) {
			throw new BusinessException("PRODUCT_SKU_ALREADY_EXISTS", "Ya existe un producto con ese SKU.");
		}
		String barcode = normalizeNullable(request.barcode());
		validateBarcodeAvailability(principal.organizationId(), barcode, null);
		validateCategory(principal.organizationId(), request.categoryId());
		List<String> tags = normalizeTags(request.tags());
		UUID productId = UUID.randomUUID();
		OffsetDateTime now = OffsetDateTime.now();
		commands.create(new ProductMutation(
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
				barcode,
				trimToNull(request.brand()),
				trimToNull(request.presentation()),
				normalizeNullable(request.unitOfMeasure()),
				request.inventoryTracked(),
				request.displayOrder(),
				trimToNull(request.imageObjectKey()),
				now,
				principal.userId()
		));
		commands.replaceCategory(
				principal.organizationId(), productId, request.categoryId(), principal.userId(), now);
		commands.replaceTags(principal.organizationId(), productId, tags, principal.userId(), now);
		auditService.record(principal, "PRODUCT_CREATED", "CATALOG_PRODUCT", productId, "BACKOFFICE", null, correlationId);
		return requireProduct(principal.organizationId(), productId);
	}

	ProductSummaryView updateProduct(GymOsPrincipal principal, UUID productId, ProductCommand request, String correlationId) {
		CatalogAccessGuard.requirePrincipal(principal);
		validate(request);
		ProductSummaryView existing = requireProduct(principal.organizationId(), productId);
		validateTransition(principal.organizationId(), existing, request);
		String sku = normalizeRequired(request.sku());
		if (products.skuExists(principal.organizationId(), sku, productId)) {
			throw new BusinessException("PRODUCT_SKU_ALREADY_EXISTS", "Ya existe otro producto con ese SKU.");
		}
		String barcode = normalizeNullable(request.barcode());
		validateBarcodeAvailability(principal.organizationId(), barcode, productId);
		validateCategory(principal.organizationId(), request.categoryId());
		List<String> tags = normalizeTags(request.tags());
		int updated = commands.update(new ProductMutation(
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
				barcode,
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
		commands.replaceCategory(
				principal.organizationId(), productId, request.categoryId(), principal.userId(), OffsetDateTime.now());
		commands.replaceTags(
				principal.organizationId(), productId, tags, principal.userId(), OffsetDateTime.now());
		if (!java.util.Objects.equals(existing.categoryId(), request.categoryId())) {
			auditService.record(
					principal, "PRODUCT_CATEGORY_CHANGED", "CATALOG_PRODUCT", productId,
					"BACKOFFICE", null, correlationId);
		}
		auditService.record(principal, "PRODUCT_UPDATED", "CATALOG_PRODUCT", productId, "BACKOFFICE", null, correlationId);
		return requireProduct(principal.organizationId(), productId);
	}

	ProductSummaryView changeStatus(GymOsPrincipal principal, UUID productId, String status, String correlationId) {
		CatalogAccessGuard.requirePrincipal(principal);
		String normalizedStatus = normalizeRequired(status);
		if (!"ACTIVE".equals(normalizedStatus) && !"INACTIVE".equals(normalizedStatus)) {
			throw new BusinessException("PRODUCT_STATUS_INVALID", "El estado del producto no es valido.");
		}
		requireProduct(principal.organizationId(), productId);
		int updated = commands.changeStatus(
				principal.organizationId(), productId, normalizedStatus, principal.userId(), OffsetDateTime.now());
		if (updated != 1) {
			throw new BusinessException("PRODUCT_NOT_FOUND", "El producto no existe o fue archivado.");
		}
		String eventType = "ACTIVE".equals(normalizedStatus) ? "PRODUCT_ACTIVATED" : "PRODUCT_DEACTIVATED";
		auditService.record(principal, eventType, "CATALOG_PRODUCT", productId, "BACKOFFICE", normalizedStatus, correlationId);
		return requireProduct(principal.organizationId(), productId);
	}

	void archiveProduct(GymOsPrincipal principal, UUID productId, String correlationId) {
		CatalogAccessGuard.requirePrincipal(principal);
		ProductSummaryView product = requireProduct(principal.organizationId(), productId);
		if ("MEMBERSHIP_PLAN".equals(product.productType())) {
			throw new BusinessException("PRODUCT_ARCHIVE_REQUIRES_PLAN_WORKFLOW", "Los productos de membresia se administran desde planes.");
		}
		if (dependencies.hasActiveMembershipPlan(principal.organizationId(), productId)) {
			throw new BusinessException("PRODUCT_ARCHIVE_NOT_ALLOWED", "El producto tiene un plan de membresia activo.");
		}
		int updated = commands.archive(
				principal.organizationId(), productId, principal.userId(), OffsetDateTime.now());
		if (updated != 1) {
			throw new BusinessException("PRODUCT_NOT_FOUND", "El producto no existe o ya fue archivado.");
		}
		auditService.record(principal, "PRODUCT_ARCHIVED", "CATALOG_PRODUCT", productId, "BACKOFFICE", null, correlationId);
	}

	private ProductSummaryView requireProduct(UUID organizationId, UUID productId) {
		return products.findProduct(organizationId, productId)
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
		String barcode = normalizeNullable(request.barcode());
		if (barcode != null && !barcode.matches("[A-Z0-9._-]{3,128}")) {
			throw new BusinessException("PRODUCT_BARCODE_INVALID", "El codigo de barras contiene caracteres no permitidos.");
		}
	}

	private void validateBarcodeAvailability(UUID organizationId, String barcode, UUID excludedProductId) {
		if (barcode != null && products.barcodeExists(organizationId, barcode, excludedProductId)) {
			throw new BusinessException("PRODUCT_BARCODE_ALREADY_EXISTS", "Ya existe otro producto con ese codigo de barras.");
		}
	}

	private void validateCategory(UUID organizationId, UUID categoryId) {
		if (categoryId != null && categories.findCategory(organizationId, categoryId).isEmpty()) {
			throw new BusinessException("CATEGORY_NOT_FOUND", "La categoria no pertenece a la organizacion.");
		}
	}

	private List<String> normalizeTags(List<String> tags) {
		if (tags == null) {
			return List.of();
		}
		List<String> normalized = tags.stream()
				.filter(java.util.Objects::nonNull)
				.map(String::trim)
				.filter(tag -> !tag.isBlank())
				.distinct()
				.toList();
		if (normalized.size() > 20 || normalized.stream().anyMatch(tag -> tag.length() > 80 || tag.contains(","))) {
			throw new BusinessException("PRODUCT_TAGS_INVALID", "Usa hasta 20 etiquetas sin comas.");
		}
		return normalized;
	}

	private void validateTransition(UUID organizationId, ProductSummaryView existing, ProductCommand request) {
		String nextType = normalizeRequired(request.productType());
		boolean hasMovements = dependencies.hasInventoryMovements(organizationId, existing.id());
		if (hasMovements && !existing.productType().equals(nextType)) {
			throw new BusinessException(
					"PRODUCT_TYPE_CHANGE_NOT_ALLOWED",
					"No se puede cambiar el tipo de un producto con movimientos de inventario."
			);
		}
		if (hasMovements && existing.inventoryTracked() && !request.inventoryTracked()) {
			throw new BusinessException(
					"PRODUCT_INVENTORY_TRACKING_CHANGE_NOT_ALLOWED",
					"No se puede desactivar el control de inventario cuando existen movimientos."
			);
		}
		if (dependencies.hasActiveMembershipPlan(organizationId, existing.id())
				&& !"MEMBERSHIP_PLAN".equals(nextType)) {
			throw new BusinessException(
					"PRODUCT_TYPE_CHANGE_NOT_ALLOWED",
					"El producto esta vinculado a un plan de membresia activo."
			);
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
