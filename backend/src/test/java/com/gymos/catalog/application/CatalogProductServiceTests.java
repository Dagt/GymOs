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
import com.gymos.catalog.application.port.CatalogCategoryQueryPort;
import com.gymos.catalog.application.port.CatalogDependencyQueryPort;
import com.gymos.catalog.application.port.CatalogProductCommandPort;
import com.gymos.catalog.application.port.CatalogProductCommandPort.ProductMutation;
import com.gymos.catalog.application.port.CatalogProductQueryPort;
import com.gymos.catalog.application.query.ProductSummaryView;
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
	private CatalogProductService service;
	private GymOsPrincipal principal;

	@BeforeEach
	void setUp() {
		products = mock(CatalogProductQueryPort.class);
		commands = mock(CatalogProductCommandPort.class);
		dependencies = mock(CatalogDependencyQueryPort.class);
		service = new CatalogProductService(products, commands, dependencies,
				mock(CatalogCategoryQueryPort.class), mock(AuditService.class));
		principal = new GymOsPrincipal(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
				"owner", "Owner", Set.of("catalog.manage"));
	}

	@Test
	void exactBarcodeLookupNormalizesAndReturnsOnlyActiveProduct() {
		ProductSummaryView expected = product(false, "ACTIVE", "750123");
		when(products.findActiveByBarcode(principal.organizationId(), "750123")).thenReturn(Optional.of(expected));
		assertThat(service.productByBarcode(principal, " 750123 ")).isEqualTo(expected);
	}

	@Test
	void exactBarcodeLookupRejectsMissingProduct() {
		when(products.findActiveByBarcode(principal.organizationId(), "ABC")).thenReturn(Optional.empty());
		assertThatThrownBy(() -> service.productByBarcode(principal, "abc"))
				.isInstanceOf(BusinessException.class)
				.hasMessageContaining("codigo de barras");
	}

	@Test
	void restoreRevalidatesSkuAndBarcodeAndReturnsInactiveProduct() {
		UUID productId = UUID.randomUUID();
		ProductSummaryView archived = product(true, "INACTIVE", "750123", productId);
		ProductSummaryView restored = product(false, "INACTIVE", "750123", productId);
		when(products.findProductIncludingArchived(principal.organizationId(), productId)).thenReturn(Optional.of(archived));
		when(products.skuExists(principal.organizationId(), archived.sku(), productId)).thenReturn(false);
		when(products.barcodeExists(principal.organizationId(), archived.barcode(), productId)).thenReturn(false);
		when(commands.restore(eq(principal.organizationId()), eq(productId), eq("INACTIVE"), eq(principal.userId()), any())).thenReturn(1);
		when(products.findProduct(principal.organizationId(), productId)).thenReturn(Optional.of(restored));
		assertThat(service.restoreProduct(principal, productId, "test").status()).isEqualTo("INACTIVE");
		verify(commands).restore(eq(principal.organizationId()), eq(productId), eq("INACTIVE"), eq(principal.userId()), any());
	}

	@Test
	void readerWithoutCostPermissionDoesNotReceiveSupplierOrReferenceCost() {
		GymOsPrincipal reader = new GymOsPrincipal(UUID.randomUUID(), principal.organizationId(), principal.branchId(),
				"reader", "Reader", Set.of("catalog.read"));
		UUID productId = UUID.randomUUID();
		when(products.findProduct(reader.organizationId(), productId)).thenReturn(Optional.of(costedProduct(productId)));

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
	void updateWithoutCostPermissionPreservesProtectedCommercialFields() {
		GymOsPrincipal updater = new GymOsPrincipal(UUID.randomUUID(), principal.organizationId(), principal.branchId(),
				"updater", "Updater", Set.of("catalog.update"));
		UUID productId = UUID.randomUUID();
		ProductSummaryView existing = costedProduct(productId);
		ProductSummaryView updated = costedProduct(productId);
		when(products.findProduct(updater.organizationId(), productId)).thenReturn(Optional.of(existing), Optional.of(updated));
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
		when(products.findProduct(principal.organizationId(), productId)).thenReturn(Optional.of(existing));

		assertThatThrownBy(() -> service.updateProduct(principal, productId, command(existing.version() + 1), "test"))
				.isInstanceOfSatisfying(BusinessException.class, exception ->
						assertThat(exception.code()).isEqualTo("PRODUCT_CONCURRENT_MODIFICATION"));
		verify(commands, never()).update(any());
	}

	private ProductCommand command(long version) {
		return new ProductCommand(
				"SKU-1", "Producto actualizado", null, "PHYSICAL", "GENERAL_PRODUCT", 1_000,
				"MXN", true, "ACTIVE", "750123", null, null, null, true, 0,
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
}
