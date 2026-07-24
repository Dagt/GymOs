package com.gymos.inventory.infrastructure;

import com.gymos.inventory.application.query.InventoryStockSearchCriteria;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class InventoryStockQueryRepository {
	private static final String SELECT_STOCK = """
			SELECT cp.id AS product_id, cp.sku, cp.name AS product_name, cp.price_cents, cp.currency,
			       s.id AS stock_id, COALESCE(s.quantity_on_hand, 0) AS quantity_on_hand,
			       COALESCE(s.reorder_point, 0) AS reorder_point, s.average_cost_cents, s.updated_at
			FROM catalog_product cp
			JOIN catalog_product_branch cpb ON cpb.organization_id=cp.organization_id
			    AND cpb.product_id=cp.id AND cpb.branch_id=:branchId
			LEFT JOIN inventory_stock s ON s.product_id=cp.id AND s.organization_id=cp.organization_id
			    AND s.branch_id=:branchId AND s.deleted_at IS NULL
			WHERE cp.organization_id=:organizationId AND cp.product_type='PHYSICAL'
			  AND cp.inventory_tracked=TRUE AND cp.deleted_at IS NULL AND cp.archived_at IS NULL
			""";
	private final JdbcClient jdbcClient;

	InventoryStockQueryRepository(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	List<InventoryRepository.InventoryStockRecord> stock(UUID organizationId, UUID branchId) {
		return jdbcClient.sql(SELECT_STOCK + " ORDER BY cp.name, cp.id")
				.param("organizationId", organizationId).param("branchId", branchId)
				.query(this::mapStock).list();
	}

	StockPageRecords search(UUID organizationId, UUID branchId, InventoryStockSearchCriteria criteria) {
		String queryFilter = hasText(criteria.query())
				? " AND (LOWER(cp.name) LIKE :query OR LOWER(cp.sku) LIKE :query OR LOWER(COALESCE(cp.barcode,'')) LIKE :query)"
				: "";
		var itemSpec = jdbcClient.sql(SELECT_STOCK + queryFilter + " ORDER BY " + orderBy(criteria.sort()) + " LIMIT :size OFFSET :offset")
				.param("organizationId", organizationId).param("branchId", branchId)
				.param("size", criteria.size()).param("offset", criteria.page() * criteria.size());
		var countSpec = jdbcClient.sql("""
				SELECT COUNT(*) FROM catalog_product cp
				JOIN catalog_product_branch cpb ON cpb.organization_id=cp.organization_id
				  AND cpb.product_id=cp.id AND cpb.branch_id=:branchId
				WHERE cp.organization_id=:organizationId AND cp.product_type='PHYSICAL'
				  AND cp.inventory_tracked=TRUE AND cp.deleted_at IS NULL AND cp.archived_at IS NULL
				""" + queryFilter).param("organizationId", organizationId).param("branchId", branchId);
		if (hasText(criteria.query())) {
			String value = "%" + criteria.query().trim().toLowerCase() + "%";
			itemSpec = itemSpec.param("query", value);
			countSpec = countSpec.param("query", value);
		}
		return new StockPageRecords(itemSpec.query(this::mapStock).list(), countSpec.query(Long.class).single());
	}

	Optional<InventoryRepository.InventoryStockRecord> stockDetail(UUID organizationId, UUID branchId, UUID productId) {
		return jdbcClient.sql(SELECT_STOCK + " AND cp.id=:productId")
				.param("organizationId", organizationId).param("branchId", branchId).param("productId", productId)
				.query(this::mapStock).optional();
	}

	Optional<InventoryRepository.InventoryStockRecord> stockById(UUID organizationId, UUID branchId, UUID stockId) {
		return jdbcClient.sql("""
				SELECT cp.id AS product_id, cp.sku, cp.name AS product_name, cp.price_cents, cp.currency,
				       s.id AS stock_id, s.quantity_on_hand, s.reorder_point, s.average_cost_cents, s.updated_at
				FROM inventory_stock s
				JOIN catalog_product cp ON cp.id=s.product_id AND cp.organization_id=s.organization_id
				JOIN catalog_product_branch cpb ON cpb.organization_id=cp.organization_id
				  AND cpb.product_id=cp.id AND cpb.branch_id=s.branch_id
				WHERE s.organization_id=:organizationId AND s.branch_id=:branchId AND s.id=:stockId
				  AND s.deleted_at IS NULL
				""")
				.param("organizationId", organizationId).param("branchId", branchId).param("stockId", stockId)
				.query(this::mapStock).optional();
	}

	List<InventoryRepository.InventoryStockRecord> lowStock(UUID organizationId, UUID branchId) {
		return jdbcClient.sql(SELECT_STOCK + " AND s.reorder_point>0 AND s.quantity_on_hand<=s.reorder_point ORDER BY s.quantity_on_hand, cp.name LIMIT 100")
				.param("organizationId", organizationId).param("branchId", branchId)
				.query(this::mapStock).list();
	}

	private String orderBy(String sort) {
		return switch (sort) {
			case "name,desc" -> "cp.name DESC, cp.id";
			case "quantity,asc" -> "COALESCE(s.quantity_on_hand,0) ASC, cp.name, cp.id";
			case "quantity,desc" -> "COALESCE(s.quantity_on_hand,0) DESC, cp.name, cp.id";
			case "updatedAt,desc" -> "s.updated_at DESC NULLS LAST, cp.name, cp.id";
			default -> "cp.name ASC, cp.id";
		};
	}

	private boolean hasText(String value) { return value != null && !value.isBlank(); }

	private InventoryRepository.InventoryStockRecord mapStock(java.sql.ResultSet resultSet, int rowNumber)
			throws java.sql.SQLException {
		return new InventoryRepository.InventoryStockRecord(
				resultSet.getObject("stock_id", UUID.class), resultSet.getObject("product_id", UUID.class),
				resultSet.getString("sku"), resultSet.getString("product_name"), resultSet.getLong("price_cents"),
				resultSet.getString("currency"), resultSet.getInt("quantity_on_hand"), resultSet.getInt("reorder_point"),
				resultSet.getObject("average_cost_cents", Long.class), resultSet.getObject("updated_at", OffsetDateTime.class));
	}

	record StockPageRecords(List<InventoryRepository.InventoryStockRecord> items, long total) {
	}
}
