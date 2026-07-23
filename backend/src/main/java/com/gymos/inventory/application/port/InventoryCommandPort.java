package com.gymos.inventory.application.port;

import com.gymos.inventory.application.query.InventoryStockLedgerView;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface InventoryCommandPort {
	UUID ensureStock(UUID organizationId, UUID branchId, UUID productId, UUID actorUserId, OffsetDateTime now);
	Optional<InventoryStockLedgerView> lock(UUID organizationId, UUID branchId, UUID stockId);
	void update(UUID stockId, int quantityOnHand, int reorderPoint, Long averageCostCents, UUID actorUserId, OffsetDateTime now);
	UUID createMovement(UUID organizationId, UUID branchId, UUID stockId, UUID productId, String movementType,
			int quantityDelta, int quantityBefore, int quantityAfter, Long unitCostCents, String referenceType,
			UUID referenceId, String reason, OffsetDateTime occurredAt, UUID actorUserId, UUID correlationId);
	void updateReorderPoint(UUID organizationId, UUID branchId, UUID productId, int reorderPoint, UUID actorUserId, OffsetDateTime now);
}
