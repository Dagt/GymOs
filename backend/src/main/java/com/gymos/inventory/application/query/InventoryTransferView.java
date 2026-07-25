package com.gymos.inventory.application.query;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record InventoryTransferView(
		UUID id,
		UUID organizationId,
		UUID originBranchId,
		String originBranchName,
		UUID destinationBranchId,
		String destinationBranchName,
		String status,
		String notes,
		OffsetDateTime createdAt,
		OffsetDateTime updatedAt,
		UUID createdBy,
		OffsetDateTime shippedAt,
		UUID shippedBy,
		OffsetDateTime receivedAt,
		UUID receivedBy,
		List<InventoryTransferItemView> items
) {
}
