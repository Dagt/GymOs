package com.gymos.inventory.infrastructure;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.gymos.common.api.BusinessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class InventoryRepository {
	private final JdbcClient jdbcClient;

	public InventoryRepository(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	public List<InventoryStockRecord> stock(UUID organizationId, UUID branchId) {
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
				  AND cp.deleted_at IS NULL
				ORDER BY cp.name
				""")
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.query(this::mapStock)
				.list();
	}

	public Optional<InventoryStockRecord> stockDetail(UUID organizationId, UUID branchId, UUID productId) {
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
				  AND cp.deleted_at IS NULL
				""")
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("productId", productId)
				.query(this::mapStock)
				.optional();
	}

	public Optional<InventoryStockRecord> stockById(UUID organizationId, UUID branchId, UUID stockId) {
		return jdbcClient.sql("""
				SELECT cp.id AS product_id, cp.sku, cp.name AS product_name, cp.price_cents, cp.currency,
				       s.id AS stock_id, s.quantity_on_hand, s.reorder_point, s.average_cost_cents, s.updated_at
				FROM inventory_stock s
				JOIN catalog_product cp ON cp.id = s.product_id
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

	public List<InventoryStockRecord> lowStock(UUID organizationId, UUID branchId) {
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
				  AND cp.deleted_at IS NULL
				  AND s.reorder_point > 0
				  AND s.quantity_on_hand <= s.reorder_point
				ORDER BY s.quantity_on_hand, cp.name
				""")
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.query(this::mapStock)
				.list();
	}

	public List<InventoryMovementRecord> movements(UUID organizationId, UUID branchId, UUID productId, int limit) {
		StringBuilder sql = new StringBuilder("""
				SELECT m.id, m.product_id, cp.sku, cp.name AS product_name, m.movement_type,
				       m.quantity_delta, m.quantity_before, m.quantity_after, m.unit_cost_cents,
				       m.reference_type, m.reference_id, m.reason, m.occurred_at
				FROM inventory_movement m
				JOIN catalog_product cp ON cp.id = m.product_id
				WHERE m.organization_id = :organizationId
				  AND m.branch_id = :branchId
				""");
		if (productId != null) {
			sql.append(" AND m.product_id = :productId\n");
		}
		sql.append("""
				ORDER BY m.occurred_at DESC
				LIMIT :limit
				""");
		var spec = jdbcClient.sql(sql.toString())
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("limit", limit);
		if (productId != null) {
			spec = spec.param("productId", productId);
		}
		return spec.query((resultSet, rowNumber) -> new InventoryMovementRecord(
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
				))
				.list();
	}

	public Optional<UUID> stockIdentity(UUID organizationId, UUID branchId, UUID productId) {
		return jdbcClient.sql("""
				SELECT id
				FROM inventory_stock
				WHERE organization_id = :organizationId
				  AND branch_id = :branchId
				  AND product_id = :productId
				  AND deleted_at IS NULL
				""")
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("productId", productId)
				.query(UUID.class)
				.optional();
	}

	public UUID createStock(UUID organizationId, UUID branchId, UUID productId, UUID actorUserId, OffsetDateTime now) {
		UUID stockId = UUID.randomUUID();
		jdbcClient.sql("""
				INSERT INTO inventory_stock (
				    id, organization_id, branch_id, product_id, quantity_on_hand, reorder_point,
				    created_at, updated_at, created_by, updated_by, version
				)
				VALUES (
				    :id, :organizationId, :branchId, :productId, 0, 0,
				    :createdAt, :updatedAt, :createdBy, :updatedBy, 0
				)
				""")
				.param("id", stockId)
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("productId", productId)
				.param("createdAt", now)
				.param("updatedAt", now)
				.param("createdBy", actorUserId)
				.param("updatedBy", actorUserId)
				.update();
		return stockId;
	}

	public UUID ensureStock(UUID organizationId, UUID branchId, UUID productId, UUID actorUserId, OffsetDateTime now) {
		UUID stockId = UUID.randomUUID();
		jdbcClient.sql("""
				INSERT INTO inventory_stock (
				    id, organization_id, branch_id, product_id, quantity_on_hand, reorder_point,
				    created_at, updated_at, created_by, updated_by, version
				)
				VALUES (
				    :id, :organizationId, :branchId, :productId, 0, 0,
				    :createdAt, :updatedAt, :createdBy, :updatedBy, 0
				)
				ON CONFLICT DO NOTHING
				""")
				.param("id", stockId)
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("productId", productId)
				.param("createdAt", now)
				.param("updatedAt", now)
				.param("createdBy", actorUserId)
				.param("updatedBy", actorUserId)
				.update();
		return stockIdentity(organizationId, branchId, productId)
				.orElseThrow(() -> new BusinessException("INVENTORY_STOCK_NOT_FOUND", "No fue posible preparar existencias."));
	}

	public StockDeductionResult deductPaidSaleItem(
			UUID organizationId,
			UUID branchId,
			UUID productId,
			UUID saleId,
			int quantity,
			OffsetDateTime occurredAt,
			UUID correlationId
	) {
		ProductForInventory product = product(organizationId, productId).orElse(null);
		if (product == null || !"PHYSICAL".equals(product.productType()) || !"ACTIVE".equals(product.status())) {
			return StockDeductionResult.IGNORED_NON_PHYSICAL;
		}
		if (quantity <= 0) {
			throw new BusinessException("INVENTORY_QUANTITY_INVALID", "La cantidad a descontar debe ser mayor a cero.");
		}
		UUID stockId = ensureStock(organizationId, branchId, productId, null, occurredAt);
		StockLedgerRecord stock = lockStock(organizationId, branchId, stockId)
				.orElseThrow(() -> new BusinessException("INVENTORY_STOCK_NOT_FOUND", "No fue posible bloquear existencias."));
		if (movementExists(organizationId, branchId, productId, "SALE", saleId)) {
			return StockDeductionResult.ALREADY_PROCESSED;
		}
		int quantityAfter = stock.quantityOnHand() - quantity;
		if (quantityAfter < 0) {
			throw new BusinessException("INVENTORY_STOCK_INSUFFICIENT", "La venta supera las existencias disponibles de " + product.name() + ".");
		}
		updateStock(stockId, quantityAfter, stock.reorderPoint(), stock.averageCostCents(), null, occurredAt);
		createMovement(
				organizationId,
				branchId,
				stockId,
				productId,
				"SALE",
				-quantity,
				stock.quantityOnHand(),
				quantityAfter,
				null,
				"SALE",
				saleId,
				"Salida automatica por venta pagada.",
				occurredAt,
				null,
				correlationId
		);
		return StockDeductionResult.DEDUCTED;
	}

	public void updateStock(UUID stockId, int quantityOnHand, int reorderPoint, Long averageCostCents, UUID actorUserId, OffsetDateTime now) {
		jdbcClient.sql("""
				UPDATE inventory_stock
				SET quantity_on_hand = :quantityOnHand,
				    reorder_point = :reorderPoint,
				    average_cost_cents = :averageCostCents,
				    updated_at = :updatedAt,
				    updated_by = COALESCE(:updatedBy, updated_by),
				    version = version + 1
				WHERE id = :stockId
				""")
				.param("quantityOnHand", quantityOnHand)
				.param("reorderPoint", reorderPoint)
				.param("averageCostCents", averageCostCents)
				.param("updatedAt", now)
				.param("updatedBy", actorUserId)
				.param("stockId", stockId)
				.update();
	}

	public UUID createMovement(
			UUID organizationId,
			UUID branchId,
			UUID stockId,
			UUID productId,
			String movementType,
			int quantityDelta,
			int quantityBefore,
			int quantityAfter,
			Long unitCostCents,
			String referenceType,
			UUID referenceId,
			String reason,
			OffsetDateTime occurredAt,
			UUID actorUserId,
			UUID correlationId
	) {
		UUID movementId = UUID.randomUUID();
		jdbcClient.sql("""
				INSERT INTO inventory_movement (
				    id, organization_id, branch_id, stock_id, product_id, movement_type,
				    quantity_delta, quantity_before, quantity_after, unit_cost_cents,
				    reference_type, reference_id, reason, occurred_at, created_at, created_by, correlation_id
				)
				VALUES (
				    :id, :organizationId, :branchId, :stockId, :productId, :movementType,
				    :quantityDelta, :quantityBefore, :quantityAfter, :unitCostCents,
				    :referenceType, :referenceId, :reason, :occurredAt, :createdAt, :createdBy, :correlationId
				)
				""")
				.param("id", movementId)
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("stockId", stockId)
				.param("productId", productId)
				.param("movementType", movementType)
				.param("quantityDelta", quantityDelta)
				.param("quantityBefore", quantityBefore)
				.param("quantityAfter", quantityAfter)
				.param("unitCostCents", unitCostCents)
				.param("referenceType", referenceType)
				.param("referenceId", referenceId)
				.param("reason", reason)
				.param("occurredAt", occurredAt)
				.param("createdAt", occurredAt)
				.param("createdBy", actorUserId)
				.param("correlationId", correlationId)
				.update();
		return movementId;
	}

	public Optional<ProductForInventory> product(UUID organizationId, UUID productId) {
		return jdbcClient.sql("""
				SELECT id, sku, name, product_type, status
				FROM catalog_product
				WHERE organization_id = :organizationId
				  AND id = :productId
				  AND deleted_at IS NULL
				""")
				.param("organizationId", organizationId)
				.param("productId", productId)
				.query((resultSet, rowNumber) -> new ProductForInventory(
						resultSet.getObject("id", UUID.class),
						resultSet.getString("sku"),
						resultSet.getString("name"),
						resultSet.getString("product_type"),
						resultSet.getString("status")
				))
				.optional();
	}

	private Optional<StockLedgerRecord> lockStock(UUID organizationId, UUID branchId, UUID stockId) {
		return jdbcClient.sql("""
				SELECT id, quantity_on_hand, reorder_point, average_cost_cents
				FROM inventory_stock
				WHERE organization_id = :organizationId
				  AND branch_id = :branchId
				  AND id = :stockId
				  AND deleted_at IS NULL
				FOR UPDATE
				""")
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("stockId", stockId)
				.query((resultSet, rowNumber) -> new StockLedgerRecord(
						resultSet.getObject("id", UUID.class),
						resultSet.getInt("quantity_on_hand"),
						resultSet.getInt("reorder_point"),
						resultSet.getObject("average_cost_cents", Long.class)
				))
				.optional();
	}

	private boolean movementExists(UUID organizationId, UUID branchId, UUID productId, String referenceType, UUID referenceId) {
		Integer count = jdbcClient.sql("""
				SELECT COUNT(*)
				FROM inventory_movement
				WHERE organization_id = :organizationId
				  AND branch_id = :branchId
				  AND product_id = :productId
				  AND reference_type = :referenceType
				  AND reference_id = :referenceId
				""")
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("productId", productId)
				.param("referenceType", referenceType)
				.param("referenceId", referenceId)
				.query(Integer.class)
				.single();
		return count > 0;
	}

	private InventoryStockRecord mapStock(java.sql.ResultSet resultSet, int rowNumber) throws java.sql.SQLException {
		return new InventoryStockRecord(
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

	public record ProductForInventory(UUID id, String sku, String name, String productType, String status) {
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
