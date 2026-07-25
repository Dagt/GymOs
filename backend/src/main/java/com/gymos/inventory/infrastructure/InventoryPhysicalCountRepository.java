package com.gymos.inventory.infrastructure;

import com.gymos.inventory.application.query.InventoryPhysicalCountItemView;
import com.gymos.inventory.application.query.InventoryPhysicalCountView;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class InventoryPhysicalCountRepository {
	private final JdbcClient jdbcClient;

	InventoryPhysicalCountRepository(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	List<InventoryPhysicalCountView> list(UUID organizationId, UUID branchId) {
		var headers = jdbcClient.sql("""
				SELECT id, organization_id, branch_id, status, notes, created_at, updated_at, created_by, approved_at, approved_by
				FROM inventory_physical_count
				WHERE organization_id = :organizationId AND branch_id = :branchId
				ORDER BY created_at DESC LIMIT 50
				""")
				.param("organizationId", organizationId).param("branchId", branchId)
				.query((rs, rowNum) -> new InventoryPhysicalCountView(
						rs.getObject("id", UUID.class), rs.getObject("organization_id", UUID.class), rs.getObject("branch_id", UUID.class),
						rs.getString("status"), rs.getString("notes"), rs.getObject("created_at", OffsetDateTime.class),
						rs.getObject("updated_at", OffsetDateTime.class), rs.getObject("created_by", UUID.class),
						rs.getObject("approved_at", OffsetDateTime.class), rs.getObject("approved_by", UUID.class),
						List.of()))
				.list();

		if (headers.isEmpty()) return List.of();

		return headers.stream().map(h -> {
			var items = jdbcClient.sql("""
					SELECT i.id, i.product_id, cp.name AS product_name, cp.sku, i.system_quantity, i.counted_quantity, i.variance_quantity, i.unit_cost_cents, i.notes
					FROM inventory_physical_count_item i
					JOIN catalog_product cp ON cp.id = i.product_id
					WHERE i.physical_count_id = :countId
					ORDER BY cp.name
					""")
					.param("countId", h.id())
					.query((rs, rowNum) -> new InventoryPhysicalCountItemView(
							rs.getObject("id", UUID.class), rs.getObject("product_id", UUID.class),
							rs.getString("product_name"), rs.getString("sku"),
							rs.getInt("system_quantity"), rs.getObject("counted_quantity", Integer.class),
							rs.getObject("variance_quantity", Integer.class), rs.getObject("unit_cost_cents", Long.class),
							rs.getString("notes")))
					.list();

			return new InventoryPhysicalCountView(
					h.id(), h.organizationId(), h.branchId(), h.status(), h.notes(),
					h.createdAt(), h.updatedAt(), h.createdBy(), h.approvedAt(), h.approvedBy(), items);
		}).toList();
	}

	Optional<InventoryPhysicalCountView> findById(UUID organizationId, UUID branchId, UUID countId) {
		var header = jdbcClient.sql("""
				SELECT id, organization_id, branch_id, status, notes, created_at, updated_at, created_by, approved_at, approved_by
				FROM inventory_physical_count
				WHERE organization_id = :organizationId AND branch_id = :branchId AND id = :countId
				""")
				.param("organizationId", organizationId).param("branchId", branchId).param("countId", countId)
				.query((rs, rowNum) -> new InventoryPhysicalCountView(
						rs.getObject("id", UUID.class), rs.getObject("organization_id", UUID.class), rs.getObject("branch_id", UUID.class),
						rs.getString("status"), rs.getString("notes"), rs.getObject("created_at", OffsetDateTime.class),
						rs.getObject("updated_at", OffsetDateTime.class), rs.getObject("created_by", UUID.class),
						rs.getObject("approved_at", OffsetDateTime.class), rs.getObject("approved_by", UUID.class),
						List.of()))
				.optional();
		if (header.isEmpty()) return Optional.empty();

		var items = jdbcClient.sql("""
				SELECT i.id, i.product_id, cp.name AS product_name, cp.sku, i.system_quantity, i.counted_quantity, i.variance_quantity, i.unit_cost_cents, i.notes
				FROM inventory_physical_count_item i
				JOIN catalog_product cp ON cp.id = i.product_id
				WHERE i.physical_count_id = :countId
				ORDER BY cp.name
				""")
				.param("countId", countId)
				.query((rs, rowNum) -> new InventoryPhysicalCountItemView(
						rs.getObject("id", UUID.class), rs.getObject("product_id", UUID.class),
						rs.getString("product_name"), rs.getString("sku"),
						rs.getInt("system_quantity"), rs.getObject("counted_quantity", Integer.class),
						rs.getObject("variance_quantity", Integer.class), rs.getObject("unit_cost_cents", Long.class),
						rs.getString("notes")))
				.list();

		InventoryPhysicalCountView h = header.get();
		return Optional.of(new InventoryPhysicalCountView(
				h.id(), h.organizationId(), h.branchId(), h.status(), h.notes(),
				h.createdAt(), h.updatedAt(), h.createdBy(), h.approvedAt(), h.approvedBy(), items));
	}

	UUID create(UUID organizationId, UUID branchId, String notes, UUID actorUserId, OffsetDateTime now) {
		UUID id = UUID.randomUUID();
		jdbcClient.sql("""
				INSERT INTO inventory_physical_count (
				 id, organization_id, branch_id, status, notes, created_at, updated_at, created_by, version
				) VALUES (
				 :id, :organizationId, :branchId, 'IN_PROGRESS', :notes, :now, :now, :actorUserId, 0
				)
				""")
				.param("id", id).param("organizationId", organizationId).param("branchId", branchId)
				.param("notes", notes).param("now", now).param("actorUserId", actorUserId).update();
		return id;
	}

	void updateItem(UUID physicalCountId, UUID productId, int systemQuantity, Integer countedQuantity, Long unitCostCents, String notes, OffsetDateTime now) {
		Integer variance = countedQuantity == null ? null : countedQuantity - systemQuantity;
		jdbcClient.sql("""
				INSERT INTO inventory_physical_count_item (
				 id, physical_count_id, product_id, system_quantity, counted_quantity, variance_quantity, unit_cost_cents, notes, updated_at
				) VALUES (
				 :id, :countId, :productId, :systemQuantity, :countedQuantity, :variance, :unitCostCents, :notes, :now
				)
				ON CONFLICT (physical_count_id, product_id) DO UPDATE SET
				 system_quantity = EXCLUDED.system_quantity,
				 counted_quantity = EXCLUDED.counted_quantity,
				 variance_quantity = EXCLUDED.variance_quantity,
				 unit_cost_cents = COALESCE(EXCLUDED.unit_cost_cents, inventory_physical_count_item.unit_cost_cents),
				 notes = COALESCE(EXCLUDED.notes, inventory_physical_count_item.notes),
				 updated_at = EXCLUDED.updated_at
				""")
				.param("id", UUID.randomUUID()).param("countId", physicalCountId).param("productId", productId)
				.param("systemQuantity", systemQuantity).param("countedQuantity", countedQuantity)
				.param("variance", variance).param("unitCostCents", unitCostCents).param("notes", notes).param("now", now).update();
	}

	void updateStatus(UUID organizationId, UUID branchId, UUID physicalCountId, String status, UUID actorUserId, OffsetDateTime now) {
		boolean isApproved = "APPROVED".equals(status);
		jdbcClient.sql("""
				UPDATE inventory_physical_count
				SET status = :status,
				    updated_at = :now,
				    approved_at = CASE WHEN :isApproved THEN :now ELSE approved_at END,
				    approved_by = CASE WHEN :isApproved THEN :actorUserId ELSE approved_by END,
				    version = version + 1
				WHERE id = :id AND organization_id = :organizationId AND branch_id = :branchId
				""")
				.param("status", status).param("now", now).param("isApproved", isApproved)
				.param("actorUserId", actorUserId).param("id", physicalCountId)
				.param("organizationId", organizationId).param("branchId", branchId).update();
	}
}
