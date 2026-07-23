package com.gymos.inventory.infrastructure;

import com.gymos.audit.api.AuditService;
import com.gymos.catalog.api.CatalogProductOperations;
import com.gymos.inventory.api.InventoryRefundOperations;
import com.gymos.inventory.infrastructure.InventorySaleDeductionRepository.StockDeductionResult;
import com.gymos.sales.api.events.PaymentCompletedLineItemV1;
import com.gymos.sales.api.events.PaymentCompletedV1;
import com.gymos.sales.api.events.RefundProcessedV1;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class InventorySalesEventListener {
	private final CatalogProductOperations catalog;
	private final InventorySaleDeductionRepository saleDeductions;
	private final InventoryRefundOperations inventoryRefundOperations;
	private final AuditService auditService;

	public InventorySalesEventListener(CatalogProductOperations catalog,
			InventorySaleDeductionRepository saleDeductions,
			InventoryRefundOperations inventoryRefundOperations,
			AuditService auditService) {
		this.catalog = catalog;
		this.saleDeductions = saleDeductions;
		this.inventoryRefundOperations = inventoryRefundOperations;
		this.auditService = auditService;
	}

	@EventListener
	@Transactional
	public void onPaymentCompleted(PaymentCompletedV1 event) {
		Map<UUID, Integer> physicalQuantities = event.items().stream()
				.filter(item -> "PHYSICAL".equals(item.itemType()))
				.collect(Collectors.groupingBy(
						PaymentCompletedLineItemV1::productId,
						Collectors.summingInt(PaymentCompletedLineItemV1::quantity)
				));
		if (physicalQuantities.isEmpty()) return;
		OffsetDateTime occurredAt = event.occurredAt() == null ? OffsetDateTime.now() : event.occurredAt();
		UUID correlationId = UUID.nameUUIDFromBytes(("inventory:" + event.eventKey()).getBytes(StandardCharsets.UTF_8));
		physicalQuantities.forEach((productId, quantity) -> catalog.product(event.organizationId(), productId)
				.filter(CatalogProductOperations.CatalogProductSnapshot::physicalInventoryTracked)
				.ifPresent(product -> {
					StockDeductionResult result = saleDeductions.deductPaidSaleItem(
							event.organizationId(), event.branchId(), productId, product.name(),
							event.saleId(), quantity, occurredAt, correlationId
					);
					if (result == StockDeductionResult.DEDUCTED) {
						auditService.recordSystem(
								event.organizationId(), event.branchId(), null,
								"INVENTORY_SALE_DEDUCTED", "SALE", event.saleId(), "SALES_EVENT",
								"Descuento automatico por venta pagada.", correlationId.toString()
						);
					}
				}));
	}

	@EventListener
	@Transactional
	public void onRefundProcessed(RefundProcessedV1 event) {
		inventoryRefundOperations.restoreFullSale(
				event.organizationId(), event.branchId(), event.saleId(), event.refundId(),
				event.actorUserId(), event.occurredAt(), event.correlationId()
		);
	}
}
