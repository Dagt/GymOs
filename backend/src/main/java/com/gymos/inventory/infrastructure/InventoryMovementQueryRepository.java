package com.gymos.inventory.infrastructure;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class InventoryMovementQueryRepository {
	private final JdbcClient jdbcClient;

	InventoryMovementQueryRepository(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	List<InventoryRepository.InventoryMovementRecord> movements(UUID organizationId, UUID branchId,
			UUID productId, int limit) {
		StringBuilder sql = new StringBuilder("""
				SELECT m.id, m.product_id, cp.sku, cp.name AS product_name, m.movement_type,
				       m.quantity_delta, m.quantity_before, m.quantity_after, m.unit_cost_cents,
				       m.reference_type, m.reference_id, m.reason, m.occurred_at
				FROM inventory_movement m
				JOIN catalog_product cp ON cp.id = m.product_id AND cp.organization_id = m.organization_id
				WHERE m.organization_id = :organizationId
				  AND m.branch_id = :branchId
				""");
		if (productId != null) sql.append(" AND m.product_id = :productId\n");
		sql.append(" ORDER BY m.occurred_at DESC, m.id DESC LIMIT :limit");
		var spec = jdbcClient.sql(sql.toString())
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("limit", limit);
		if (productId != null) spec = spec.param("productId", productId);
		return spec.query((resultSet, rowNumber) -> new InventoryRepository.InventoryMovementRecord(
				resultSet.getObject("id", UUID.class),
				resultSet.getObject("product_id", UUID.class),
				resultSet.getString("sku"),
				resultSet.getString("product_name"),
				resultSet.getString("movement_type"),
				resultSet.getInt("quantity_delta"),
				resultSet.getInt("quantity_before"),
				resultSet.getInt("quantity_after"),
				resultSet.getObject("unit_cost_cents", Long.class),
				resultSet.getString("reference_type"),
				resultSet.getObject("reference_id", UUID.class),
				resultSet.getString("reason"),
				resultSet.getObject("occurred_at", OffsetDateTime.class)
		)).list();
	}
}
