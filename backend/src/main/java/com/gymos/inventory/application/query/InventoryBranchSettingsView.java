package com.gymos.inventory.application.query;

import java.util.UUID;

public record InventoryBranchSettingsView(
		UUID branchId,
		boolean allowNegativeStock
) {
}
