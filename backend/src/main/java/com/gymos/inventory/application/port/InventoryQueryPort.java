package com.gymos.inventory.application.port;

import com.gymos.common.api.PageResponse;
import com.gymos.inventory.application.query.InventoryHistorySearchCriteria;
import com.gymos.inventory.application.query.InventoryBranchSettingsView;
import com.gymos.inventory.application.query.InventoryMovementPageView;
import com.gymos.inventory.application.query.InventoryMovementSearchCriteria;
import com.gymos.inventory.application.query.InventoryMovementView;
import com.gymos.inventory.application.query.InventoryPhysicalCountView;
import com.gymos.inventory.application.query.InventoryProductView;
import com.gymos.inventory.application.query.InventoryStockPageView;
import com.gymos.inventory.application.query.InventoryStockSearchCriteria;
import com.gymos.inventory.application.query.InventoryStockView;
import com.gymos.inventory.application.query.InventorySummaryView;
import com.gymos.inventory.application.query.InventoryTransferView;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InventoryQueryPort {
	List<InventoryStockView> stock(UUID organizationId, UUID branchId);

	InventoryStockPageView searchStock(UUID organizationId, UUID branchId, InventoryStockSearchCriteria criteria);

	Optional<InventoryStockView> stockDetail(UUID organizationId, UUID branchId, UUID productId);
	List<InventoryStockView> lowStock(UUID organizationId, UUID branchId);
	List<InventoryMovementView> movements(UUID organizationId, UUID branchId, UUID productId, int limit);

	InventoryMovementPageView searchMovements(UUID organizationId, UUID branchId, InventoryMovementSearchCriteria criteria);

	Optional<InventoryProductView> product(UUID organizationId, UUID branchId, UUID productId);
	Optional<InventoryProductView> product(UUID organizationId, UUID productId);

	boolean isNegativeStockAllowed(UUID organizationId, UUID branchId);
	InventorySummaryView summary(UUID organizationId, UUID branchId);
	InventoryBranchSettingsView branchSettings(UUID organizationId, UUID branchId);

	List<InventoryPhysicalCountView> physicalCounts(UUID organizationId, UUID branchId);
	PageResponse<InventoryPhysicalCountView> searchPhysicalCounts(UUID organizationId, UUID branchId, InventoryHistorySearchCriteria criteria);
	Optional<InventoryPhysicalCountView> physicalCountDetail(UUID organizationId, UUID branchId, UUID countId);

	List<InventoryTransferView> transfers(UUID organizationId, UUID branchId);
	PageResponse<InventoryTransferView> searchTransfers(UUID organizationId, UUID branchId, InventoryHistorySearchCriteria criteria);
	Optional<InventoryTransferView> transferDetail(UUID organizationId, UUID branchId, UUID transferId);
}
