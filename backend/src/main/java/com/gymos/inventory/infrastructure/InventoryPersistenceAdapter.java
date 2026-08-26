package com.gymos.inventory.infrastructure;

import com.gymos.catalog.api.CatalogProductOperations;
import com.gymos.common.api.PageResponse;
import com.gymos.inventory.application.query.InventoryHistorySearchCriteria;
import com.gymos.inventory.application.port.InventoryCommandPort;
import com.gymos.inventory.application.port.InventoryQueryPort;
import com.gymos.inventory.application.query.InventoryBranchSettingsView;
import com.gymos.inventory.application.query.InventoryMovementPageView;
import com.gymos.inventory.application.query.InventoryMovementSearchCriteria;
import com.gymos.inventory.application.query.InventoryMovementView;
import com.gymos.inventory.application.query.InventoryPhysicalCountView;
import com.gymos.inventory.application.query.InventoryProductView;
import com.gymos.inventory.application.query.InventoryStockLedgerView;
import com.gymos.inventory.application.query.InventoryStockPageView;
import com.gymos.inventory.application.query.InventoryStockSearchCriteria;
import com.gymos.inventory.application.query.InventoryStockView;
import com.gymos.inventory.application.query.InventorySummaryView;
import com.gymos.inventory.application.query.InventoryTransferView;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
class InventoryPersistenceAdapter implements InventoryQueryPort, InventoryCommandPort {
	private final InventoryRepository repository;
	private final InventoryStockQueryRepository stockQueries;
	private final InventoryMovementQueryRepository movementQueries;
	private final InventoryStockCommandRepository stockCommands;
	private final InventoryPhysicalCountRepository physicalCountRepository;
	private final InventoryTransferRepository transferRepository;
	private final CatalogProductOperations catalog;

	InventoryPersistenceAdapter(InventoryRepository repository, InventoryStockQueryRepository stockQueries,
			InventoryMovementQueryRepository movementQueries, InventoryStockCommandRepository stockCommands,
			InventoryPhysicalCountRepository physicalCountRepository, InventoryTransferRepository transferRepository,
			CatalogProductOperations catalog) {
		this.repository = repository;
		this.stockQueries = stockQueries;
		this.movementQueries = movementQueries;
		this.stockCommands = stockCommands;
		this.physicalCountRepository = physicalCountRepository;
		this.transferRepository = transferRepository;
		this.catalog = catalog;
	}

	@Override public List<InventoryStockView> stock(UUID organizationId, UUID branchId) { return repository.stock(organizationId, branchId).stream().map(this::stock).toList(); }
	@Override public InventoryStockPageView searchStock(UUID organizationId, UUID branchId, InventoryStockSearchCriteria criteria) {
		var page = stockQueries.search(organizationId, branchId, criteria);
		return InventoryStockPageView.of(page.items().stream().map(this::stock).toList(), criteria.page(), criteria.size(), page.total());
	}
	@Override public Optional<InventoryStockView> stockDetail(UUID organizationId, UUID branchId, UUID productId) { return repository.stockDetail(organizationId, branchId, productId).map(this::stock); }
	@Override public List<InventoryStockView> lowStock(UUID organizationId, UUID branchId) { return repository.lowStock(organizationId, branchId).stream().map(this::stock).toList(); }
	@Override public List<InventoryMovementView> movements(UUID organizationId, UUID branchId, UUID productId, int limit) { return repository.movements(organizationId, branchId, productId, limit).stream().map(this::movement).toList(); }
	@Override public InventoryMovementPageView searchMovements(UUID organizationId, UUID branchId, InventoryMovementSearchCriteria criteria) {
		var page = movementQueries.search(organizationId, branchId, criteria);
		return InventoryMovementPageView.of(page.items().stream().map(this::movement).toList(), criteria.page(), criteria.size(), page.total());
	}
	@Override public Optional<InventoryProductView> product(UUID organizationId, UUID branchId, UUID productId) { return catalog.product(organizationId, branchId, productId).map(value -> new InventoryProductView(value.id(), value.sku(), value.name(), value.productType(), value.inventoryTracked(), value.status())); }
	@Override public Optional<InventoryProductView> product(UUID organizationId, UUID productId) { return catalog.product(organizationId, productId).map(value -> new InventoryProductView(value.id(), value.sku(), value.name(), value.productType(), value.inventoryTracked(), value.status())); }
	@Override public UUID ensureStock(UUID organizationId, UUID branchId, UUID productId, UUID actorUserId, OffsetDateTime now) { return stockCommands.ensureStock(organizationId, branchId, productId, actorUserId, now); }
	@Override public void attachBranch(UUID organizationId, UUID branchId, UUID productId, UUID actorUserId, OffsetDateTime now) { catalog.attachBranch(organizationId, branchId, productId, actorUserId, now); }
	@Override public Optional<InventoryStockLedgerView> lock(UUID organizationId, UUID branchId, UUID stockId) { return stockCommands.lock(organizationId, branchId, stockId).map(value -> new InventoryStockLedgerView(value.stockId(), value.quantityOnHand(), value.reorderPoint(), value.averageCostCents())); }
	@Override public void update(UUID stockId, int quantityOnHand, int reorderPoint, Long averageCostCents, UUID actorUserId, OffsetDateTime now) { stockCommands.update(stockId, quantityOnHand, reorderPoint, averageCostCents, actorUserId, now); }
	@Override public UUID createMovement(UUID organizationId, UUID branchId, UUID stockId, UUID productId, String movementType, int quantityDelta, int quantityBefore, int quantityAfter, Long unitCostCents, String referenceType, UUID referenceId, String reason, OffsetDateTime occurredAt, UUID actorUserId, UUID correlationId) { return repository.createMovement(organizationId, branchId, stockId, productId, movementType, quantityDelta, quantityBefore, quantityAfter, unitCostCents, referenceType, referenceId, reason, occurredAt, actorUserId, correlationId); }
	@Override public void updateReorderPoint(UUID organizationId, UUID branchId, UUID productId, int reorderPoint, UUID actorUserId, OffsetDateTime now) {
		UUID stockId = stockCommands.ensureStock(organizationId, branchId, productId, actorUserId, now);
		InventoryStockCommandRepository.StockLedger ledger = stockCommands.lock(organizationId, branchId, stockId).orElseThrow();
		stockCommands.update(stockId, ledger.quantityOnHand(), reorderPoint, ledger.averageCostCents(), actorUserId, now);
	}

	@Override public boolean isNegativeStockAllowed(UUID organizationId, UUID branchId) { return stockQueries.isNegativeStockAllowed(organizationId, branchId); }
	@Override public InventorySummaryView summary(UUID organizationId, UUID branchId) { return stockQueries.summary(organizationId, branchId); }
	@Override public InventoryBranchSettingsView branchSettings(UUID organizationId, UUID branchId) { return stockQueries.branchSettings(organizationId, branchId); }
	@Override public void updateBranchSettings(UUID organizationId, UUID branchId, boolean allowNegativeStock, UUID actorUserId, OffsetDateTime now) { stockCommands.updateBranchSettings(organizationId, branchId, allowNegativeStock, actorUserId, now); }

	@Override public List<InventoryPhysicalCountView> physicalCounts(UUID organizationId, UUID branchId) { return physicalCountRepository.list(organizationId, branchId); }
	@Override public PageResponse<InventoryPhysicalCountView> searchPhysicalCounts(UUID organizationId, UUID branchId, InventoryHistorySearchCriteria criteria) { return physicalCountRepository.search(organizationId, branchId, criteria); }
	@Override public Optional<InventoryPhysicalCountView> physicalCountDetail(UUID organizationId, UUID branchId, UUID countId) { return physicalCountRepository.findById(organizationId, branchId, countId); }
	@Override public UUID createPhysicalCount(UUID organizationId, UUID branchId, String notes, UUID actorUserId, OffsetDateTime now) { return physicalCountRepository.create(organizationId, branchId, notes, actorUserId, now); }
	@Override public void updatePhysicalCountItem(UUID physicalCountId, UUID productId, int systemQuantity, Integer countedQuantity, Long unitCostCents, String notes, OffsetDateTime now) { physicalCountRepository.updateItem(physicalCountId, productId, systemQuantity, countedQuantity, unitCostCents, notes, now); }
	@Override public void updatePhysicalCountStatus(UUID organizationId, UUID branchId, UUID physicalCountId, String status, UUID actorUserId, OffsetDateTime now) { physicalCountRepository.updateStatus(organizationId, branchId, physicalCountId, status, actorUserId, now); }

	@Override public List<InventoryTransferView> transfers(UUID organizationId, UUID branchId) { return transferRepository.list(organizationId, branchId); }
	@Override public PageResponse<InventoryTransferView> searchTransfers(UUID organizationId, UUID branchId, InventoryHistorySearchCriteria criteria) { return transferRepository.search(organizationId, branchId, criteria); }
	@Override public Optional<InventoryTransferView> transferDetail(UUID organizationId, UUID branchId, UUID transferId) { return transferRepository.findById(organizationId, branchId, transferId); }
	@Override public UUID createTransfer(UUID organizationId, UUID originBranchId, UUID destinationBranchId, String notes, UUID actorUserId, OffsetDateTime now) { return transferRepository.create(organizationId, originBranchId, destinationBranchId, notes, actorUserId, now); }
	@Override public void addTransferItem(UUID transferId, UUID productId, int requestedQuantity, Long unitCostCents) { transferRepository.addItem(transferId, productId, requestedQuantity, unitCostCents); }
	@Override public void updateTransferStatus(UUID organizationId, UUID transferId, String status, UUID actorUserId, OffsetDateTime now) { transferRepository.updateStatus(organizationId, transferId, status, actorUserId, now); }
	@Override public void updateTransferItemQuantities(UUID transferId, UUID productId, Integer shippedQuantity, Integer receivedQuantity) { transferRepository.updateTransferItemQuantities(transferId, productId, shippedQuantity, receivedQuantity); }

	private InventoryStockView stock(InventoryRepository.InventoryStockRecord value) { return new InventoryStockView(value.stockId(), value.productId(), value.sku(), value.productName(), value.priceCents(), value.currency(), value.quantityOnHand(), value.reorderPoint(), value.averageCostCents(), value.updatedAt()); }
	private InventoryMovementView movement(InventoryRepository.InventoryMovementRecord value) { return new InventoryMovementView(value.id(), value.productId(), value.sku(), value.productName(), value.movementType(), value.quantityDelta(), value.quantityBefore(), value.quantityAfter(), value.unitCostCents(), value.referenceType(), value.referenceId(), value.reason(), value.occurredAt()); }
}
