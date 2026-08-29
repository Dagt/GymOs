package com.gymos.catalog.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gymos.audit.api.AuditService;
import com.gymos.catalog.application.command.ProductCommand;
import com.gymos.catalog.application.command.CreateProductVariantsCommand;
import com.gymos.catalog.application.port.CatalogCategoryQueryPort;
import com.gymos.catalog.application.port.CatalogDependencyQueryPort;
import com.gymos.catalog.application.port.CatalogProductCommandPort;
import com.gymos.catalog.application.port.CatalogProductCommandPort.ProductMutation;
import com.gymos.catalog.application.port.CatalogProductFamilyPort;
import com.gymos.catalog.application.port.CatalogProductQueryPort;
import com.gymos.catalog.application.query.ProductSummaryView;
import com.gymos.catalog.application.query.ProductFamilySummaryView;
import com.gymos.common.api.BusinessException;
import com.gymos.common.security.GymOsPrincipal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CatalogProductServiceTests {
	private CatalogProductQueryPort products;
	private CatalogProductCommandPort commands;
	private CatalogDependencyQueryPort dependencies;
	private CatalogProductFamilyPort families;
	private CatalogProductService service;
	private GymOsPrincipal principal;

	@BeforeEach
	void setUp() {
		products = mock(CatalogProductQueryPort.class);
		commands = mock(CatalogProductCommandPort.class);
		dependencies = mock(CatalogDependencyQueryPort.class);
		families = mock(CatalogProductFamilyPort.class);
		service = new CatalogProductService(products, commands, dependencies,
				mock(CatalogCategoryQueryPort.class), families, mock(AuditService.class));
		principal = new GymOsPrincipal(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
				"owner", "Owner", Set.of("catalog.manage"));
	}

	@Test
	void exactBarcodeLookupNormalizesAndReturnsOnlyActiveProduct() {
		ProductSummaryView expected = product(false, "ACTIVE", "750123");
		when(products.findActiveByBarcode(principal.organizationId(), principal.branchId(), "750123"))
				.thenReturn(Optional.of(expected));
		assertThat(service.productByBarcode(principal, " 750123 ")).isEqualTo(expected);
	}

	@Test
	void exactBarcodeLookupRejectsMissingProduct() {
		when(products.findActiveByBarcode(principal.organizationId(), principal.branchId(), "ABC"))
				.thenReturn(Optional.empty());
		assertThatThrownBy(() -> service.productByBarcode(principal, "abc"))
				.isInstanceOf(BusinessException.class)
				.hasMessageContaining("codigo de barras");
	}

	@Test
	void restoreRevalidatesSkuAndBarcodeAndReturnsInactiveProduct() {
		UUID productId = UUID.randomUUID();
		ProductSummaryView archived = product(true, "INACTIVE", "750123", productId);
		ProductSummaryView restored = product(false, "INACTIVE", "750123", productId);
		when(products.findProductIncludingArchived(principal.organizationId(), principal.branchId(), productId))
				.thenReturn(Optional.of(archived));
		when(products.skuExists(principal.organizationId(), archived.sku(), productId)).thenReturn(false);
		when(products.barcodeExists(principal.organizationId(), archived.barcode(), productId)).thenReturn(false);
		when(commands.restore(eq(principal.organizationId()), eq(productId), eq("INACTIVE"), eq(principal.userId()), any())).thenReturn(1);
		when(products.findProduct(principal.organizationId(), principal.branchId(), productId)).thenReturn(Optional.of(restored));
		assertThat(service.restoreProduct(principal, productId, "test").status()).isEqualTo("INACTIVE");
		verify(commands).restore(eq(principal.organizationId()), eq(productId), eq("INACTIVE"), eq(principal.userId()), any());
	}

	@Test
	void readerWithoutCostPermissionDoesNotReceiveSupplierOrReferenceCost() {
		GymOsPrincipal reader = new GymOsPrincipal(UUID.randomUUID(), principal.organizationId(), principal.branchId(),
				"reader", "Reader", Set.of("catalog.read"));
		UUID productId = UUID.randomUUID();
		when(products.findProduct(reader.organizationId(), reader.branchId(), productId)).thenReturn(Optional.of(costedProduct(productId)));

		ProductSummaryView result = service.product(reader, productId);

		assertThat(result.supplierName()).isNull();
		assertThat(result.referenceCostCents()).isNull();
		assertThat(result.promotionalPriceCents()).isEqualTo(900L);
	}

	@Test
	void genericProductFlowRejectsMembershipProducts() {
		ProductCommand request = new ProductCommand(
				"PLAN", "Plan tecnico", null, "MEMBERSHIP_PLAN", 1_000,
				"MXN", false, "ACTIVE", null, null, null, null,
				false, 0, null, List.of()
		);
		assertThatThrownBy(() -> service.createProduct(principal, request, "test"))
				.isInstanceOfSatisfying(BusinessException.class, exception ->
						assertThat(exception.code()).isEqualTo("PRODUCT_TYPE_INVALID"));
	}

	@Test
	void genericProductFlowRejectsVisitPackageProducts() {
		ProductCommand request = new ProductCommand(
				"VISITAS-10", "10 visitas", null, "SERVICE", "VISIT_PASS", 50_000,
				"MXN", true, "ACTIVE", null, null, null, null,
				false, 0, null, null, null, null, null, null, List.of()
		);

		assertThatThrownBy(() -> service.createProduct(principal, request, "test"))
				.isInstanceOfSatisfying(BusinessException.class, exception ->
						assertThat(exception.code()).isEqualTo("PRODUCT_COMMERCIAL_SUBTYPE_INVALID"));
		verify(commands, never()).create(any());
	}

	@Test
	void visitPackageProductIsHiddenAndCannotBeEditedAsAnArticle() {
		UUID packageProductId = UUID.randomUUID();
		ProductSummaryView regular = costedProduct(UUID.randomUUID());
		ProductSummaryView packageProduct = visitPackageProduct(packageProductId);
		when(products.listProducts(principal.organizationId(), principal.branchId()))
				.thenReturn(List.of(regular, packageProduct));
		when(products.findProduct(principal.organizationId(), principal.branchId(), packageProductId))
				.thenReturn(Optional.of(packageProduct));

		assertThat(service.products(principal)).containsExactly(regular);
		assertThatThrownBy(() -> service.updateProduct(
				principal, packageProductId, command(packageProduct.version()), "test"))
				.isInstanceOfSatisfying(BusinessException.class, exception ->
						assertThat(exception.code()).isEqualTo("PRODUCT_REQUIRES_VISIT_PACKAGE_WORKFLOW"));
		verify(commands, never()).update(any());
	}

	@Test
	void updateWithoutCostPermissionPreservesProtectedCommercialFields() {
		GymOsPrincipal updater = new GymOsPrincipal(UUID.randomUUID(), principal.organizationId(), principal.branchId(),
				"updater", "Updater", Set.of("catalog.update"));
		UUID productId = UUID.randomUUID();
		ProductSummaryView existing = costedProduct(productId);
		ProductSummaryView updated = costedProduct(productId);
		when(products.findProduct(updater.organizationId(), updater.branchId(), productId))
				.thenReturn(Optional.of(existing), Optional.of(updated));
		when(commands.update(any())).thenReturn(1);
		ProductCommand request = command(existing.version());

		service.updateProduct(updater, productId, request, "test");

		ArgumentCaptor<ProductMutation> mutation = ArgumentCaptor.forClass(ProductMutation.class);
		verify(commands).update(mutation.capture());
		assertThat(mutation.getValue().supplierName()).isEqualTo(existing.supplierName());
		assertThat(mutation.getValue().referenceCostCents()).isEqualTo(existing.referenceCostCents());
		assertThat(mutation.getValue().expectedVersion()).isEqualTo(existing.version());
	}

	@Test
	void staleVersionIsRejectedBeforeMutation() {
		UUID productId = UUID.randomUUID();
		ProductSummaryView existing = costedProduct(productId);
		when(products.findProduct(principal.organizationId(), principal.branchId(), productId)).thenReturn(Optional.of(existing));

		assertThatThrownBy(() -> service.updateProduct(principal, productId, command(existing.version() + 1), "test"))
				.isInstanceOfSatisfying(BusinessException.class, exception ->
						assertThat(exception.code()).isEqualTo("PRODUCT_CONCURRENT_MODIFICATION"));
		verify(commands, never()).update(any());
	}

	@Test
	void physicalProductRejectsUnitOutsideClosedCatalog() {
		assertThatThrownBy(() -> service.createProduct(
				principal,
				command(0, "CUSTOM_BUCKET"),
				"test"
		))
				.isInstanceOfSatisfying(BusinessException.class, exception ->
						assertThat(exception.code()).isEqualTo("PRODUCT_UNIT_INVALID"));
		verify(commands, never()).create(any());
	}

	@Test
	void createProductPersistsEveryBarcodeAliasAndKeepsFirstAsLegacyPrimary() {
		when(families.findByIdentity(eq(principal.organizationId()), any(), any())).thenReturn(Optional.empty());
		when(products.findProduct(eq(principal.organizationId()), eq(principal.branchId()), any()))
				.thenAnswer(invocation -> Optional.of(createdProduct(invocation.getArgument(2))));
		ProductCommand request = new ProductCommand(
				"PSY-RED", "Psychotic", null, "PHYSICAL", "GENERAL_PRODUCT", 65_000,
				"MXN", true, "ACTIVE", null, "Insane Labz", "Bote", "PIECE", true, 0,
				null, null, null, null, null, null, List.of(), null, null, "Frutos rojos",
				List.of("750100000001", "750100000009")
		);

		service.createProduct(principal, request, "test");

		ArgumentCaptor<ProductMutation> mutation = ArgumentCaptor.forClass(ProductMutation.class);
		verify(commands).create(mutation.capture());
		assertThat(mutation.getValue().barcode()).isEqualTo("750100000001");
		verify(commands).replaceBarcodes(eq(principal.organizationId()), eq(mutation.getValue().productId()),
				eq(List.of("750100000001", "750100000009")), eq(true), eq(principal.userId()), any());
	}

	@Test
	void batchAppliesCommonValuesAndKeepsVariantOverridesInsideOneExistingFamily() {
		UUID familyId = UUID.randomUUID();
		when(families.find(principal.organizationId(), familyId)).thenReturn(Optional.of(
				new ProductFamilySummaryView(familyId, "Psychotic", "Insane Labz", null, 0, 0)));
		when(products.findProduct(eq(principal.organizationId()), eq(principal.branchId()), any()))
				.thenAnswer(invocation -> Optional.of(createdProduct(invocation.getArgument(2))));
		CreateProductVariantsCommand request = new CreateProductVariantsCommand(
				familyId, null, null, null, "Bote de 35 scoops", 65_000, 40_000L,
				"MXN", true, "ACTIVE", "PIECE", true, 5, 0,
				null, null, List.of("preentreno"), List.of(
				new CreateProductVariantsCommand.Variant("Manzana", "PSY-MAN", "750100000001",
						List.of(), null, null, null, null, null, null),
				new CreateProductVariantsCommand.Variant("Frutos rojos", "PSY-FRU", "750100000002",
						List.of("750100000009"), null, null, null, null, null, null),
				new CreateProductVariantsCommand.Variant("Chicle", "PSY-CHI", "750100000003",
						List.of(), "Bote de 40 scoops", 67_000L, 41_000L, null, null, null)
		));

		var result = service.createVariants(principal, request, "test");

		assertThat(result.id()).isEqualTo(familyId);
		assertThat(result.variants()).hasSize(3);
		ArgumentCaptor<ProductMutation> mutations = ArgumentCaptor.forClass(ProductMutation.class);
		verify(commands, org.mockito.Mockito.times(3)).create(mutations.capture());
		assertThat(mutations.getAllValues()).extracting(ProductMutation::familyId).containsOnly(familyId);
		assertThat(mutations.getAllValues()).extracting(ProductMutation::variantName)
				.containsExactly("Manzana", "Frutos rojos", "Chicle");
		assertThat(mutations.getAllValues()).extracting(ProductMutation::presentation)
				.containsExactly("Bote de 35 scoops", "Bote de 35 scoops", "Bote de 40 scoops");
		assertThat(mutations.getAllValues()).extracting(ProductMutation::priceCents)
				.containsExactly(65_000L, 65_000L, 67_000L);
		assertThat(mutations.getAllValues()).extracting(ProductMutation::referenceCostCents)
				.containsExactly(40_000L, 40_000L, 41_000L);
		verify(commands).replaceBarcodes(eq(principal.organizationId()), any(),
				eq(List.of("750100000002", "750100000009")), eq(true), eq(principal.userId()), any());
	}

	@Test
	void duplicatedSkuInsideBatchIsRejectedBeforeAnyWrite() {
		CreateProductVariantsCommand request = batchWithVariants(List.of(
				variant("Manzana", "PSY-MAN", "750100000001"),
				variant("Chicle", "PSY-MAN", "750100000002")
		));

		assertThatThrownBy(() -> service.createVariants(principal, request, "test"))
				.isInstanceOfSatisfying(BusinessException.class, exception ->
						assertThat(exception.code()).isEqualTo("PRODUCT_VARIANT_SKU_DUPLICATED"));
		verify(commands, never()).create(any());
		verify(families, never()).create(any());
	}

	@Test
	void duplicatedBarcodeInsideBatchIsRejectedBeforeAnyWrite() {
		CreateProductVariantsCommand request = batchWithVariants(List.of(
				variant("Manzana", "PSY-MAN", "750100000001"),
				variant("Chicle", "PSY-CHI", "750100000001")
		));

		assertThatThrownBy(() -> service.createVariants(principal, request, "test"))
				.isInstanceOfSatisfying(BusinessException.class, exception ->
						assertThat(exception.code()).isEqualTo("PRODUCT_VARIANT_BARCODE_DUPLICATED"));
		verify(commands, never()).create(any());
		verify(families, never()).create(any());
	}

	private CreateProductVariantsCommand batchWithVariants(List<CreateProductVariantsCommand.Variant> variants) {
		return new CreateProductVariantsCommand(
				null, "Psychotic", "Insane Labz", null, "Bote", 55_000, 32_000L,
				"MXN", true, "ACTIVE", "PIECE", true, null, 0, null, null, List.of(), variants);
	}

	private CreateProductVariantsCommand.Variant variant(String name, String sku, String barcode) {
		return new CreateProductVariantsCommand.Variant(
				name, sku, barcode, List.of(), null, null, null, null, null, null);
	}

	private ProductCommand command(long version) {
		return command(version, "PIECE");
	}

	private ProductCommand command(long version, String unitOfMeasure) {
		return new ProductCommand(
				"SKU-1", "Producto actualizado", null, "PHYSICAL", "GENERAL_PRODUCT", 1_000,
				"MXN", true, "ACTIVE", "750123", null, null, unitOfMeasure, true, 0,
				"Proveedor malicioso", 1L, 900L, null, null, null, List.of(), version
		);
	}

	private ProductSummaryView product(boolean archived, String status, String barcode) {
		return product(archived, status, barcode, UUID.randomUUID());
	}

	private ProductSummaryView product(boolean archived, String status, String barcode, UUID id) {
		OffsetDateTime now = OffsetDateTime.now();
		return new ProductSummaryView(id, "SKU-1", "Producto", null, "PHYSICAL", 1000, "MXN", true,
				status, barcode, null, null, null, true, 0, null, null, null, List.of(), archived,
				archived ? now : null, now, now);
	}

	private ProductSummaryView costedProduct(UUID id) {
		OffsetDateTime now = OffsetDateTime.now();
		return new ProductSummaryView(
				id, "SKU-1", "Producto", null, "PHYSICAL", "GENERAL_PRODUCT", 1_000, "MXN", true,
				"ACTIVE", "750123", null, null, null, true, 0, null,
				"Proveedor original", 700L, 900L, null, null, null, null, List.of(), false, null, now, now, 0
		);
	}

	private ProductSummaryView createdProduct(UUID id) {
		OffsetDateTime now = OffsetDateTime.now();
		return new ProductSummaryView(
				id, "SKU", "Psychotic", null, "PHYSICAL", "GENERAL_PRODUCT", 65_000, "MXN", true,
				"ACTIVE", null, "Insane Labz", "Bote", "PIECE", true, 0, null,
				null, null, null, null, null, null, null, List.of(), false, null, now, now, 0,
				null, "Psychotic", "Variante", List.of()
		);
	}

	private ProductSummaryView visitPackageProduct(UUID id) {
		OffsetDateTime now = OffsetDateTime.now();
		return new ProductSummaryView(
				id, "VISITAS-10", "10 visitas", null, "SERVICE", "VISIT_PASS", 50_000,
				"MXN", true, "ACTIVE", null, null, null, null, false, 0, null,
				null, null, null, null, null, null, null, List.of(), false, null,
				now, now, 0
		);
	}
}
