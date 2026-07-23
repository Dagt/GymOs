package com.gymos.inventory.application.port;

import com.gymos.inventory.application.query.InventoryMovementPageView;
import com.gymos.inventory.application.query.InventoryMovementSearchCriteria;
import com.gymos.inventory.application.query.InventoryMovementView;
import com.gymos.inventory.application.query.InventoryProductView;
import com.gymos.inventory.application.query.InventoryStockPageView;
import com.gymos.inventory.application.query.InventoryStockSearchCriteria;
import com.gymos.inventory.application.query.InventoryStockView;
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
	Optional<InventoryProductView> product(UUID organizationId, UUID productId);
}
