package com.gymos.inventory.application.query;

import java.time.OffsetDateTime;
import java.util.UUID;

public record InventoryMovementView(
		UUID id,
		UUID productId,
		String sku,
		String productName,
		String movementType,
		int quantityDelta,
		int quantityBefore,
		int quantityAfter,
		Long unitCostCents,
		String referenceType,
		UUID referenceId,
		String reason,
		OffsetDateTime occurredAt
) {
}
