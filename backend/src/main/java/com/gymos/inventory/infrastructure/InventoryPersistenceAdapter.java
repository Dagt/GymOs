package com.gymos.inventory.infrastructure;

import com.gymos.catalog.api.CatalogProductOperations;
import com.gymos.inventory.application.port.InventoryCommandPort;
import com.gymos.inventory.application.port.InventoryQueryPort;
import com.gymos.inventory.application.query.InventoryMovementView;
import com.gymos.inventory.application.query.InventoryProductView;
import com.gymos.inventory.application.query.InventoryStockLedgerView;
import com.gymos.inventory.application.query.InventoryStockView;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
class InventoryPersistenceAdapter implements InventoryQueryPort, InventoryCommandPort {
	private final InventoryRepository repository;
	private final InventoryStockCommandRepository stockCommands;
	private final CatalogProductOperations catalog;

	InventoryPersistenceAdapter(InventoryRepository repository, InventoryStockCommandRepository stockCommands,
			CatalogProductOperations catalog) {
		this.repository = repository;
		this.stockCommands = stockCommands;
		this.catalog = catalog;
	}

	@Override public List<InventoryStockView> stock(UUID organizationId, UUID branchId) { return repository.stock(organizationId, branchId).stream().map(this::stock).toList(); }
	@Override public Optional<InventoryStockView> stockDetail(UUID organizationId, UUID branchId, UUID productId) { return repository.stockDetail(organizationId, branchId, productId).map(this::stock); }
	@Override public List<InventoryStockView> lowStock(UUID organizationId, UUID branchId) { return repository.lowStock(organizationId, branchId).stream().map(this::stock).toList(); }
	@Override public List<InventoryMovementView> movements(UUID organizationId, UUID branchId, UUID productId, int limit) { return repository.movements(organizationId, branchId, productId, limit).stream().map(this::movement).toList(); }
	@Override public Optional<InventoryProductView> product(UUID organizationId, UUID productId) { return catalog.product(organizationId, productId).map(value -> new InventoryProductView(value.id(), value.sku(), value.name(), value.productType(), value.inventoryTracked(), value.status())); }
	@Override public UUID ensureStock(UUID organizationId, UUID branchId, UUID productId, UUID actorUserId, OffsetDateTime now) { return stockCommands.ensureStock(organizationId, branchId, productId, actorUserId, now); }
	@Override public Optional<InventoryStockLedgerView> lock(UUID organizationId, UUID branchId, UUID stockId) { return stockCommands.lock(organizationId, branchId, stockId).map(value -> new InventoryStockLedgerView(value.stockId(), value.quantityOnHand(), value.reorderPoint(), value.averageCostCents())); }
	@Override public void update(UUID stockId, int quantityOnHand, int reorderPoint, Long averageCostCents, UUID actorUserId, OffsetDateTime now) { stockCommands.update(stockId, quantityOnHand, reorderPoint, averageCostCents, actorUserId, now); }
	@Override public UUID createMovement(UUID organizationId, UUID branchId, UUID stockId, UUID productId, String movementType, int quantityDelta, int quantityBefore, int quantityAfter, Long unitCostCents, String referenceType, UUID referenceId, String reason, OffsetDateTime occurredAt, UUID actorUserId, UUID correlationId) { return repository.createMovement(organizationId, branchId, stockId, productId, movementType, quantityDelta, quantityBefore, quantityAfter, unitCostCents, referenceType, referenceId, reason, occurredAt, actorUserId, correlationId); }
	@Override public void updateReorderPoint(UUID organizationId, UUID branchId, UUID productId, int reorderPoint, UUID actorUserId, OffsetDateTime now) {
		UUID stockId = stockCommands.ensureStock(organizationId, branchId, productId, actorUserId, now);
		InventoryStockCommandRepository.StockLedger ledger = stockCommands.lock(organizationId, branchId, stockId).orElseThrow();
		stockCommands.update(stockId, ledger.quantityOnHand(), reorderPoint, ledger.averageCostCents(), actorUserId, now);
	}

	private InventoryStockView stock(InventoryRepository.InventoryStockRecord value) { return new InventoryStockView(value.stockId(), value.productId(), value.sku(), value.productName(), value.priceCents(), value.currency(), value.quantityOnHand(), value.reorderPoint(), value.averageCostCents(), value.updatedAt()); }
	private InventoryMovementView movement(InventoryRepository.InventoryMovementRecord value) { return new InventoryMovementView(value.id(), value.productId(), value.sku(), value.productName(), value.movementType(), value.quantityDelta(), value.quantityBefore(), value.quantityAfter(), value.unitCostCents(), value.referenceType(), value.referenceId(), value.reason(), value.occurredAt()); }
}
