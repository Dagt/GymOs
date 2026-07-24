package com.gymos.inventory.api;

import java.time.OffsetDateTime;
import java.util.List;
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

	void restoreItems(
			UUID organizationId,
			UUID branchId,
			UUID saleId,
			UUID refundId,
			UUID actorUserId,
			List<RefundedInventoryItem> items,
			OffsetDateTime occurredAt,
			UUID correlationId
	);

	record RefundedInventoryItem(UUID productId, int quantity) {
		public RefundedInventoryItem {
			if (productId == null) throw new IllegalArgumentException("productId es obligatorio");
			if (quantity <= 0) throw new IllegalArgumentException("quantity debe ser mayor a cero");
		}
	}
}
