package com.gymos.catalog.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gymos.audit.api.AuditService;
import com.gymos.catalog.application.port.CatalogCategoryQueryPort;
import com.gymos.catalog.application.port.CatalogDependencyQueryPort;
import com.gymos.catalog.application.port.CatalogProductCommandPort;
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

class CatalogProductServiceTests {
	private CatalogProductQueryPort products;
	private CatalogProductCommandPort commands;
	private CatalogProductService service;
	private GymOsPrincipal principal;

	@BeforeEach
	void setUp() {
		products = mock(CatalogProductQueryPort.class);
		commands = mock(CatalogProductCommandPort.class);
		service = new CatalogProductService(products, commands, mock(CatalogDependencyQueryPort.class),
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

	private ProductSummaryView product(boolean archived, String status, String barcode) {
		return product(archived, status, barcode, UUID.randomUUID());
	}

	private ProductSummaryView product(boolean archived, String status, String barcode, UUID id) {
		OffsetDateTime now = OffsetDateTime.now();
		return new ProductSummaryView(id, "SKU-1", "Producto", null, "PHYSICAL", 1000, "MXN", true,
				status, barcode, null, null, null, true, 0, null, null, null, List.of(), archived,
				archived ? now : null, now, now);
	}
}
