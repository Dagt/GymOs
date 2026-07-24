package com.gymos.inventory.application.query;

import java.util.UUID;

public record InventoryStockLedgerView(
		UUID stockId,
		int quantityOnHand,
		int reorderPoint,
		Long averageCostCents
) {
}
