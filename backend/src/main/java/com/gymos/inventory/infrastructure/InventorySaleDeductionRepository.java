package com.gymos.inventory.infrastructure;

import com.gymos.common.api.BusinessException;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class InventorySaleDeductionRepository {
	private final JdbcClient jdbcClient;
	private final InventoryStockCommandRepository stockCommands;

	InventorySaleDeductionRepository(JdbcClient jdbcClient, InventoryStockCommandRepository stockCommands) {
		this.jdbcClient = jdbcClient;
		this.stockCommands = stockCommands;
	}

	StockDeductionResult deductPaidSaleItem(UUID organizationId, UUID branchId, UUID productId,
			String productName, UUID saleId, int quantity, OffsetDateTime occurredAt, UUID correlationId) {
		if (quantity <= 0) {
			throw new BusinessException("INVENTORY_QUANTITY_INVALID", "La cantidad a descontar debe ser mayor a cero.");
		}
		UUID stockId = stockCommands.ensureStock(organizationId, branchId, productId, null, occurredAt);
		InventoryStockCommandRepository.StockLedger stock = stockCommands.lock(organizationId, branchId, stockId)
				.orElseThrow(() -> new BusinessException("INVENTORY_STOCK_NOT_FOUND", "No fue posible bloquear existencias."));
		if (movementExists(organizationId, branchId, productId, saleId)) {
			return StockDeductionResult.ALREADY_PROCESSED;
		}
		int quantityAfter = stock.quantityOnHand() - quantity;
		if (quantityAfter < 0) {
			throw new BusinessException("INVENTORY_STOCK_INSUFFICIENT", "La venta supera las existencias disponibles de " + productName + ".");
		}
		stockCommands.update(stockId, quantityAfter, stock.reorderPoint(), stock.averageCostCents(), null, occurredAt);
		jdbcClient.sql("""
				INSERT INTO inventory_movement (
				 id, organization_id, branch_id, stock_id, product_id, movement_type,
				 quantity_delta, quantity_before, quantity_after, unit_cost_cents,
				 reference_type, reference_id, reason, occurred_at, created_at, created_by, correlation_id)
				VALUES (
				 :id, :organizationId, :branchId, :stockId, :productId, 'SALE',
				 :quantityDelta, :quantityBefore, :quantityAfter, null,
				 'SALE', :saleId, 'Salida automatica por venta pagada.', :occurredAt, :occurredAt, null, :correlationId)
				""")
				.param("id", UUID.randomUUID()).param("organizationId", organizationId).param("branchId", branchId)
				.param("stockId", stockId).param("productId", productId).param("quantityDelta", -quantity)
				.param("quantityBefore", stock.quantityOnHand()).param("quantityAfter", quantityAfter)
				.param("saleId", saleId).param("occurredAt", occurredAt).param("correlationId", correlationId)
				.update();
		return StockDeductionResult.DEDUCTED;
	}

	private boolean movementExists(UUID organizationId, UUID branchId, UUID productId, UUID saleId) {
		return jdbcClient.sql("""
				SELECT EXISTS (
				 SELECT 1 FROM inventory_movement
				 WHERE organization_id=:organizationId AND branch_id=:branchId AND product_id=:productId
				   AND reference_type='SALE' AND reference_id=:saleId)
				""")
				.param("organizationId", organizationId).param("branchId", branchId)
				.param("productId", productId).param("saleId", saleId)
				.query(Boolean.class).single();
	}

	enum StockDeductionResult {
		DEDUCTED,
		ALREADY_PROCESSED
	}
}
