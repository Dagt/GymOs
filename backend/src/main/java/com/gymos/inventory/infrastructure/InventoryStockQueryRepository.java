package com.gymos.inventory.infrastructure;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class InventoryStockQueryRepository {
	private final JdbcClient jdbcClient;

	InventoryStockQueryRepository(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	List<InventoryRepository.InventoryStockRecord> stock(UUID organizationId, UUID branchId) {
		return jdbcClient.sql("""
				SELECT cp.id AS product_id, cp.sku, cp.name AS product_name, cp.price_cents, cp.currency,
				       s.id AS stock_id, COALESCE(s.quantity_on_hand, 0) AS quantity_on_hand,
				       COALESCE(s.reorder_point, 0) AS reorder_point,
				       s.average_cost_cents, s.updated_at
				FROM catalog_product cp
				LEFT JOIN inventory_stock s ON s.product_id = cp.id
				    AND s.organization_id = cp.organization_id
				    AND s.branch_id = :branchId
				    AND s.deleted_at IS NULL
				WHERE cp.organization_id = :organizationId
				  AND cp.product_type = 'PHYSICAL'
				  AND cp.inventory_tracked = TRUE
				  AND cp.deleted_at IS NULL
				  AND cp.archived_at IS NULL
				ORDER BY cp.name
				""")
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.query(this::mapStock)
				.list();
	}

	Optional<InventoryRepository.InventoryStockRecord> stockDetail(UUID organizationId, UUID branchId, UUID productId) {
		return jdbcClient.sql("""
				SELECT cp.id AS product_id, cp.sku, cp.name AS product_name, cp.price_cents, cp.currency,
				       s.id AS stock_id, COALESCE(s.quantity_on_hand, 0) AS quantity_on_hand,
				       COALESCE(s.reorder_point, 0) AS reorder_point,
				       s.average_cost_cents, s.updated_at
				FROM catalog_product cp
				LEFT JOIN inventory_stock s ON s.product_id = cp.id
				    AND s.organization_id = cp.organization_id
				    AND s.branch_id = :branchId
				    AND s.deleted_at IS NULL
				WHERE cp.organization_id = :organizationId
				  AND cp.id = :productId
				  AND cp.product_type = 'PHYSICAL'
				  AND cp.inventory_tracked = TRUE
				  AND cp.deleted_at IS NULL
				  AND cp.archived_at IS NULL
				""")
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("productId", productId)
				.query(this::mapStock)
				.optional();
	}

	Optional<InventoryRepository.InventoryStockRecord> stockById(UUID organizationId, UUID branchId, UUID stockId) {
		return jdbcClient.sql("""
				SELECT cp.id AS product_id, cp.sku, cp.name AS product_name, cp.price_cents, cp.currency,
				       s.id AS stock_id, s.quantity_on_hand, s.reorder_point, s.average_cost_cents, s.updated_at
				FROM inventory_stock s
				JOIN catalog_product cp ON cp.id = s.product_id AND cp.organization_id = s.organization_id
				WHERE s.organization_id = :organizationId
				  AND s.branch_id = :branchId
				  AND s.id = :stockId
				  AND s.deleted_at IS NULL
				""")
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("stockId", stockId)
				.query(this::mapStock)
				.optional();
	}

	List<InventoryRepository.InventoryStockRecord> lowStock(UUID organizationId, UUID branchId) {
		return jdbcClient.sql("""
				SELECT cp.id AS product_id, cp.sku, cp.name AS product_name, cp.price_cents, cp.currency,
				       s.id AS stock_id, COALESCE(s.quantity_on_hand, 0) AS quantity_on_hand,
				       COALESCE(s.reorder_point, 0) AS reorder_point,
				       s.average_cost_cents, s.updated_at
				FROM catalog_product cp
				JOIN inventory_stock s ON s.product_id = cp.id
				    AND s.organization_id = cp.organization_id
				    AND s.branch_id = :branchId
				    AND s.deleted_at IS NULL
				WHERE cp.organization_id = :organizationId
				  AND cp.product_type = 'PHYSICAL'
				  AND cp.inventory_tracked = TRUE
				  AND cp.deleted_at IS NULL
				  AND cp.archived_at IS NULL
				  AND s.reorder_point > 0
				  AND s.quantity_on_hand <= s.reorder_point
				ORDER BY s.quantity_on_hand, cp.name
				""")
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.query(this::mapStock)
				.list();
	}

	private InventoryRepository.InventoryStockRecord mapStock(java.sql.ResultSet resultSet, int rowNumber)
			throws java.sql.SQLException {
		return new InventoryRepository.InventoryStockRecord(
				resultSet.getObject("stock_id", UUID.class),
				resultSet.getObject("product_id", UUID.class),
				resultSet.getString("sku"),
				resultSet.getString("product_name"),
				resultSet.getLong("price_cents"),
				resultSet.getString("currency"),
				resultSet.getInt("quantity_on_hand"),
				resultSet.getInt("reorder_point"),
				resultSet.getObject("average_cost_cents", Long.class),
				resultSet.getObject("updated_at", OffsetDateTime.class)
		);
	}
}
