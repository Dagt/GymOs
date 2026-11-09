package com.gymos.inventory.infrastructure;

import com.gymos.inventory.api.InventoryRefundOperations;
import com.gymos.inventory.api.InventoryRefundOperations.RefundedInventoryItem;
import com.gymos.common.api.BusinessException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class InventoryRefundAdapter implements InventoryRefundOperations {
	private final JdbcClient jdbcClient;
	private final InventoryStockCommandRepository stockCommands;

	InventoryRefundAdapter(JdbcClient jdbcClient, InventoryStockCommandRepository stockCommands) {
		this.jdbcClient = jdbcClient;
		this.stockCommands = stockCommands;
	}

	@Override
	public void restoreFullSale(UUID organizationId, UUID branchId, UUID saleId, UUID refundId,
			UUID actorUserId, OffsetDateTime occurredAt, UUID correlationId) {
		List<RefundItem> items = jdbcClient.sql("""
				SELECT product_id, SUM(-quantity_delta)::int AS quantity
				FROM inventory_movement
				WHERE organization_id=:organizationId AND branch_id=:branchId AND reference_id=:saleId
				  AND reference_type='SALE' AND movement_type='SALE'
				GROUP BY product_id ORDER BY product_id
				""")
				.param("organizationId", organizationId)
				.param("saleId", saleId)
				.param("branchId", branchId)
				.query((rs, row) -> new RefundItem(rs.getObject("product_id", UUID.class), rs.getInt("quantity")))
				.list();
		for (RefundItem item : items) {
			restoreItem(organizationId, branchId, saleId, refundId, actorUserId, occurredAt, correlationId, item,
					"Reposicion automatica por reembolso total.");
		}
	}

	@Override
	public void restoreItems(UUID organizationId, UUID branchId, UUID saleId, UUID refundId,
			UUID actorUserId, List<RefundedInventoryItem> items, OffsetDateTime occurredAt, UUID correlationId) {
		for (RefundedInventoryItem item : items) {
			restoreItem(organizationId, branchId, saleId, refundId, actorUserId, occurredAt, correlationId,
					new RefundItem(item.productId(), item.quantity()),
					"Reposicion automatica por devolucion de producto.");
		}
	}

	private void restoreItem(UUID organizationId, UUID branchId, UUID saleId, UUID refundId, UUID actorUserId,
			OffsetDateTime occurredAt, UUID correlationId, RefundItem item, String reason) {
		UUID stockId = stockCommands.ensureStock(organizationId, branchId, item.productId(), actorUserId, occurredAt);
		InventoryStockCommandRepository.StockLedger stock = stockCommands.lock(organizationId, branchId, stockId).orElseThrow();
		if (movementExists(organizationId, branchId, item.productId(), refundId)) return;
		if (item.quantity() > restorableQuantity(organizationId, branchId, item.productId(), saleId)) {
			throw new BusinessException("INVENTORY_REFUND_EXCEEDS_SOLD_STOCK", "La devolución supera las unidades descontadas para esta venta.");
		}
		long after = (long) stock.quantityOnHand() + item.quantity();
		if (after > Integer.MAX_VALUE) {
			throw new BusinessException("INVENTORY_QUANTITY_INVALID", "El saldo de inventario está fuera de rango.");
		}
		int quantityAfter = (int) after;
		stockCommands.update(stockId, quantityAfter, stock.reorderPoint(), stock.averageCostCents(), actorUserId, occurredAt);
		jdbcClient.sql("""
				INSERT INTO inventory_movement (
				 id, organization_id, branch_id, stock_id, product_id, movement_type,
				 quantity_delta, quantity_before, quantity_after, reference_type, reference_id,
				 reason, occurred_at, created_at, created_by, correlation_id
				) VALUES (
				 :id, :organizationId, :branchId, :stockId, :productId, 'RETURN',
				 :quantity, :before, :after, 'SALE_REFUND', :refundId,
				 :reason, :occurredAt, :occurredAt, :actorUserId, :correlationId
				)
				ON CONFLICT DO NOTHING
				""")
				.param("id", UUID.randomUUID()).param("organizationId", organizationId).param("branchId", branchId)
				.param("stockId", stockId).param("productId", item.productId()).param("quantity", item.quantity())
				.param("before", stock.quantityOnHand()).param("after", quantityAfter).param("refundId", refundId)
				.param("reason", reason).param("occurredAt", occurredAt).param("actorUserId", actorUserId)
				.param("correlationId", correlationId)
				.update();
	}

	private long restorableQuantity(UUID organizationId, UUID branchId, UUID productId, UUID saleId) {
		return jdbcClient.sql("""
				SELECT COALESCE(SUM(-m.quantity_delta), 0) FROM inventory_movement m
				WHERE m.organization_id=:org AND m.branch_id=:branch AND m.product_id=:product AND (
				    (m.reference_type='SALE' AND m.movement_type='SALE' AND m.reference_id=:sale)
				    OR (m.reference_type='SALE_REFUND' AND m.movement_type='RETURN' AND EXISTS (
				        SELECT 1 FROM sale_refund r WHERE r.id=m.reference_id AND r.organization_id=:org AND r.sale_id=:sale)))
				""").param("org", organizationId).param("branch", branchId).param("product", productId)
				.param("sale", saleId).query(Long.class).single();
	}

	private boolean movementExists(UUID organizationId, UUID branchId, UUID productId, UUID refundId) {
		return jdbcClient.sql("""
				SELECT COUNT(*) FROM inventory_movement
				WHERE organization_id=:organizationId AND branch_id=:branchId AND product_id=:productId
				  AND reference_type='SALE_REFUND' AND reference_id=:refundId
				""")
				.param("organizationId", organizationId).param("branchId", branchId)
				.param("productId", productId).param("refundId", refundId)
				.query(Integer.class).single() > 0;
	}

	private record RefundItem(UUID productId, int quantity) {
	}
}
