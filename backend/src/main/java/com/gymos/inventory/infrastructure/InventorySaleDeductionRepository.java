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
		releaseStock(stockId, saleId);
		boolean allowNegative = isNegativeStockAllowed(organizationId, branchId);
		long after = (long) stock.quantityOnHand() - quantity;
		if (after < Integer.MIN_VALUE) {
			throw new BusinessException("INVENTORY_QUANTITY_INVALID", "El saldo de inventario está fuera de rango.");
		}
		int quantityAfter = (int) after;
		if (quantityAfter < 0 && !allowNegative) {
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

	void reserve(UUID organizationId, UUID branchId, UUID productId, UUID saleId, int quantity, OffsetDateTime now) {
		UUID stockId = stockCommands.ensureStock(organizationId, branchId, productId, null, now);
		var stock = stockCommands.lock(organizationId, branchId, stockId).orElseThrow();
		if (hasReservation(organizationId, branchId, productId, saleId)) return;
		if ((long) stock.quantityOnHand() - stockCommands.reservedQuantity(stockId) < quantity) {
			throw new BusinessException("INVENTORY_STOCK_INSUFFICIENT", "No hay unidades disponibles para reservar antes del cobro con Point.");
		}
		jdbcClient.sql("""
				INSERT INTO inventory_sale_reservation(stock_id, sale_id, quantity, created_at)
				VALUES (:stock, :sale, :quantity, :now)
				""").param("stock", stockId).param("sale", saleId).param("quantity", quantity).param("now", now).update();
	}

	boolean hasReservation(UUID organizationId, UUID branchId, UUID productId, UUID saleId) {
		return jdbcClient.sql("""
				SELECT EXISTS (SELECT 1 FROM inventory_sale_reservation r JOIN inventory_stock s ON s.id = r.stock_id
				WHERE r.sale_id = :sale AND s.organization_id = :org AND s.branch_id = :branch AND s.product_id = :product)
				""").param("sale", saleId).param("org", organizationId).param("branch", branchId)
				.param("product", productId).query(Boolean.class).single();
	}

	void releaseSale(UUID organizationId, UUID branchId, UUID saleId) {
		var stocks = jdbcClient.sql("""
				SELECT s.id FROM inventory_sale_reservation r JOIN inventory_stock s ON s.id = r.stock_id
				WHERE r.sale_id = :sale AND s.organization_id = :org AND s.branch_id = :branch
				ORDER BY s.product_id FOR UPDATE OF s
				""").param("sale", saleId).param("org", organizationId).param("branch", branchId).query(UUID.class).list();
		for (UUID stock : stocks) releaseStock(stock, saleId);
	}

	private void releaseStock(UUID stockId, UUID saleId) {
		jdbcClient.sql("DELETE FROM inventory_sale_reservation WHERE stock_id = :stock AND sale_id = :sale")
				.param("stock", stockId).param("sale", saleId).update();
	}

	private boolean isNegativeStockAllowed(UUID organizationId, UUID branchId) {
		return jdbcClient.sql("""
				SELECT allow_negative_stock FROM branch
				WHERE id = :branchId AND organization_id = :organizationId AND deleted_at IS NULL
				""")
				.param("branchId", branchId).param("organizationId", organizationId)
				.query(Boolean.class).optional().orElse(false);
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
