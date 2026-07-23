package com.gymos.catalog.application;

import com.gymos.audit.api.AuditService;
import com.gymos.catalog.application.command.ProductCommand;
import com.gymos.catalog.application.port.CatalogCategoryQueryPort;
import com.gymos.catalog.application.port.CatalogDependencyQueryPort;
import com.gymos.catalog.application.port.CatalogProductCommandPort;
import com.gymos.catalog.application.port.CatalogProductCommandPort.ProductMutation;
import com.gymos.catalog.application.port.CatalogProductQueryPort;
import com.gymos.catalog.application.query.ProductPageView;
import com.gymos.catalog.application.query.ProductSearchCriteria;
import com.gymos.catalog.application.query.ProductSummaryView;
import com.gymos.common.api.BusinessException;
import com.gymos.common.security.GymOsPrincipal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
class CatalogProductService {
	private static final List<String> TYPES = List.of("PHYSICAL", "SERVICE");
	private static final List<String> SUBTYPES = List.of(
			"GENERAL_PRODUCT", "GENERAL_SERVICE", "VISIT_PASS", "BOOKING", "ROUTINE",
			"CONSULTATION", "WELLNESS_SERVICE"
	);

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
		return products.listProducts(principal.organizationId()).stream()
				.filter(product -> !"MEMBERSHIP_PLAN".equals(product.productType()))
				.map(product -> visible(principal, product))
				.toList();
	}

	ProductSummaryView product(GymOsPrincipal principal, UUID productId) {
		CatalogAccessGuard.requirePrincipal(principal);
		return visible(principal, requireProduct(principal.organizationId(), productId));
	}

	ProductSummaryView productByBarcode(GymOsPrincipal principal, String barcode) {
		CatalogAccessGuard.requirePrincipal(principal);
		String normalized = normalizeNullable(barcode);
		if (normalized == null) {
			throw new BusinessException("PRODUCT_BARCODE_REQUIRED", "Proporciona un codigo de barras.");
		}
		ProductSummaryView product = products.findActiveByBarcode(principal.organizationId(), normalized)
				.filter(candidate -> !"MEMBERSHIP_PLAN".equals(candidate.productType()))
				.orElseThrow(() -> new BusinessException(
						"PRODUCT_BARCODE_NOT_FOUND", "No existe un producto activo con ese codigo de barras."));
		return visible(principal, product);
	}

	ProductPageView search(GymOsPrincipal principal, ProductSearchCriteria criteria) {
		CatalogAccessGuard.requirePrincipal(principal);
		ProductPageView page = products.search(principal.organizationId(), criteria);
		return new ProductPageView(
				page.items().stream().map(product -> visible(principal, product)).toList(),
				page.page(), page.size(), page.totalElements(), page.totalPages()
		);
	}

	ProductSummaryView createProduct(GymOsPrincipal principal, ProductCommand request, String correlationId) {
		CatalogAccessGuard.requirePrincipal(principal);
		validate(request);
		String sku = normalizeRequired(request.sku());
		if (products.skuExists(principal.organizationId(), sku, null)) throw duplicateSku();
		String barcode = normalizeNullable(request.barcode());
		validateBarcodeAvailability(principal.organizationId(), barcode, null);
		validateCategory(principal.organizationId(), request.categoryId());
		List<String> tags = normalizeTags(request.tags());
		UUID productId = UUID.randomUUID();
		OffsetDateTime now = OffsetDateTime.now();
		commands.create(mutation(principal, productId, request, sku, barcode, null, null, now));
		commands.replaceCategory(principal.organizationId(), productId, request.categoryId(), principal.userId(), now);
		commands.replaceTags(principal.organizationId(), productId, tags, principal.userId(), now);
		auditService.record(principal, "PRODUCT_CREATED", "CATALOG_PRODUCT", productId, "BACKOFFICE", null, correlationId);
		return visible(principal, requireProduct(principal.organizationId(), productId));
	}

	ProductSummaryView updateProduct(GymOsPrincipal principal, UUID productId, ProductCommand request, String correlationId) {
		CatalogAccessGuard.requirePrincipal(principal);
		validate(request);
		ProductSummaryView existing = requireProduct(principal.organizationId(), productId);
		if ("MEMBERSHIP_PLAN".equals(existing.productType())) {
			throw new BusinessException("PRODUCT_UPDATE_REQUIRES_PLAN_WORKFLOW",
					"Los productos de membresia se administran desde planes.");
		}
		validateTransition(principal.organizationId(), existing, request);
		String sku = normalizeRequired(request.sku());
		if (products.skuExists(principal.organizationId(), sku, productId)) throw duplicateSku();
		String barcode = normalizeNullable(request.barcode());
		validateBarcodeAvailability(principal.organizationId(), barcode, productId);
		validateCategory(principal.organizationId(), request.categoryId());
		List<String> tags = normalizeTags(request.tags());
		OffsetDateTime now = OffsetDateTime.now();
		int updated = commands.update(mutation(principal, productId, request, sku, barcode, existing.imageObjectKey(), existing, now));
		if (updated != 1) throw notFound();
		commands.replaceCategory(principal.organizationId(), productId, request.categoryId(), principal.userId(), now);
		commands.replaceTags(principal.organizationId(), productId, tags, principal.userId(), now);
		auditService.record(principal, "PRODUCT_UPDATED", "CATALOG_PRODUCT", productId, "BACKOFFICE",
				describeChanges(principal, existing, request, sku, barcode, tags), correlationId);
		return visible(principal, requireProduct(principal.organizationId(), productId));
	}

	ProductSummaryView changeStatus(GymOsPrincipal principal, UUID productId, String status, String correlationId) {
		CatalogAccessGuard.requirePrincipal(principal);
		String normalizedStatus = normalizeStatus(status);
		ProductSummaryView product = requireProduct(principal.organizationId(), productId);
		if ("MEMBERSHIP_PLAN".equals(product.productType())) {
			throw new BusinessException("PRODUCT_STATUS_REQUIRES_PLAN_WORKFLOW",
					"Los productos de membresia se administran desde planes.");
		}
		if (commands.changeStatus(principal.organizationId(), productId, normalizedStatus,
				principal.userId(), OffsetDateTime.now()) != 1) throw notFound();
		String eventType = "ACTIVE".equals(normalizedStatus) ? "PRODUCT_ACTIVATED" : "PRODUCT_DEACTIVATED";
		auditService.record(principal, eventType, "CATALOG_PRODUCT", productId, "BACKOFFICE", normalizedStatus, correlationId);
		return visible(principal, requireProduct(principal.organizationId(), productId));
	}

	void archiveProduct(GymOsPrincipal principal, UUID productId, String correlationId) {
		CatalogAccessGuard.requirePrincipal(principal);
		ProductSummaryView product = requireProduct(principal.organizationId(), productId);
		if ("MEMBERSHIP_PLAN".equals(product.productType())) {
			throw new BusinessException("PRODUCT_ARCHIVE_REQUIRES_PLAN_WORKFLOW",
					"Los productos de membresia se administran desde planes.");
		}
		if (dependencies.hasActiveMembershipPlan(principal.organizationId(), productId)) {
			throw new BusinessException("PRODUCT_ARCHIVE_NOT_ALLOWED", "El producto tiene un plan de membresia activo.");
		}
		if (commands.archive(principal.organizationId(), productId, principal.userId(), OffsetDateTime.now()) != 1) {
			throw notFound();
		}
		auditService.record(principal, "PRODUCT_ARCHIVED", "CATALOG_PRODUCT", productId, "BACKOFFICE", null, correlationId);
	}

	ProductSummaryView restoreProduct(GymOsPrincipal principal, UUID productId, String correlationId) {
		CatalogAccessGuard.requirePrincipal(principal);
		ProductSummaryView archived = products.findProductIncludingArchived(principal.organizationId(), productId)
				.orElseThrow(this::notFound);
		if ("MEMBERSHIP_PLAN".equals(archived.productType())) {
			throw new BusinessException("PRODUCT_RESTORE_REQUIRES_PLAN_WORKFLOW",
					"Los productos de membresia se administran desde planes.");
		}
		if (!archived.archived()) throw new BusinessException("PRODUCT_NOT_ARCHIVED", "El producto no esta archivado.");
		if (products.skuExists(principal.organizationId(), archived.sku(), productId)) throw duplicateSku();
		validateBarcodeAvailability(principal.organizationId(), archived.barcode(), productId);
		if (commands.restore(principal.organizationId(), productId, "INACTIVE", principal.userId(), OffsetDateTime.now()) != 1) {
			throw notFound();
		}
		auditService.record(principal, "PRODUCT_RESTORED", "CATALOG_PRODUCT", productId,
				"BACKOFFICE", "INACTIVE", correlationId);
		return visible(principal, requireProduct(principal.organizationId(), productId));
	}

	private ProductMutation mutation(GymOsPrincipal principal, UUID productId, ProductCommand request,
			String sku, String barcode, String imageObjectKey, ProductSummaryView existing, OffsetDateTime now) {
		return new ProductMutation(
				principal.organizationId(), productId, sku, request.name().trim(),
				CatalogBenefitsSerializer.blankToNull(request.description()), normalizeRequired(request.productType()),
				normalizeRequired(request.commercialSubtype()), request.priceCents(), normalizeRequired(request.currency()),
				request.taxable(), normalizeStatus(request.status()), barcode, trimToNull(request.brand()),
				trimToNull(request.presentation()), normalizeNullable(request.unitOfMeasure()), request.inventoryTracked(),
				request.displayOrder(), imageObjectKey, effectiveSupplier(principal, request, existing),
				effectiveReferenceCost(principal, request, existing), request.promotionalPriceCents(),
				request.promotionStartsAt(), request.promotionEndsAt(), now, principal.userId()
		);
	}

	private String describeChanges(GymOsPrincipal principal, ProductSummaryView existing, ProductCommand request,
			String sku, String barcode, List<String> tags) {
		List<String> changes = new ArrayList<>();
		change(changes, "sku", existing.sku(), sku);
		change(changes, "name", existing.name(), request.name().trim());
		change(changes, "description", existing.description(), CatalogBenefitsSerializer.blankToNull(request.description()));
		change(changes, "productType", existing.productType(), normalizeRequired(request.productType()));
		change(changes, "commercialSubtype", existing.commercialSubtype(), normalizeRequired(request.commercialSubtype()));
		change(changes, "priceCents", existing.priceCents(), request.priceCents());
		change(changes, "currency", existing.currency(), normalizeRequired(request.currency()));
		change(changes, "taxable", existing.taxable(), request.taxable());
		change(changes, "status", existing.status(), normalizeStatus(request.status()));
		change(changes, "barcode", existing.barcode(), barcode);
		change(changes, "brand", existing.brand(), trimToNull(request.brand()));
		change(changes, "presentation", existing.presentation(), trimToNull(request.presentation()));
		change(changes, "unitOfMeasure", existing.unitOfMeasure(), normalizeNullable(request.unitOfMeasure()));
		change(changes, "inventoryTracked", existing.inventoryTracked(), request.inventoryTracked());
		change(changes, "displayOrder", existing.displayOrder(), request.displayOrder());
		if (canReadCosts(principal)) {
			change(changes, "supplierName", existing.supplierName(), trimToNull(request.supplierName()));
			change(changes, "referenceCostCents", existing.referenceCostCents(), request.referenceCostCents());
		}
		change(changes, "promotionalPriceCents", existing.promotionalPriceCents(), request.promotionalPriceCents());
		change(changes, "promotionStartsAt", existing.promotionStartsAt(), request.promotionStartsAt());
		change(changes, "promotionEndsAt", existing.promotionEndsAt(), request.promotionEndsAt());
		change(changes, "categoryId", existing.categoryId(), request.categoryId());
		change(changes, "tags", existing.tags(), tags);
		return changes.isEmpty() ? "{}" : "{" + String.join(",", changes) + "}";
	}

	private void change(List<String> changes, String field, Object before, Object after) {
		if (!Objects.equals(before, after)) {
			changes.add("\"" + field + "\":{\"before\":\"" + safe(before)
					+ "\",\"after\":\"" + safe(after) + "\"}");
		}
	}

	private void validate(ProductCommand request) {
		String type = normalizeRequired(request.productType());
		String subtype = normalizeRequired(request.commercialSubtype());
		if (!TYPES.contains(type)) {
			throw new BusinessException("PRODUCT_TYPE_INVALID",
					"Los productos de membresia deben crearse desde el flujo de planes.");
		}
		if (!SUBTYPES.contains(subtype)) {
			throw new BusinessException("PRODUCT_COMMERCIAL_SUBTYPE_INVALID", "El subtipo comercial no es valido.");
		}
		if ("PHYSICAL".equals(type) != "GENERAL_PRODUCT".equals(subtype)) {
			if ("PHYSICAL".equals(type) || "GENERAL_PRODUCT".equals(subtype)) {
				throw new BusinessException("PRODUCT_COMMERCIAL_SUBTYPE_INVALID",
						"Los productos fisicos deben usar el subtipo GENERAL_PRODUCT.");
			}
		}
		if (request.inventoryTracked() && !"PHYSICAL".equals(type)) {
			throw new BusinessException("PRODUCT_INVENTORY_TRACKING_INVALID",
					"Solo los productos fisicos pueden controlar inventario.");
		}
		if (request.displayOrder() < 0) {
			throw new BusinessException("PRODUCT_DISPLAY_ORDER_INVALID", "El orden de visualizacion no puede ser negativo.");
		}
		if (request.referenceCostCents() != null && request.referenceCostCents() < 0) {
			throw new BusinessException("PRODUCT_REFERENCE_COST_INVALID", "El costo de referencia no puede ser negativo.");
		}
		if (request.promotionalPriceCents() != null) {
			if (request.promotionalPriceCents() < 0 || request.promotionalPriceCents() > request.priceCents()) {
				throw new BusinessException("PRODUCT_PROMOTIONAL_PRICE_INVALID",
						"El precio promocional debe estar entre cero y el precio regular.");
			}
		}
		if (request.promotionStartsAt() != null && request.promotionEndsAt() != null
				&& !request.promotionEndsAt().isAfter(request.promotionStartsAt())) {
			throw new BusinessException("PRODUCT_PROMOTION_DATES_INVALID",
					"La vigencia final debe ser posterior al inicio de la promocion.");
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
		if (categoryId != null && categories.findCategory(organizationId, categoryId)
				.filter(category -> "ACTIVE".equals(category.status())).isEmpty()) {
			throw new BusinessException("CATEGORY_NOT_AVAILABLE", "La categoria no existe o esta inactiva.");
		}
	}

	private List<String> normalizeTags(List<String> tags) {
		if (tags == null) return List.of();
		List<String> normalized = tags.stream().filter(Objects::nonNull).map(String::trim)
				.filter(tag -> !tag.isBlank()).distinct().toList();
		if (normalized.size() > 20 || normalized.stream().anyMatch(tag -> tag.length() > 80 || tag.contains(","))) {
			throw new BusinessException("PRODUCT_TAGS_INVALID", "Usa hasta 20 etiquetas sin comas.");
		}
		return normalized;
	}

	private void validateTransition(UUID organizationId, ProductSummaryView existing, ProductCommand request) {
		String nextType = normalizeRequired(request.productType());
		boolean hasMovements = dependencies.hasInventoryMovements(organizationId, existing.id());
		if (hasMovements && !existing.productType().equals(nextType)) {
			throw new BusinessException("PRODUCT_TYPE_CHANGE_NOT_ALLOWED",
					"No se puede cambiar el tipo de un producto con movimientos de inventario.");
		}
		if (hasMovements && existing.inventoryTracked() && !request.inventoryTracked()) {
			throw new BusinessException("PRODUCT_INVENTORY_TRACKING_CHANGE_NOT_ALLOWED",
					"No se puede desactivar el control de inventario cuando existen movimientos.");
		}
		if (dependencies.hasActiveMembershipPlan(organizationId, existing.id())) {
			throw new BusinessException("PRODUCT_TYPE_CHANGE_NOT_ALLOWED",
					"El producto esta vinculado a un plan de membresia activo.");
		}
	}

	private ProductSummaryView visible(GymOsPrincipal principal, ProductSummaryView product) {
		return product.visibleTo(principal.permissions());
	}
	private boolean canReadCosts(GymOsPrincipal principal) {
		return principal.permissions().contains("catalog.cost.read")
				|| principal.permissions().contains("catalog.manage")
				|| principal.permissions().contains("platform.admin");
	}
	private String effectiveSupplier(GymOsPrincipal principal, ProductCommand request, ProductSummaryView existing) {
		return canReadCosts(principal) ? trimToNull(request.supplierName()) : existing == null ? null : existing.supplierName();
	}
	private Long effectiveReferenceCost(GymOsPrincipal principal, ProductCommand request, ProductSummaryView existing) {
		return canReadCosts(principal) ? request.referenceCostCents() : existing == null ? null : existing.referenceCostCents();
	}
	private String safe(Object value) { return String.valueOf(value).replace("\\", "\\\\").replace("\"", "\\\""); }
	private ProductSummaryView requireProduct(UUID organizationId, UUID productId) {
		return products.findProduct(organizationId, productId).orElseThrow(this::notFound);
	}
	private String normalizeRequired(String value) { return value == null ? "" : value.trim().toUpperCase(); }
	private String normalizeNullable(String value) { String normalized = normalizeRequired(value); return normalized.isBlank() ? null : normalized; }
	private String normalizeStatus(String status) {
		String normalized = normalizeRequired(status);
		if (!List.of("ACTIVE", "INACTIVE").contains(normalized)) {
			throw new BusinessException("PRODUCT_STATUS_INVALID", "El estado del producto no es valido.");
		}
		return normalized;
	}
	private String trimToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
	private BusinessException duplicateSku() { return new BusinessException("PRODUCT_SKU_ALREADY_EXISTS", "Ya existe otro producto con ese SKU."); }
	private BusinessException notFound() { return new BusinessException("PRODUCT_NOT_FOUND", "El producto no existe o fue archivado."); }
}
