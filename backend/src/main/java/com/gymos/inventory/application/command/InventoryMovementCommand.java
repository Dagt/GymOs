package com.gymos.inventory.application.command;

import java.util.UUID;

public record InventoryMovementCommand(
		UUID productId,
		String movementType,
		int quantity,
		Long unitCostCents,
		String referenceType,
		UUID referenceId,
		String reason
) {
}
