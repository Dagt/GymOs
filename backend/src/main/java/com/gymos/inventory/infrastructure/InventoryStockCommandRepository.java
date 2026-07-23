package com.gymos.inventory.infrastructure;

import com.gymos.common.api.BusinessException;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class InventoryStockCommandRepository {
	private final JdbcClient jdbcClient;

	public InventoryStockCommandRepository(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	public UUID ensureStock(UUID organizationId, UUID branchId, UUID productId, UUID actorUserId, OffsetDateTime now) {
		UUID candidateId = UUID.randomUUID();
		jdbcClient.sql("""
				INSERT INTO inventory_stock (
				    id, organization_id, branch_id, product_id, quantity_on_hand, reorder_point,
				    created_at, updated_at, created_by, updated_by, version
				)
				VALUES (
				    :id, :organizationId, :branchId, :productId, 0, 0,
				    :now, :now, :actorUserId, :actorUserId, 0
				)
				ON CONFLICT DO NOTHING
				""")
				.param("id", candidateId)
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("productId", productId)
				.param("now", now)
				.param("actorUserId", actorUserId)
				.update();
		return jdbcClient.sql("""
				SELECT id FROM inventory_stock
				WHERE organization_id = :organizationId AND branch_id = :branchId
				  AND product_id = :productId AND deleted_at IS NULL
				""")
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("productId", productId)
				.query(UUID.class)
				.optional()
				.orElseThrow(() -> new BusinessException("INVENTORY_STOCK_NOT_FOUND", "No fue posible preparar existencias."));
	}

	public Optional<StockLedger> lock(UUID organizationId, UUID branchId, UUID stockId) {
		return jdbcClient.sql("""
				SELECT id, quantity_on_hand, reorder_point, average_cost_cents
				FROM inventory_stock
				WHERE organization_id = :organizationId AND branch_id = :branchId
				  AND id = :stockId AND deleted_at IS NULL
				FOR UPDATE
				""")
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("stockId", stockId)
				.query((resultSet, rowNumber) -> new StockLedger(
						resultSet.getObject("id", UUID.class),
						resultSet.getInt("quantity_on_hand"),
						resultSet.getInt("reorder_point"),
						resultSet.getObject("average_cost_cents", Long.class)))
				.optional();
	}

	public void update(UUID stockId, int quantityOnHand, int reorderPoint, Long averageCostCents,
			UUID actorUserId, OffsetDateTime now) {
		int updated = jdbcClient.sql("""
				UPDATE inventory_stock
				SET quantity_on_hand = :quantityOnHand,
				    reorder_point = :reorderPoint,
				    average_cost_cents = :averageCostCents,
				    updated_at = :now,
				    updated_by = COALESCE(:actorUserId, updated_by),
				    version = version + 1
				WHERE id = :stockId AND deleted_at IS NULL
				""")
				.param("quantityOnHand", quantityOnHand)
				.param("reorderPoint", reorderPoint)
				.param("averageCostCents", averageCostCents)
				.param("now", now)
				.param("actorUserId", actorUserId)
				.param("stockId", stockId)
				.update();
		if (updated != 1) {
			throw new BusinessException("INVENTORY_STOCK_NOT_FOUND", "No fue posible actualizar existencias.");
		}
	}

	public record StockLedger(UUID stockId, int quantityOnHand, int reorderPoint, Long averageCostCents) {
	}
}
