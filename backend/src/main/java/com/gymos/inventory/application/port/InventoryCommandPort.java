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

	void updateBranchSettings(UUID organizationId, UUID branchId, boolean allowNegativeStock, UUID actorUserId, OffsetDateTime now);

	UUID createPhysicalCount(UUID organizationId, UUID branchId, String notes, UUID actorUserId, OffsetDateTime now);
	void updatePhysicalCountItem(UUID physicalCountId, UUID productId, int systemQuantity, Integer countedQuantity, Long unitCostCents, String notes, OffsetDateTime now);
	void updatePhysicalCountStatus(UUID organizationId, UUID branchId, UUID physicalCountId, String status, UUID actorUserId, OffsetDateTime now);

	UUID createTransfer(UUID organizationId, UUID originBranchId, UUID destinationBranchId, String notes, UUID actorUserId, OffsetDateTime now);
	void addTransferItem(UUID transferId, UUID productId, int requestedQuantity, Long unitCostCents);
	void updateTransferStatus(UUID organizationId, UUID transferId, String status, UUID actorUserId, OffsetDateTime now);
	void updateTransferItemQuantities(UUID transferId, UUID productId, Integer shippedQuantity, Integer receivedQuantity);
}
