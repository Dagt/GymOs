package com.gymos.inventory.infrastructure;

import com.gymos.audit.api.AuditService;
import com.gymos.catalog.api.CatalogProductOperations;
import com.gymos.inventory.api.InventoryRefundOperations;
import com.gymos.inventory.api.InventoryRefundOperations.RefundedInventoryItem;
import com.gymos.inventory.infrastructure.InventorySaleDeductionRepository.StockDeductionResult;
import com.gymos.sales.api.events.PaymentCompletedLineItemV1;
import com.gymos.sales.api.events.PaymentCompletedV1;
import com.gymos.sales.api.events.RefundProcessedV1;
import com.gymos.sales.api.events.PointSaleStockRequested;
import com.gymos.sales.api.events.PointSaleStockReleased;
import com.gymos.sales.api.events.SaleItemsRefundedLineV1;
import com.gymos.sales.api.events.SaleItemsRefundedV1;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
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
		physicalQuantities.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> catalog.product(event.organizationId(), entry.getKey())
				.filter(product -> product.physicalInventoryTracked()
						|| saleDeductions.hasReservation(event.organizationId(), event.branchId(), entry.getKey(), event.saleId()))
				.ifPresent(product -> {
					StockDeductionResult result = saleDeductions.deductPaidSaleItem(
							event.organizationId(), event.branchId(), entry.getKey(), product.name(),
							event.saleId(), entry.getValue(), occurredAt, correlationId
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
	public void onPointStockRequested(PointSaleStockRequested event) {
		Map<UUID, Integer> quantities = event.items().stream().filter(item -> "PHYSICAL".equals(item.itemType()))
				.collect(Collectors.toMap(PaymentCompletedLineItemV1::productId, PaymentCompletedLineItemV1::quantity, Math::addExact));
		OffsetDateTime now = OffsetDateTime.now();
		quantities.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry ->
				catalog.product(event.organizationId(), entry.getKey())
						.filter(CatalogProductOperations.CatalogProductSnapshot::physicalInventoryTracked)
						.ifPresent(product -> saleDeductions.reserve(event.organizationId(), event.branchId(), product.id(),
								event.saleId(), entry.getValue(), now)));
	}

	@EventListener
	@Transactional
	public void onPointStockReleased(PointSaleStockReleased event) {
		saleDeductions.releaseSale(event.organizationId(), event.branchId(), event.saleId());
	}

	@EventListener
	@Transactional
	public void onRefundProcessed(RefundProcessedV1 event) {
		inventoryRefundOperations.restoreFullSale(
				event.organizationId(), event.branchId(), event.saleId(), event.refundId(),
				event.actorUserId(), event.occurredAt(), event.correlationId()
		);
	}

	@EventListener
	@Transactional
	public void onSaleItemsRefunded(SaleItemsRefundedV1 event) {
		Map<UUID, Integer> requestedQuantities = event.items().stream()
				.filter(SaleItemsRefundedLineV1::restock)
				.filter(item -> "PHYSICAL".equals(item.itemType()) && item.productId() != null)
				.collect(Collectors.groupingBy(
						SaleItemsRefundedLineV1::productId,
						Collectors.summingInt(SaleItemsRefundedLineV1::quantity)
				));
		List<RefundedInventoryItem> items = requestedQuantities.entrySet().stream().sorted(Map.Entry.comparingByKey())
				.filter(entry -> catalog.product(event.organizationId(), entry.getKey())
						.filter(CatalogProductOperations.CatalogProductSnapshot::physicalInventoryTracked)
						.isPresent())
				.map(entry -> new RefundedInventoryItem(entry.getKey(), entry.getValue()))
				.toList();
		if (items.isEmpty()) return;
		inventoryRefundOperations.restoreItems(
				event.organizationId(), event.branchId(), event.saleId(), event.refundId(),
				event.actorUserId(), items, event.occurredAt(), event.correlationId()
		);
		auditService.recordSystem(
				event.organizationId(), event.branchId(), event.actorUserId(),
				"INVENTORY_REFUND_RESTOCKED", "SALE_REFUND", event.refundId(), "SALES_EVENT",
				"Reposicion selectiva por devolucion de productos.", event.correlationId().toString()
		);
	}
}
