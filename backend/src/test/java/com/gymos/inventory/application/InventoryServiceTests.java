package com.gymos.inventory.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gymos.audit.api.AuditService;
import com.gymos.common.api.BusinessException;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.inventory.application.command.InventoryMovementCommand;
import com.gymos.inventory.application.port.InventoryCommandPort;
import com.gymos.inventory.application.port.InventoryQueryPort;
import com.gymos.inventory.application.query.InventoryBranchSettingsView;
import com.gymos.inventory.application.query.InventoryPhysicalCountItemView;
import com.gymos.inventory.application.query.InventoryPhysicalCountView;
import com.gymos.inventory.application.query.InventoryProductView;
import com.gymos.inventory.application.query.InventoryStockLedgerView;
import com.gymos.inventory.application.query.InventoryStockView;
import com.gymos.inventory.application.query.InventorySummaryView;
import com.gymos.inventory.application.query.InventoryTransferItemView;
import com.gymos.inventory.application.query.InventoryTransferView;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InventoryServiceTests {
	private InventoryQueryPort queries;
	private InventoryCommandPort commands;
	private InventoryService service;
	private GymOsPrincipal principal;
	private UUID productId;

	@BeforeEach
	void setUp() {
		queries = mock(InventoryQueryPort.class);
		commands = mock(InventoryCommandPort.class);
		service = new InventoryService(queries, commands, mock(AuditService.class));
		productId = UUID.randomUUID();
		principal = new GymOsPrincipal(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
				"inventory", "Inventory", Set.of("inventory.manage"));
		when(queries.product(principal.organizationId(), principal.branchId(), productId))
				.thenReturn(Optional.of(new InventoryProductView(productId, "SKU", "Producto", "PHYSICAL", true, "ACTIVE")));
	}

	@Test
	void manualSaleMovementIsRejected() {
		InventoryMovementCommand command = new InventoryMovementCommand(productId, "SALE", 1, null, null, null, "manual");
		assertThatThrownBy(() -> service.createMovement(principal, command, "test"))
				.isInstanceOf(BusinessException.class)
				.hasMessageContaining("automaticamente");
	}

	@Test
	void movementLocksStockAndPreservesIndependentReorderPoint() {
		UUID stockId = UUID.randomUUID();
		when(commands.ensureStock(eq(principal.organizationId()), eq(principal.branchId()), eq(productId), eq(principal.userId()), any()))
				.thenReturn(stockId);
		when(commands.lock(principal.organizationId(), principal.branchId(), stockId))
				.thenReturn(Optional.of(new InventoryStockLedgerView(stockId, 5, 3, 100L)));
		InventoryStockView expected = stock(stockId, 7, 3);
		when(queries.stockDetail(principal.organizationId(), principal.branchId(), productId)).thenReturn(Optional.of(expected));
		InventoryMovementCommand request = new InventoryMovementCommand(productId, "PURCHASE", 2, 120L, null, null, "compra");
		assertThat(service.createMovement(principal, request, "test").quantityOnHand()).isEqualTo(7);
		verify(commands).update(eq(stockId), eq(7), eq(3), any(), eq(principal.userId()), any());
	}

	@Test
	void negativeStockIsRejectedWhenNotAllowed() {
		UUID stockId = UUID.randomUUID();
		when(commands.ensureStock(eq(principal.organizationId()), eq(principal.branchId()), eq(productId), eq(principal.userId()), any()))
				.thenReturn(stockId);
		when(commands.lock(principal.organizationId(), principal.branchId(), stockId))
				.thenReturn(Optional.of(new InventoryStockLedgerView(stockId, 2, 0, 100L)));
		when(queries.isNegativeStockAllowed(principal.organizationId(), principal.branchId())).thenReturn(false);

		InventoryMovementCommand request = new InventoryMovementCommand(productId, "WASTE", 5, null, null, null, "merma mayor a stock");
		assertThatThrownBy(() -> service.createMovement(principal, request, "test"))
				.isInstanceOf(BusinessException.class)
				.hasMessageContaining("existencias negativas");
	}

	@Test
	void negativeStockIsAllowedWhenBranchSettingEnabled() {
		UUID stockId = UUID.randomUUID();
		when(commands.ensureStock(eq(principal.organizationId()), eq(principal.branchId()), eq(productId), eq(principal.userId()), any()))
				.thenReturn(stockId);
		when(commands.lock(principal.organizationId(), principal.branchId(), stockId))
				.thenReturn(Optional.of(new InventoryStockLedgerView(stockId, 2, 0, 100L)));
		when(queries.isNegativeStockAllowed(principal.organizationId(), principal.branchId())).thenReturn(true);

		InventoryStockView expected = stock(stockId, -3, 0);
		when(queries.stockDetail(principal.organizationId(), principal.branchId(), productId)).thenReturn(Optional.of(expected));

		InventoryMovementCommand request = new InventoryMovementCommand(productId, "INTERNAL_CONSUMPTION", 5, null, null, null, "uso interno");
		assertThat(service.createMovement(principal, request, "test").quantityOnHand()).isEqualTo(-3);
		verify(commands).update(eq(stockId), eq(-3), eq(0), any(), eq(principal.userId()), any());
	}

	@Test
	void reorderPointUsesDedicatedCommand() {
		InventoryStockView expected = stock(UUID.randomUUID(), 5, 10);
		when(queries.stockDetail(principal.organizationId(), principal.branchId(), productId)).thenReturn(Optional.of(expected));
		assertThat(service.updateReorderPoint(principal, productId, 10, "test").reorderPoint()).isEqualTo(10);
		verify(commands).updateReorderPoint(eq(principal.organizationId()), eq(principal.branchId()), eq(productId), eq(10), eq(principal.userId()), any());
	}

	@Test
	void summaryReturnsCalculatedView() {
		InventorySummaryView summaryView = new InventorySummaryView(50000L, 10L, 2L, 1L, false);
		when(queries.summary(principal.organizationId(), principal.branchId())).thenReturn(summaryView);

		InventorySummaryView result = service.summary(principal);
		assertThat(result.totalValuationCents()).isEqualTo(50000L);
		assertThat(result.totalTrackedProducts()).isEqualTo(10L);
		assertThat(result.lowStockCount()).isEqualTo(2L);
	}

	@Test
	void updateBranchSettingsDelegatesToCommandPort() {
		InventoryBranchSettingsView settingsView = new InventoryBranchSettingsView(principal.branchId(), true);
		when(queries.branchSettings(principal.organizationId(), principal.branchId())).thenReturn(settingsView);

		InventoryBranchSettingsView result = service.updateBranchSettings(principal, true, "test");
		assertThat(result.allowNegativeStock()).isTrue();
		verify(commands).updateBranchSettings(eq(principal.organizationId()), eq(principal.branchId()), eq(true), eq(principal.userId()), any());
	}

	@Test
	void approvePhysicalCountCreatesAdjustmentMovements() {
		UUID countId = UUID.randomUUID();
		InventoryPhysicalCountItemView item = new InventoryPhysicalCountItemView(
				UUID.randomUUID(), productId, "Producto", "SKU", 10, 8, -2, 100L, "conteo faltante"
		);
		InventoryPhysicalCountView countView = new InventoryPhysicalCountView(
				countId, principal.organizationId(), principal.branchId(), "IN_PROGRESS", "Ajuste de fin de mes",
				OffsetDateTime.now(), OffsetDateTime.now(), principal.userId(), null, null, List.of(item)
		);
		when(queries.physicalCountDetail(principal.organizationId(), principal.branchId(), countId)).thenReturn(Optional.of(countView));

		UUID stockId = UUID.randomUUID();
		when(commands.ensureStock(eq(principal.organizationId()), eq(principal.branchId()), eq(productId), eq(principal.userId()), any())).thenReturn(stockId);
		when(commands.lock(principal.organizationId(), principal.branchId(), stockId)).thenReturn(Optional.of(new InventoryStockLedgerView(stockId, 10, 0, 100L)));
		when(queries.stockDetail(principal.organizationId(), principal.branchId(), productId)).thenReturn(Optional.of(stock(stockId, 8, 0)));

		service.approvePhysicalCount(principal, countId, "test");

		verify(commands).updatePhysicalCountStatus(eq(principal.organizationId()), eq(principal.branchId()), eq(countId), eq("APPROVED"), eq(principal.userId()), any());
	}

	@Test
	void createTransferRejectsSameBranch() {
		assertThatThrownBy(() -> service.createTransfer(principal, principal.branchId(), "misma sucursal", List.of(), "test"))
				.isInstanceOf(BusinessException.class)
				.hasMessageContaining("sucursal destino debe ser distinta");
	}

	private InventoryStockView stock(UUID stockId, int quantity, int reorderPoint) {
		return new InventoryStockView(stockId, productId, "SKU", "Producto", 1000, "MXN", quantity,
				reorderPoint, 100L, OffsetDateTime.now());
	}
}
