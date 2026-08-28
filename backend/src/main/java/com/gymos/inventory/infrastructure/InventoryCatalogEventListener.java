package com.gymos.inventory.infrastructure;

import com.gymos.catalog.api.events.ProductVariantInventoryDefaultsRequestedV1;
import com.gymos.inventory.application.InventoryService;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class InventoryCatalogEventListener {
	private final InventoryService inventoryService;

	InventoryCatalogEventListener(InventoryService inventoryService) {
		this.inventoryService = inventoryService;
	}

	@EventListener
	@Transactional
	void applyVariantDefaults(ProductVariantInventoryDefaultsRequestedV1 event) {
		for (var productId : event.productIds()) {
			inventoryService.updateReorderPoint(
					event.principal(), productId, event.reorderPoint(), event.correlationId());
		}
	}
}
