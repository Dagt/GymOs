package com.gymos.catalog.application;

import com.gymos.audit.api.AuditService;
import com.gymos.catalog.application.command.CreateProductVariantsCommand;
import com.gymos.catalog.application.command.ProductCommand;
import com.gymos.catalog.application.port.CatalogCategoryQueryPort;
import com.gymos.catalog.application.port.CatalogDependencyQueryPort;
import com.gymos.catalog.application.port.CatalogProductCommandPort;
import com.gymos.catalog.application.port.CatalogProductCommandPort.ProductMutation;
import com.gymos.catalog.application.port.CatalogProductFamilyPort;
import com.gymos.catalog.application.port.CatalogProductFamilyPort.FamilyMutation;
import com.gymos.catalog.application.port.CatalogProductQueryPort;
import com.gymos.catalog.application.query.ProductFamilySummaryView;
import com.gymos.catalog.application.query.ProductFamilyView;
import com.gymos.catalog.application.query.ProductPageView;
import com.gymos.catalog.application.query.ProductSearchCriteria;
import com.gymos.catalog.application.query.ProductSummaryView;
import com.gymos.common.api.BusinessException;
import com.gymos.common.security.GymOsPrincipal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
class CatalogProductService {
	private static final List<String> TYPES = List.of("PHYSICAL", "SERVICE");
	private static final List<String> SUBTYPES = List.of(
			"GENERAL_PRODUCT", "GENERAL_SERVICE", "VISIT_PASS", "BOOKING", "ROUTINE",
			"CONSULTATION", "WELLNESS_SERVICE"
	);
	private static final Set<String> UNITS = Set.of(
			"PIECE", "BOTTLE", "CAN", "PACKAGE", "BOX", "BAG", "SACHET",
			"SERVING", "TABLET", "CAPSULE", "MILLILITER", "LITER", "GRAM", "KILOGRAM"
	);

	private final CatalogProductQueryPort products;
	private final CatalogProductCommandPort commands;
	private final CatalogDependencyQueryPort dependencies;
	private final CatalogCategoryQueryPort categories;
	private final CatalogProductFamilyPort families;
	private final AuditService auditService;

	CatalogProductService(CatalogProductQueryPort products, CatalogProductCommandPort commands,
			CatalogDependencyQueryPort dependencies, CatalogCategoryQueryPort categories,
			CatalogProductFamilyPort families, AuditService auditService) {
		this.products = products;
		this.commands = commands;
		this.dependencies = dependencies;
		this.categories = categories;
		this.families = families;
		this.auditService = auditService;
	}

	List<ProductFamilySummaryView> families(GymOsPrincipal principal) {
		CatalogAccessGuard.requireBranch(principal);
		return families.list(principal.organizationId(), principal.branchId());
	}

	List<ProductSummaryView> products(GymOsPrincipal principal) {
		CatalogAccessGuard.requireBranch(principal);
		return products.listProducts(principal.organizationId(), principal.branchId()).stream()
				.filter(product -> !"MEMBERSHIP_PLAN".equals(product.productType()))
				.map(product -> visible(principal, product))
				.toList();
	}

	ProductSummaryView product(GymOsPrincipal principal, UUID productId) {
		CatalogAccessGuard.requireBranch(principal);
		return visible(principal, requireProduct(principal.organizationId(), principal.branchId(), productId));
	}

	ProductSummaryView productByBarcode(GymOsPrincipal principal, String barcode) {
		CatalogAccessGuard.requireBranch(principal);
		String normalized = normalizeNullable(barcode);
		if (normalized == null) {
			throw new BusinessException("PRODUCT_BARCODE_REQUIRED", "Proporciona un codigo de barras.");
		}
		ProductSummaryView product = products.findActiveByBarcode(principal.organizationId(), principal.branchId(), normalized)
				.filter(candidate -> !"MEMBERSHIP_PLAN".equals(candidate.productType()))
				.orElseThrow(() -> new BusinessException(
						"PRODUCT_BARCODE_NOT_FOUND", "No existe un producto activo con ese codigo de barras en esta sucursal."));
		return visible(principal, product);
	}

	ProductPageView search(GymOsPrincipal principal, ProductSearchCriteria criteria) {
		CatalogAccessGuard.requireBranch(principal);
		ProductPageView page = products.search(principal.organizationId(), principal.branchId(), criteria);
		return new ProductPageView(
				page.items().stream().map(product -> visible(principal, product)).toList(),
				page.page(), page.size(), page.totalElements(), page.totalPages()
		);
	}

	ProductSummaryView createProduct(GymOsPrincipal principal, ProductCommand request, String correlationId) {
		CatalogAccessGuard.requireBranch(principal);
		validate(request);
		String sku = normalizeRequired(request.sku());
		if (products.skuExists(principal.organizationId(), sku, null)) throw duplicateSku();
		List<String> barcodes = normalizeBarcodes(request.barcode(), request.barcodes());
		validateBarcodeAvailability(principal.organizationId(), barcodes, null);
		validateCategory(principal.organizationId(), request.categoryId(), null);
		List<String> tags = normalizeTags(request.tags());
		UUID productId = UUID.randomUUID();
		OffsetDateTime now = OffsetDateTime.now();
		UUID familyId = resolveFamily(principal, request, null, now);
		String variantName = resolveVariantName(request);
		commands.create(mutation(principal, productId, request, sku, primaryBarcode(barcodes), null, null,
				now, familyId, variantName));
		commands.attachBranch(principal.organizationId(), principal.branchId(), productId, principal.userId(), now);
		commands.replaceCategory(principal.organizationId(), productId, request.categoryId(), principal.userId(), now);
		commands.replaceTags(principal.organizationId(), productId, tags, principal.userId(), now);
		commands.replaceBarcodes(principal.organizationId(), productId, barcodes, true, principal.userId(), now);
		auditService.record(principal, "PRODUCT_CREATED", "CATALOG_PRODUCT", productId, "BACKOFFICE", null, correlationId);
		return visible(principal, requireProduct(principal.organizationId(), principal.branchId(), productId));
	}

	ProductFamilyView createVariants(GymOsPrincipal principal, CreateProductVariantsCommand request,
			String correlationId) {
		CatalogAccessGuard.requireBranch(principal);
		if (request.variants() == null || request.variants().isEmpty() || request.variants().size() > 50) {
			throw new BusinessException("PRODUCT_VARIANTS_INVALID", "Agrega entre 1 y 50 variantes.");
		}
		String unit = normalizeNullable(request.unitOfMeasure());
		if (unit != null && !UNITS.contains(unit)) {
			throw new BusinessException("PRODUCT_UNIT_INVALID", "Selecciona una unidad valida para las variantes.");
		}
		validateCategory(principal.organizationId(), request.categoryId(), null);
		List<String> tags = normalizeTags(request.tags());
		OffsetDateTime now = OffsetDateTime.now();
		ProductFamilySummaryView family = resolveBatchFamily(principal, request, now);
		List<ProductSummaryView> created = new ArrayList<>();
		for (int index = 0; index < request.variants().size(); index++) {
			CreateProductVariantsCommand.Variant variant = request.variants().get(index);
			String variantName = trimToNull(variant.variantName());
			if (variantName == null) {
				throw new BusinessException("PRODUCT_VARIANT_NAME_REQUIRED", "Cada variante necesita sabor o presentacion.");
			}
			String productName = family.name() + " · " + variantName;
			List<String> barcodes = normalizeBarcodes(null, variant.barcodes());
			ProductCommand command = new ProductCommand(
					variant.sku(), productName, request.description(), "PHYSICAL", "GENERAL_PRODUCT",
					variant.priceCents(), request.currency(), request.taxable(), request.status(),
					primaryBarcode(barcodes), family.brand(), variant.presentation(), unit,
					request.inventoryTracked(), request.displayOrder() + index, request.supplierName(),
					variant.referenceCostCents(), variant.promotionalPriceCents(), variant.promotionStartsAt(),
					variant.promotionEndsAt(), request.categoryId(), tags, null, family.id(), variantName, barcodes
			);
			created.add(createProduct(principal, command, correlationId));
		}
		auditService.record(principal, "PRODUCT_VARIANTS_CREATED", "CATALOG_PRODUCT_FAMILY", family.id(),
				"BACKOFFICE", "{\"variants\":" + created.size() + "}", correlationId);
		return new ProductFamilyView(family.id(), family.name(), family.brand(), created);
	}

	ProductSummaryView updateProduct(GymOsPrincipal principal, UUID productId, ProductCommand request, String correlationId) {
		CatalogAccessGuard.requireBranch(principal);
		validate(request);
		ProductSummaryView existing = requireProduct(principal.organizationId(), principal.branchId(), productId);
		if ("MEMBERSHIP_PLAN".equals(existing.productType())) {
			throw new BusinessException("PRODUCT_UPDATE_REQUIRES_PLAN_WORKFLOW",
					"Los productos de membresia se administran desde planes.");
		}
		if (request.version() == null) {
			throw new BusinessException("PRODUCT_VERSION_REQUIRED", "Recarga el producto antes de guardar cambios.");
		}
		if (request.version() != existing.version()) throw concurrentModification();
		validateTransition(principal.organizationId(), existing, request);
		String sku = normalizeRequired(request.sku());
		if (products.skuExists(principal.organizationId(), sku, productId)) throw duplicateSku();
		List<String> barcodes = normalizeBarcodes(request.barcode(), request.barcodes());
		validateBarcodeAvailability(principal.organizationId(), barcodes, productId);
		validateCategory(principal.organizationId(), request.categoryId(), existing.categoryId());
		List<String> tags = normalizeTags(request.tags());
		OffsetDateTime now = OffsetDateTime.now();
		UUID familyId = resolveFamily(principal, request, existing, now);
		String variantName = resolveVariantName(request, existing);
		int updated = commands.update(mutation(principal, productId, request, sku, primaryBarcode(barcodes),
				existing.imageObjectKey(), existing, now, familyId, variantName));
		if (updated != 1) throw concurrentModification();
		commands.replaceCategory(principal.organizationId(), productId, request.categoryId(), principal.userId(), now);
		commands.replaceTags(principal.organizationId(), productId, tags, principal.userId(), now);
		commands.replaceBarcodes(principal.organizationId(), productId, barcodes, true, principal.userId(), now);
		auditService.record(principal, "PRODUCT_UPDATED", "CATALOG_PRODUCT", productId, "BACKOFFICE",
				describeChanges(principal, existing, request, sku, barcodes, tags), correlationId);
		return visible(principal, requireProduct(principal.organizationId(), principal.branchId(), productId));
	}

	ProductSummaryView changeStatus(GymOsPrincipal principal, UUID productId, String status, String correlationId) {
		CatalogAccessGuard.requireBranch(principal);
		String normalizedStatus = normalizeStatus(status);
		ProductSummaryView product = requireProduct(principal.organizationId(), principal.branchId(), productId);
		if ("MEMBERSHIP_PLAN".equals(product.productType())) {
			throw new BusinessException("PRODUCT_STATUS_REQUIRES_PLAN_WORKFLOW",
					"Los productos de membresia se administran desde planes.");
		}
		if (commands.changeStatus(principal.organizationId(), productId, normalizedStatus,
				principal.userId(), OffsetDateTime.now()) != 1) throw notFound();
		String eventType = "ACTIVE".equals(normalizedStatus) ? "PRODUCT_ACTIVATED" : "PRODUCT_DEACTIVATED";
		auditService.record(principal, eventType, "CATALOG_PRODUCT", productId, "BACKOFFICE", normalizedStatus, correlationId);
		return visible(principal, requireProduct(principal.organizationId(), principal.branchId(), productId));
	}

	void archiveProduct(GymOsPrincipal principal, UUID productId, String correlationId) {
		CatalogAccessGuard.requireBranch(principal);
		ProductSummaryView product = requireProduct(principal.organizationId(), principal.branchId(), productId);
		if ("MEMBERSHIP_PLAN".equals(product.productType())) {
			throw new BusinessException("PRODUCT_ARCHIVE_REQUIRES_PLAN_WORKFLOW",
					"Los productos de membresia se administran desde planes.");
		}
		if (dependencies.hasActiveMembershipPlan(principal.organizationId(), productId)) {
			throw new BusinessException("PRODUCT_ARCHIVE_NOT_ALLOWED", "El producto tiene un plan de membresia activo.");
		}
		OffsetDateTime now = OffsetDateTime.now();
		if (commands.archive(principal.organizationId(), productId, principal.userId(), now) != 1) {
			throw notFound();
		}
		commands.setBarcodesActive(principal.organizationId(), productId, false);
		auditService.record(principal, "PRODUCT_ARCHIVED", "CATALOG_PRODUCT", productId, "BACKOFFICE", null, correlationId);
	}

	ProductSummaryView restoreProduct(GymOsPrincipal principal, UUID productId, String correlationId) {
		CatalogAccessGuard.requireBranch(principal);
		ProductSummaryView archived = products.findProductIncludingArchived(principal.organizationId(), principal.branchId(), productId)
				.orElseThrow(this::notFound);
		if ("MEMBERSHIP_PLAN".equals(archived.productType())) {
			throw new BusinessException("PRODUCT_RESTORE_REQUIRES_PLAN_WORKFLOW",
					"Los productos de membresia se administran desde planes.");
		}
		if (!archived.archived()) throw new BusinessException("PRODUCT_NOT_ARCHIVED", "El producto no esta archivado.");
		if (products.skuExists(principal.organizationId(), archived.sku(), productId)) throw duplicateSku();
		validateBarcodeAvailability(principal.organizationId(), archived.barcodes(), productId);
		if (commands.restore(principal.organizationId(), productId, "INACTIVE", principal.userId(), OffsetDateTime.now()) != 1) {
			throw notFound();
		}
		commands.setBarcodesActive(principal.organizationId(), productId, true);
		auditService.record(principal, "PRODUCT_RESTORED", "CATALOG_PRODUCT", productId,
				"BACKOFFICE", "INACTIVE", correlationId);
		return visible(principal, requireProduct(principal.organizationId(), principal.branchId(), productId));
	}

	private ProductMutation mutation(GymOsPrincipal principal, UUID productId, ProductCommand request,
			String sku, String barcode, String imageObjectKey, ProductSummaryView existing, OffsetDateTime now,
			UUID familyId, String variantName) {
		return new ProductMutation(
				principal.organizationId(), productId, sku, request.name().trim(),
				CatalogBenefitsSerializer.blankToNull(request.description()), normalizeRequired(request.productType()),
				normalizeRequired(request.commercialSubtype()), request.priceCents(), normalizeRequired(request.currency()),
				request.taxable(), normalizeStatus(request.status()), barcode, trimToNull(request.brand()),
				trimToNull(request.presentation()), normalizeNullable(request.unitOfMeasure()), request.inventoryTracked(),
				request.displayOrder(), imageObjectKey, effectiveSupplier(principal, request, existing),
				effectiveReferenceCost(principal, request, existing), request.promotionalPriceCents(),
				request.promotionStartsAt(), request.promotionEndsAt(), request.version(), now, principal.userId(),
				familyId, variantName
		);
	}

	private String describeChanges(GymOsPrincipal principal, ProductSummaryView existing, ProductCommand request,
			String sku, List<String> barcodes, List<String> tags) {
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
		change(changes, "barcodes", existing.barcodes(), barcodes);
		change(changes, "brand", existing.brand(), trimToNull(request.brand()));
		change(changes, "presentation", existing.presentation(), trimToNull(request.presentation()));
		change(changes, "variantName", existing.variantName(), resolveVariantName(request, existing));
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
		String unit = normalizeNullable(request.unitOfMeasure());
		if ("PHYSICAL".equals(type) && unit != null && !UNITS.contains(unit)) {
			throw new BusinessException(
					"PRODUCT_UNIT_INVALID",
					"Selecciona una unidad valida para el producto fisico."
			);
		}
		if (!"PHYSICAL".equals(type) && unit != null) {
			throw new BusinessException(
					"PRODUCT_UNIT_INVALID",
					"Los cargos y servicios no deben definir una unidad fisica."
			);
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
		for (String barcode : normalizeBarcodes(request.barcode(), request.barcodes())) {
			if (!barcode.matches("[A-Z0-9._-]{3,128}")) {
				throw new BusinessException("PRODUCT_BARCODE_INVALID", "El codigo de barras contiene caracteres no permitidos.");
			}
		}
	}

	private void validateBarcodeAvailability(UUID organizationId, List<String> barcodes, UUID excludedProductId) {
		for (String barcode : barcodes) {
			if (products.barcodeExists(organizationId, barcode, excludedProductId)) {
				throw new BusinessException("PRODUCT_BARCODE_ALREADY_EXISTS",
						"Ya existe otro producto con el codigo de barras " + barcode + ".");
			}
		}
	}

	private ProductFamilySummaryView resolveBatchFamily(GymOsPrincipal principal,
			CreateProductVariantsCommand request, OffsetDateTime now) {
		if (request.familyId() != null) {
			return families.find(principal.organizationId(), request.familyId())
					.orElseThrow(() -> new BusinessException("PRODUCT_FAMILY_NOT_FOUND", "La familia seleccionada no existe."));
		}
		String name = trimToNull(request.familyName());
		if (name == null) {
			throw new BusinessException("PRODUCT_FAMILY_NAME_REQUIRED", "Indica el nombre del producto base.");
		}
		String brand = trimToNull(request.brand());
		if (families.findByIdentity(principal.organizationId(), name, brand).isPresent()) {
			throw new BusinessException("PRODUCT_FAMILY_ALREADY_EXISTS",
					"La familia ya existe. Seleccionala para agregar las nuevas variantes.");
		}
		UUID familyId = UUID.randomUUID();
		families.create(new FamilyMutation(familyId, principal.organizationId(), name, brand,
				CatalogBenefitsSerializer.blankToNull(request.description()), now, principal.userId()));
		return new ProductFamilySummaryView(familyId, name, brand,
				CatalogBenefitsSerializer.blankToNull(request.description()), 0, 0);
	}

	private UUID resolveFamily(GymOsPrincipal principal, ProductCommand request,
			ProductSummaryView existing, OffsetDateTime now) {
		if (!"PHYSICAL".equals(normalizeRequired(request.productType()))) return null;
		UUID requestedFamilyId = request.familyId();
		if (requestedFamilyId == null && existing != null) requestedFamilyId = existing.familyId();
		if (requestedFamilyId != null) {
			return families.find(principal.organizationId(), requestedFamilyId)
					.orElseThrow(() -> new BusinessException("PRODUCT_FAMILY_NOT_FOUND", "La familia seleccionada no existe."))
					.id();
		}
		String name = request.name().trim();
		String brand = trimToNull(request.brand());
		var matching = families.findByIdentity(principal.organizationId(), name, brand);
		if (matching.isPresent()) return matching.get().id();
		UUID familyId = UUID.randomUUID();
		families.create(new FamilyMutation(familyId, principal.organizationId(), name, brand,
				CatalogBenefitsSerializer.blankToNull(request.description()), now, principal.userId()));
		return familyId;
	}

	private String resolveVariantName(ProductCommand request) {
		return resolveVariantName(request, null);
	}

	private String resolveVariantName(ProductCommand request, ProductSummaryView existing) {
		if (!"PHYSICAL".equals(normalizeRequired(request.productType()))) return null;
		String explicit = trimToNull(request.variantName());
		if (explicit != null) return explicit;
		if (existing != null && existing.variantName() != null) return existing.variantName();
		String presentation = trimToNull(request.presentation());
		return presentation == null ? request.name().trim() : presentation;
	}

	private List<String> normalizeBarcodes(String primaryBarcode, List<String> aliases) {
		LinkedHashSet<String> normalized = new LinkedHashSet<>();
		String primary = normalizeNullable(primaryBarcode);
		if (primary != null) normalized.add(primary);
		if (aliases != null) {
			aliases.stream().filter(Objects::nonNull).map(this::normalizeNullable)
					.filter(Objects::nonNull).forEach(normalized::add);
		}
		if (normalized.size() > 20) {
			throw new BusinessException("PRODUCT_BARCODES_INVALID", "Usa hasta 20 codigos de barras por variante.");
		}
		return List.copyOf(normalized);
	}

	private String primaryBarcode(List<String> barcodes) {
		return barcodes.isEmpty() ? null : barcodes.getFirst();
	}

	private void validateCategory(UUID organizationId, UUID categoryId, UUID existingCategoryId) {
		if (categoryId == null) return;
		var category = categories.findCategory(organizationId, categoryId)
				.orElseThrow(() -> new BusinessException("CATEGORY_NOT_AVAILABLE", "La categoria no existe o esta inactiva."));
		if (!"ACTIVE".equals(category.status()) && !categoryId.equals(existingCategoryId)) {
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
	private ProductSummaryView requireProduct(UUID organizationId, UUID branchId, UUID productId) {
		return products.findProduct(organizationId, branchId, productId).orElseThrow(this::notFound);
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
	private BusinessException concurrentModification() { return new BusinessException("PRODUCT_CONCURRENT_MODIFICATION", "El producto cambio mientras lo editabas. Recargalo antes de guardar."); }
	private BusinessException notFound() { return new BusinessException("PRODUCT_NOT_FOUND", "El producto no existe en la sucursal activa o fue archivado."); }
}
