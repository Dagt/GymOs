package com.gymos.inventory.infrastructure;

import com.gymos.inventory.api.InventoryRefundOperations;
import com.gymos.inventory.api.InventoryRefundOperations.RefundedInventoryItem;
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
				SELECT si.product_id, SUM(si.quantity)::int AS quantity
				FROM sale_item si
				JOIN catalog_product cp ON cp.id=si.product_id AND cp.organization_id=si.organization_id
				WHERE si.organization_id=:organizationId AND si.sale_id=:saleId
				  AND cp.product_type='PHYSICAL' AND cp.inventory_tracked=TRUE
				GROUP BY si.product_id
				""")
				.param("organizationId", organizationId)
				.param("saleId", saleId)
				.query((rs, row) -> new RefundItem(rs.getObject("product_id", UUID.class), rs.getInt("quantity")))
				.list();
		for (RefundItem item : items) {
			restoreItem(organizationId, branchId, refundId, actorUserId, occurredAt, correlationId, item,
					"Reposicion automatica por reembolso total.");
		}
	}

	@Override
	public void restoreItems(UUID organizationId, UUID branchId, UUID saleId, UUID refundId,
			UUID actorUserId, List<RefundedInventoryItem> items, OffsetDateTime occurredAt, UUID correlationId) {
		for (RefundedInventoryItem item : items) {
			restoreItem(organizationId, branchId, refundId, actorUserId, occurredAt, correlationId,
					new RefundItem(item.productId(), item.quantity()),
					"Reposicion automatica por devolucion de producto.");
		}
	}

	private void restoreItem(UUID organizationId, UUID branchId, UUID refundId, UUID actorUserId,
			OffsetDateTime occurredAt, UUID correlationId, RefundItem item, String reason) {
		UUID stockId = stockCommands.ensureStock(organizationId, branchId, item.productId(), actorUserId, occurredAt);
		InventoryStockCommandRepository.StockLedger stock = stockCommands.lock(organizationId, branchId, stockId).orElseThrow();
		if (movementExists(organizationId, branchId, item.productId(), refundId)) return;
		int quantityAfter = stock.quantityOnHand() + item.quantity();
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
