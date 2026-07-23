package com.gymos.inventory.infrastructure;

import com.gymos.catalog.api.CatalogProductOperations;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class InventoryRepository {
	private final InventoryStockQueryRepository stockQueries;
	private final InventoryMovementQueryRepository movementQueries;
	private final InventoryStockCommandRepository stockCommands;
	private final InventoryMovementCommandRepository movementCommands;
	private final InventorySaleDeductionRepository saleDeductions;
	private final CatalogProductOperations catalog;

	InventoryRepository(InventoryStockQueryRepository stockQueries,
			InventoryMovementQueryRepository movementQueries,
			InventoryStockCommandRepository stockCommands,
			InventoryMovementCommandRepository movementCommands,
			InventorySaleDeductionRepository saleDeductions,
			CatalogProductOperations catalog) {
		this.stockQueries = stockQueries;
		this.movementQueries = movementQueries;
		this.stockCommands = stockCommands;
		this.movementCommands = movementCommands;
		this.saleDeductions = saleDeductions;
		this.catalog = catalog;
	}

	public List<InventoryStockRecord> stock(UUID organizationId, UUID branchId) {
		return stockQueries.stock(organizationId, branchId);
	}

	public Optional<InventoryStockRecord> stockDetail(UUID organizationId, UUID branchId, UUID productId) {
		return stockQueries.stockDetail(organizationId, branchId, productId);
	}

	public Optional<InventoryStockRecord> stockById(UUID organizationId, UUID branchId, UUID stockId) {
		return stockQueries.stockById(organizationId, branchId, stockId);
	}

	public List<InventoryStockRecord> lowStock(UUID organizationId, UUID branchId) {
		return stockQueries.lowStock(organizationId, branchId);
	}

	public List<InventoryMovementRecord> movements(UUID organizationId, UUID branchId, UUID productId, int limit) {
		return movementQueries.movements(organizationId, branchId, productId, limit);
	}

	public Optional<UUID> stockIdentity(UUID organizationId, UUID branchId, UUID productId) {
		return stockCommands.stockIdentity(organizationId, branchId, productId);
	}

	public UUID createStock(UUID organizationId, UUID branchId, UUID productId, UUID actorUserId, OffsetDateTime now) {
		return stockCommands.createStock(organizationId, branchId, productId, actorUserId, now);
	}

	public UUID ensureStock(UUID organizationId, UUID branchId, UUID productId, UUID actorUserId, OffsetDateTime now) {
		return stockCommands.ensureStock(organizationId, branchId, productId, actorUserId, now);
	}

	public StockDeductionResult deductPaidSaleItem(UUID organizationId, UUID branchId, UUID productId,
			UUID saleId, int quantity, OffsetDateTime occurredAt, UUID correlationId) {
		CatalogProductOperations.CatalogProductSnapshot product = catalog.product(organizationId, productId).orElse(null);
		if (product == null || !product.physicalInventoryTracked()) return StockDeductionResult.IGNORED_NON_PHYSICAL;
		InventorySaleDeductionRepository.StockDeductionResult result = saleDeductions.deductPaidSaleItem(
				organizationId, branchId, productId, product.name(), saleId, quantity, occurredAt, correlationId
		);
		return result == InventorySaleDeductionRepository.StockDeductionResult.DEDUCTED
				? StockDeductionResult.DEDUCTED : StockDeductionResult.ALREADY_PROCESSED;
	}

	public void updateStock(UUID stockId, int quantityOnHand, int reorderPoint, Long averageCostCents,
			UUID actorUserId, OffsetDateTime now) {
		stockCommands.update(stockId, quantityOnHand, reorderPoint, averageCostCents, actorUserId, now);
	}

	public UUID createMovement(UUID organizationId, UUID branchId, UUID stockId, UUID productId,
			String movementType, int quantityDelta, int quantityBefore, int quantityAfter,
			Long unitCostCents, String referenceType, UUID referenceId, String reason,
			OffsetDateTime occurredAt, UUID actorUserId, UUID correlationId) {
		return movementCommands.createMovement(
				organizationId, branchId, stockId, productId, movementType, quantityDelta,
				quantityBefore, quantityAfter, unitCostCents, referenceType, referenceId,
				reason, occurredAt, actorUserId, correlationId
		);
	}

	public Optional<ProductForInventory> product(UUID organizationId, UUID productId) {
		return catalog.product(organizationId, productId).map(value -> new ProductForInventory(
				value.id(), value.sku(), value.name(), value.productType(), value.inventoryTracked(), value.status()
		));
	}

	public record ProductForInventory(
			UUID id, String sku, String name, String productType, boolean inventoryTracked, String status
	) {
	}

	public record StockLedgerRecord(UUID stockId, int quantityOnHand, int reorderPoint, Long averageCostCents) {
	}

	public enum StockDeductionResult {
		DEDUCTED,
		ALREADY_PROCESSED,
		IGNORED_NON_PHYSICAL
	}

	public record InventoryStockRecord(
			UUID stockId,
			UUID productId,
			String sku,
			String productName,
			long priceCents,
			String currency,
			int quantityOnHand,
			int reorderPoint,
			Long averageCostCents,
			OffsetDateTime updatedAt
	) {
		public boolean lowStock() {
			return reorderPoint > 0 && quantityOnHand <= reorderPoint;
		}
	}

	public record InventoryMovementRecord(
			UUID id,
			UUID productId,
			String sku,
			String productName,
			String movementType,
			int quantityDelta,
			int quantityBefore,
			int quantityAfter,
			Long unitCostCents,
			String referenceType,
			UUID referenceId,
			String reason,
			OffsetDateTime occurredAt
	) {
	}
}
