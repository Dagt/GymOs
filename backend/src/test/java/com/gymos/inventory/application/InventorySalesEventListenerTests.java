package com.gymos.inventory.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gymos.audit.api.AuditService;
import com.gymos.inventory.infrastructure.InventoryRepository;
import com.gymos.inventory.infrastructure.InventoryRepository.StockDeductionResult;
import com.gymos.sales.api.events.PaymentCompletedLineItemV1;
import com.gymos.sales.api.events.PaymentCompletedV1;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InventorySalesEventListenerTests {
	@Mock
	private InventoryRepository inventoryRepository;

	@Mock
	private AuditService auditService;

	@InjectMocks
	private InventorySalesEventListener listener;

	@Test
	void paymentCompletedDeductsPhysicalProductsAggregatedByProduct() {
		UUID organizationId = UUID.randomUUID();
		UUID branchId = UUID.randomUUID();
		UUID saleId = UUID.randomUUID();
		UUID waterId = UUID.randomUUID();
		UUID towelId = UUID.randomUUID();
		OffsetDateTime occurredAt = OffsetDateTime.now();
		when(inventoryRepository.deductPaidSaleItem(eq(organizationId), eq(branchId), eq(waterId), eq(saleId), eq(3), eq(occurredAt), any(UUID.class)))
				.thenReturn(StockDeductionResult.DEDUCTED);
		when(inventoryRepository.deductPaidSaleItem(eq(organizationId), eq(branchId), eq(towelId), eq(saleId), eq(1), eq(occurredAt), any(UUID.class)))
				.thenReturn(StockDeductionResult.ALREADY_PROCESSED);

		listener.onPaymentCompleted(new PaymentCompletedV1(
				"payment-completed:" + saleId,
				organizationId,
				branchId,
				saleId,
				UUID.randomUUID(),
				"S-1",
				null,
				null,
				null,
				List.of(
						new PaymentCompletedLineItemV1(waterId, "PHYSICAL", 1),
						new PaymentCompletedLineItemV1(waterId, "PHYSICAL", 2),
						new PaymentCompletedLineItemV1(towelId, "PHYSICAL", 1),
						new PaymentCompletedLineItemV1(UUID.randomUUID(), "MEMBERSHIP_PLAN", 1)
				),
				occurredAt
		));

		verify(inventoryRepository).deductPaidSaleItem(eq(organizationId), eq(branchId), eq(waterId), eq(saleId), eq(3), eq(occurredAt), any(UUID.class));
		verify(inventoryRepository).deductPaidSaleItem(eq(organizationId), eq(branchId), eq(towelId), eq(saleId), eq(1), eq(occurredAt), any(UUID.class));
		verify(auditService).recordSystem(eq(organizationId), eq(branchId), isNull(), eq("INVENTORY_SALE_DEDUCTED"), eq("SALE"), eq(saleId), eq("SALES_EVENT"), any(), any());
	}

	@Test
	void paymentCompletedWithoutPhysicalProductsDoesNothing() {
		listener.onPaymentCompleted(new PaymentCompletedV1(
				"payment-completed:none",
				UUID.randomUUID(),
				UUID.randomUUID(),
				UUID.randomUUID(),
				null,
				"S-2",
				null,
				null,
				null,
				List.of(new PaymentCompletedLineItemV1(UUID.randomUUID(), "MEMBERSHIP_PLAN", 1)),
				OffsetDateTime.now()
		));

		verify(inventoryRepository, never()).deductPaidSaleItem(any(), any(), any(), any(), anyInt(), any(), any());
		verify(auditService, never()).recordSystem(any(), any(), any(), any(), any(), any(), any(), any(), any());
	}
}
