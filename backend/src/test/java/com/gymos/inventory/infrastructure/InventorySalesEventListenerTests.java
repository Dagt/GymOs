package com.gymos.inventory.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gymos.audit.api.AuditService;
import com.gymos.catalog.api.CatalogProductOperations;
import com.gymos.catalog.api.CatalogProductOperations.CatalogProductSnapshot;
import com.gymos.inventory.api.InventoryRefundOperations;
import com.gymos.inventory.api.InventoryRefundOperations.RefundedInventoryItem;
import com.gymos.inventory.infrastructure.InventorySaleDeductionRepository.StockDeductionResult;
import com.gymos.sales.api.events.PaymentCompletedLineItemV1;
import com.gymos.sales.api.events.PaymentCompletedV1;
import com.gymos.sales.api.events.RefundProcessedV1;
import com.gymos.sales.api.events.SaleItemsRefundedLineV1;
import com.gymos.sales.api.events.SaleItemsRefundedV1;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InventorySalesEventListenerTests {
	@Mock
	private CatalogProductOperations catalog;

	@Mock
	private InventorySaleDeductionRepository saleDeductions;

	@Mock
	private InventoryRefundOperations inventoryRefundOperations;

	@Mock
	private AuditService auditService;

	@InjectMocks
	private InventorySalesEventListener listener;

	@Test
	void paymentCompletedDeductsTrackedPhysicalProductsAggregatedByProduct() {
		UUID organizationId = UUID.randomUUID();
		UUID branchId = UUID.randomUUID();
		UUID saleId = UUID.randomUUID();
		UUID waterId = UUID.randomUUID();
		UUID towelId = UUID.randomUUID();
		OffsetDateTime occurredAt = OffsetDateTime.now();
		when(catalog.product(organizationId, waterId)).thenReturn(Optional.of(product(waterId, "Agua", true)));
		when(catalog.product(organizationId, towelId)).thenReturn(Optional.of(product(towelId, "Toalla", true)));
		when(saleDeductions.deductPaidSaleItem(eq(organizationId), eq(branchId), eq(waterId), eq("Agua"),
				eq(saleId), eq(3), eq(occurredAt), any(UUID.class))).thenReturn(StockDeductionResult.DEDUCTED);
		when(saleDeductions.deductPaidSaleItem(eq(organizationId), eq(branchId), eq(towelId), eq("Toalla"),
				eq(saleId), eq(1), eq(occurredAt), any(UUID.class))).thenReturn(StockDeductionResult.ALREADY_PROCESSED);

		listener.onPaymentCompleted(new PaymentCompletedV1(
				"payment-completed:" + saleId, organizationId, branchId, saleId, UUID.randomUUID(), "S-1",
				null, null, null,
				List.of(
						new PaymentCompletedLineItemV1(waterId, "PHYSICAL", 1),
						new PaymentCompletedLineItemV1(waterId, "PHYSICAL", 2),
						new PaymentCompletedLineItemV1(towelId, "PHYSICAL", 1),
						new PaymentCompletedLineItemV1(UUID.randomUUID(), "MEMBERSHIP_PLAN", 1)
				), occurredAt
		));

		verify(saleDeductions).deductPaidSaleItem(eq(organizationId), eq(branchId), eq(waterId), eq("Agua"),
				eq(saleId), eq(3), eq(occurredAt), any(UUID.class));
		verify(saleDeductions).deductPaidSaleItem(eq(organizationId), eq(branchId), eq(towelId), eq("Toalla"),
				eq(saleId), eq(1), eq(occurredAt), any(UUID.class));
		verify(auditService).recordSystem(eq(organizationId), eq(branchId), isNull(),
				eq("INVENTORY_SALE_DEDUCTED"), eq("SALE"), eq(saleId), eq("SALES_EVENT"), any(), any());
	}

	@Test
	void physicalProductWithoutInventoryTrackingIsIgnored() {
		UUID organizationId = UUID.randomUUID();
		UUID branchId = UUID.randomUUID();
		UUID saleId = UUID.randomUUID();
		UUID productId = UUID.randomUUID();
		when(catalog.product(organizationId, productId)).thenReturn(Optional.of(product(productId, "Servicio fisico", false)));

		listener.onPaymentCompleted(new PaymentCompletedV1(
				"payment-completed:untracked", organizationId, branchId, saleId, null, "S-2",
				null, null, null, List.of(new PaymentCompletedLineItemV1(productId, "PHYSICAL", 1)),
				OffsetDateTime.now()
		));

		verify(saleDeductions, never()).deductPaidSaleItem(any(), any(), any(), any(), any(), anyInt(), any(), any());
		verify(auditService, never()).recordSystem(any(), any(), any(), any(), any(), any(), any(), any(), any());
	}

	@Test
	void paymentCompletedWithoutPhysicalProductsDoesNothing() {
		listener.onPaymentCompleted(new PaymentCompletedV1(
				"payment-completed:none", UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null, "S-3",
				null, null, null, List.of(new PaymentCompletedLineItemV1(UUID.randomUUID(), "MEMBERSHIP_PLAN", 1)),
				OffsetDateTime.now()
		));

		verify(catalog, never()).product(any(), any());
		verify(saleDeductions, never()).deductPaidSaleItem(any(), any(), any(), any(), any(), anyInt(), any(), any());
	}

	@Test
	void refundProcessedCallsRestoreFullSale() {
		UUID organizationId = UUID.randomUUID();
		UUID branchId = UUID.randomUUID();
		UUID saleId = UUID.randomUUID();
		UUID refundId = UUID.randomUUID();
		UUID actorUserId = UUID.randomUUID();
		OffsetDateTime occurredAt = OffsetDateTime.now();
		UUID correlationId = UUID.randomUUID();

		listener.onRefundProcessed(new RefundProcessedV1(
				organizationId, branchId, saleId, refundId, actorUserId, occurredAt, correlationId
		));

		verify(inventoryRefundOperations).restoreFullSale(
				eq(organizationId), eq(branchId), eq(saleId), eq(refundId), eq(actorUserId), eq(occurredAt), eq(correlationId)
		);
	}

	@Test
	void itemizedRefundRestocksOnlySelectedTrackedPhysicalItemsAggregatedByProduct() {
		UUID organizationId = UUID.randomUUID();
		UUID branchId = UUID.randomUUID();
		UUID saleId = UUID.randomUUID();
		UUID refundId = UUID.randomUUID();
		UUID actorUserId = UUID.randomUUID();
		UUID productId = UUID.randomUUID();
		UUID untrackedProductId = UUID.randomUUID();
		OffsetDateTime occurredAt = OffsetDateTime.now();
		UUID correlationId = UUID.randomUUID();
		when(catalog.product(organizationId, productId)).thenReturn(Optional.of(product(productId, "Agua", true)));
		when(catalog.product(organizationId, untrackedProductId)).thenReturn(Optional.of(product(untrackedProductId, "Playera", false)));

		listener.onSaleItemsRefunded(new SaleItemsRefundedV1(
				organizationId, branchId, saleId, refundId, actorUserId,
				List.of(
						new SaleItemsRefundedLineV1(UUID.randomUUID(), productId, "PHYSICAL", 1, true),
						new SaleItemsRefundedLineV1(UUID.randomUUID(), productId, "PHYSICAL", 2, true),
						new SaleItemsRefundedLineV1(UUID.randomUUID(), untrackedProductId, "PHYSICAL", 1, true),
						new SaleItemsRefundedLineV1(UUID.randomUUID(), UUID.randomUUID(), "PHYSICAL", 1, false),
						new SaleItemsRefundedLineV1(UUID.randomUUID(), UUID.randomUUID(), "SERVICE", 1, true)
				), occurredAt, correlationId
		));

		ArgumentCaptor<List<RefundedInventoryItem>> itemsCaptor = ArgumentCaptor.forClass(List.class);
		verify(inventoryRefundOperations).restoreItems(
				eq(organizationId), eq(branchId), eq(saleId), eq(refundId), eq(actorUserId),
				itemsCaptor.capture(), eq(occurredAt), eq(correlationId)
		);
		assertThat(itemsCaptor.getValue()).containsExactly(new RefundedInventoryItem(productId, 3));
		verify(auditService).recordSystem(eq(organizationId), eq(branchId), eq(actorUserId),
				eq("INVENTORY_REFUND_RESTOCKED"), eq("SALE_REFUND"), eq(refundId), eq("SALES_EVENT"), any(), any());
	}

	private CatalogProductSnapshot product(UUID id, String name, boolean inventoryTracked) {
		return new CatalogProductSnapshot(id, "SKU", name, "PHYSICAL", 1_000, "MXN", inventoryTracked, "ACTIVE");
	}
}
