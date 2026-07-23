package com.gymos.inventory.api;

import java.time.OffsetDateTime;
import java.util.UUID;

public interface InventoryRefundOperations {
	void restoreFullSale(
			UUID organizationId,
			UUID branchId,
			UUID saleId,
			UUID refundId,
			UUID actorUserId,
			OffsetDateTime occurredAt,
			UUID correlationId
	);
}
