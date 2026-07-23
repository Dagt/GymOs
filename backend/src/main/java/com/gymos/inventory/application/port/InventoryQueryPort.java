package com.gymos.inventory.application.port;

import com.gymos.inventory.application.query.InventoryMovementView;
import com.gymos.inventory.application.query.InventoryProductView;
import com.gymos.inventory.application.query.InventoryStockView;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InventoryQueryPort {
	List<InventoryStockView> stock(UUID organizationId, UUID branchId);
	Optional<InventoryStockView> stockDetail(UUID organizationId, UUID branchId, UUID productId);
	List<InventoryStockView> lowStock(UUID organizationId, UUID branchId);
	List<InventoryMovementView> movements(UUID organizationId, UUID branchId, UUID productId, int limit);
	Optional<InventoryProductView> product(UUID organizationId, UUID productId);
}
