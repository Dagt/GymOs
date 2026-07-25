package com.gymos.inventory.application.query;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record InventoryPhysicalCountView(
		UUID id,
		UUID organizationId,
		UUID branchId,
		String status,
		String notes,
		OffsetDateTime createdAt,
		OffsetDateTime updatedAt,
		UUID createdBy,
		OffsetDateTime approvedAt,
		UUID approvedBy,
		List<InventoryPhysicalCountItemView> items
) {
}
