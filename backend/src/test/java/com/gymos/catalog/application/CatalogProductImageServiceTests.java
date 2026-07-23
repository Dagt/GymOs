package com.gymos.catalog.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gymos.audit.api.AuditService;
import com.gymos.catalog.application.command.UploadProductImageCommand;
import com.gymos.catalog.application.port.CatalogImageStoragePort;
import com.gymos.catalog.application.port.CatalogProductCommandPort;
import com.gymos.catalog.application.port.CatalogProductQueryPort;
import com.gymos.catalog.application.port.CatalogStorageCleanupPort;
import com.gymos.catalog.application.query.ProductSummaryView;
import com.gymos.common.security.GymOsPrincipal;
import java.io.ByteArrayInputStream;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class CatalogProductImageServiceTests {
	private CatalogProductQueryPort products;
	private CatalogProductCommandPort commands;
	private CatalogImageStoragePort storage;
	private CatalogStorageCleanupPort cleanups;
	private CatalogProductImageService service;
	private GymOsPrincipal principal;

	@BeforeEach
	void setUp() {
		products = mock(CatalogProductQueryPort.class);
		commands = mock(CatalogProductCommandPort.class);
		storage = mock(CatalogImageStoragePort.class);
		cleanups = mock(CatalogStorageCleanupPort.class);
		service = new CatalogProductImageService(products, commands, storage, cleanups, mock(AuditService.class));
		principal = new GymOsPrincipal(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
				"images", "Images", Set.of("catalog.image.manage"));
	}

	@AfterEach
	void clearSynchronization() {
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.clearSynchronization();
		}
	}

	@Test
	void imageRemovalQueuesObjectAndRedactsProtectedCosts() {
		UUID productId = UUID.randomUUID();
		ProductSummaryView existing = product(productId, "catalog/old.webp");
		ProductSummaryView updated = product(productId, null);
		when(products.findProduct(principal.organizationId(), productId))
				.thenReturn(Optional.of(existing), Optional.of(updated));
		when(commands.updateImageKey(eq(principal.organizationId()), eq(productId), eq(null),
				eq(principal.userId()), any())).thenReturn(1);

		ProductSummaryView result = service.remove(principal, productId, "test");

		verify(cleanups).enqueue(eq("catalog/old.webp"), any());
		assertThat(result.referenceCostCents()).isNull();
		assertThat(result.supplierName()).isNull();
	}

	@Test
	void replacementQueuesPreviousObjectAndDeletesNewObjectOnRollback() {
		UUID productId = UUID.randomUUID();
		ProductSummaryView existing = product(productId, "catalog/old.webp");
		ProductSummaryView updated = product(productId, "catalog/new.webp");
		when(products.findProduct(principal.organizationId(), productId))
				.thenReturn(Optional.of(existing), Optional.of(updated));
		when(commands.updateImageKey(eq(principal.organizationId()), eq(productId), any(),
				eq(principal.userId()), any())).thenReturn(1);
		when(storage.store(any(), eq("image/webp"), eq(3L), any())).thenAnswer(invocation -> invocation.getArgument(0));
		TransactionSynchronizationManager.initSynchronization();

		service.upload(principal, productId,
				new UploadProductImageCommand("photo.webp", "image/webp", 3,
						new ByteArrayInputStream(new byte[]{1, 2, 3})), "test");

		ArgumentCaptor<String> newKey = ArgumentCaptor.forClass(String.class);
		verify(storage).store(newKey.capture(), eq("image/webp"), eq(3L), any());
		verify(cleanups).enqueue(eq("catalog/old.webp"), any());
		TransactionSynchronizationManager.getSynchronizations()
				.forEach(synchronization -> synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
		verify(storage).delete(newKey.getValue());
	}

	private ProductSummaryView product(UUID productId, String imageObjectKey) {
		OffsetDateTime now = OffsetDateTime.now();
		return new ProductSummaryView(
				productId, "SKU", "Producto", null, "PHYSICAL", "GENERAL_PRODUCT", 1_000, "MXN", true,
				"ACTIVE", null, null, null, null, true, 0, imageObjectKey,
				"Proveedor", 700L, null, null, null, null, null, List.of(), false, null, now, now, 0
		);
	}
}
