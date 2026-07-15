package com.gymos.inventory.application;

import com.gymos.audit.api.AuditService;
import com.gymos.inventory.infrastructure.InventoryRepository;
import com.gymos.inventory.infrastructure.InventoryRepository.StockDeductionResult;
import com.gymos.sales.api.events.PaymentCompletedLineItemV1;
import com.gymos.sales.api.events.PaymentCompletedV1;
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
	private final InventoryRepository inventoryRepository;
	private final AuditService auditService;

	public InventorySalesEventListener(InventoryRepository inventoryRepository, AuditService auditService) {
		this.inventoryRepository = inventoryRepository;
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
		if (physicalQuantities.isEmpty()) {
			return;
		}
		OffsetDateTime occurredAt = event.occurredAt() == null ? OffsetDateTime.now() : event.occurredAt();
		UUID correlationId = UUID.nameUUIDFromBytes(("inventory:" + event.eventKey()).getBytes(StandardCharsets.UTF_8));
		physicalQuantities.forEach((productId, quantity) -> {
			StockDeductionResult result = inventoryRepository.deductPaidSaleItem(
					event.organizationId(),
					event.branchId(),
					productId,
					event.saleId(),
					quantity,
					occurredAt,
					correlationId
			);
			if (result == StockDeductionResult.DEDUCTED) {
				auditService.recordSystem(
						event.organizationId(),
						event.branchId(),
						null,
						"INVENTORY_SALE_DEDUCTED",
						"SALE",
						event.saleId(),
						"SALES_EVENT",
						"Descuento automatico por venta pagada.",
						correlationId.toString()
				);
			}
		});
	}
}
