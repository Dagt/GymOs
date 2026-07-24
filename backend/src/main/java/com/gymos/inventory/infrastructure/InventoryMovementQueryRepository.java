package com.gymos.inventory.infrastructure;

import com.gymos.inventory.application.query.InventoryMovementSearchCriteria;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class InventoryMovementQueryRepository {
	private static final String SELECT_MOVEMENTS = """
			SELECT m.id, m.product_id, cp.sku, cp.name AS product_name, m.movement_type,
			       m.quantity_delta, m.quantity_before, m.quantity_after, m.unit_cost_cents,
			       m.reference_type, m.reference_id, m.reason, m.occurred_at
			FROM inventory_movement m
			JOIN catalog_product cp ON cp.id=m.product_id AND cp.organization_id=m.organization_id
			WHERE m.organization_id=:organizationId AND m.branch_id=:branchId
			""";
	private final JdbcClient jdbcClient;

	InventoryMovementQueryRepository(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	List<InventoryRepository.InventoryMovementRecord> movements(UUID organizationId, UUID branchId,
			UUID productId, int limit) {
		String productFilter = productId == null ? "" : " AND m.product_id=:productId";
		var spec = jdbcClient.sql(SELECT_MOVEMENTS + productFilter + " ORDER BY m.occurred_at DESC, m.id DESC LIMIT :limit")
				.param("organizationId", organizationId).param("branchId", branchId).param("limit", limit);
		if (productId != null) spec = spec.param("productId", productId);
		return spec.query(this::map).list();
	}

	MovementPageRecords search(UUID organizationId, UUID branchId, InventoryMovementSearchCriteria criteria) {
		String productFilter = criteria.productId() == null ? "" : " AND m.product_id=:productId";
		var itemSpec = jdbcClient.sql(SELECT_MOVEMENTS + productFilter
				+ " ORDER BY m.occurred_at DESC, m.id DESC LIMIT :size OFFSET :offset")
				.param("organizationId", organizationId).param("branchId", branchId)
				.param("size", criteria.size()).param("offset", criteria.page() * criteria.size());
		var countSpec = jdbcClient.sql("""
				SELECT COUNT(*) FROM inventory_movement m
				WHERE m.organization_id=:organizationId AND m.branch_id=:branchId
				""" + productFilter).param("organizationId", organizationId).param("branchId", branchId);
		if (criteria.productId() != null) {
			itemSpec = itemSpec.param("productId", criteria.productId());
			countSpec = countSpec.param("productId", criteria.productId());
		}
		return new MovementPageRecords(itemSpec.query(this::map).list(), countSpec.query(Long.class).single());
	}

	private InventoryRepository.InventoryMovementRecord map(java.sql.ResultSet resultSet, int rowNumber)
			throws java.sql.SQLException {
		return new InventoryRepository.InventoryMovementRecord(
				resultSet.getObject("id", UUID.class), resultSet.getObject("product_id", UUID.class),
				resultSet.getString("sku"), resultSet.getString("product_name"), resultSet.getString("movement_type"),
				resultSet.getInt("quantity_delta"), resultSet.getInt("quantity_before"),
				resultSet.getInt("quantity_after"), resultSet.getObject("unit_cost_cents", Long.class),
				resultSet.getString("reference_type"), resultSet.getObject("reference_id", UUID.class),
				resultSet.getString("reason"), resultSet.getObject("occurred_at", OffsetDateTime.class));
	}

	record MovementPageRecords(List<InventoryRepository.InventoryMovementRecord> items, long total) {
	}
}
