package com.gymos.inventory.infrastructure;

import com.gymos.inventory.application.query.InventoryTransferItemView;
import com.gymos.inventory.application.query.InventoryTransferView;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class InventoryTransferRepository {
	private final JdbcClient jdbcClient;

	InventoryTransferRepository(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	List<InventoryTransferView> list(UUID organizationId, UUID branchId) {
		return jdbcClient.sql("""
				SELECT t.id, t.organization_id, t.origin_branch_id, ob.name AS origin_branch_name,
				       t.destination_branch_id, db.name AS destination_branch_name,
				       t.status, t.notes, t.created_at, t.updated_at, t.created_by,
				       t.shipped_at, t.shipped_by, t.received_at, t.received_by
				FROM inventory_transfer t
				JOIN branch ob ON ob.id = t.origin_branch_id
				JOIN branch db ON db.id = t.destination_branch_id
				WHERE t.organization_id = :organizationId
				  AND (t.origin_branch_id = :branchId OR t.destination_branch_id = :branchId)
				ORDER BY t.created_at DESC LIMIT 50
				""")
				.param("organizationId", organizationId).param("branchId", branchId)
				.query((rs, rowNum) -> new InventoryTransferView(
						rs.getObject("id", UUID.class), rs.getObject("organization_id", UUID.class),
						rs.getObject("origin_branch_id", UUID.class), rs.getString("origin_branch_name"),
						rs.getObject("destination_branch_id", UUID.class), rs.getString("destination_branch_name"),
						rs.getString("status"), rs.getString("notes"),
						rs.getObject("created_at", OffsetDateTime.class), rs.getObject("updated_at", OffsetDateTime.class),
						rs.getObject("created_by", UUID.class), rs.getObject("shipped_at", OffsetDateTime.class),
						rs.getObject("shipped_by", UUID.class), rs.getObject("received_at", OffsetDateTime.class),
						rs.getObject("received_by", UUID.class), List.of()))
				.list();
	}

	Optional<InventoryTransferView> findById(UUID organizationId, UUID branchId, UUID transferId) {
		var header = jdbcClient.sql("""
				SELECT t.id, t.organization_id, t.origin_branch_id, ob.name AS origin_branch_name,
				       t.destination_branch_id, db.name AS destination_branch_name,
				       t.status, t.notes, t.created_at, t.updated_at, t.created_by,
				       t.shipped_at, t.shipped_by, t.received_at, t.received_by
				FROM inventory_transfer t
				JOIN branch ob ON ob.id = t.origin_branch_id
				JOIN branch db ON db.id = t.destination_branch_id
				WHERE t.organization_id = :organizationId AND t.id = :transferId
				  AND (t.origin_branch_id = :branchId OR t.destination_branch_id = :branchId)
				""")
				.param("organizationId", organizationId).param("branchId", branchId).param("transferId", transferId)
				.query((rs, rowNum) -> new InventoryTransferView(
						rs.getObject("id", UUID.class), rs.getObject("organization_id", UUID.class),
						rs.getObject("origin_branch_id", UUID.class), rs.getString("origin_branch_name"),
						rs.getObject("destination_branch_id", UUID.class), rs.getString("destination_branch_name"),
						rs.getString("status"), rs.getString("notes"),
						rs.getObject("created_at", OffsetDateTime.class), rs.getObject("updated_at", OffsetDateTime.class),
						rs.getObject("created_by", UUID.class), rs.getObject("shipped_at", OffsetDateTime.class),
						rs.getObject("shipped_by", UUID.class), rs.getObject("received_at", OffsetDateTime.class),
						rs.getObject("received_by", UUID.class), List.of()))
				.optional();
		if (header.isEmpty()) return Optional.empty();

		var items = jdbcClient.sql("""
				SELECT i.id, i.product_id, cp.name AS product_name, cp.sku,
				       i.requested_quantity, i.shipped_quantity, i.received_quantity, i.unit_cost_cents
				FROM inventory_transfer_item i
				JOIN catalog_product cp ON cp.id = i.product_id
				WHERE i.transfer_id = :transferId
				ORDER BY cp.name
				""")
				.param("transferId", transferId)
				.query((rs, rowNum) -> new InventoryTransferItemView(
						rs.getObject("id", UUID.class), rs.getObject("product_id", UUID.class),
						rs.getString("product_name"), rs.getString("sku"),
						rs.getInt("requested_quantity"), rs.getObject("shipped_quantity", Integer.class),
						rs.getObject("received_quantity", Integer.class), rs.getObject("unit_cost_cents", Long.class)))
				.list();

		InventoryTransferView h = header.get();
		return Optional.of(new InventoryTransferView(
				h.id(), h.organizationId(), h.originBranchId(), h.originBranchName(),
				h.destinationBranchId(), h.destinationBranchName(), h.status(), h.notes(),
				h.createdAt(), h.updatedAt(), h.createdBy(), h.shippedAt(), h.shippedBy(),
				h.receivedAt(), h.receivedBy(), items));
	}

	UUID create(UUID organizationId, UUID originBranchId, UUID destinationBranchId, String notes, UUID actorUserId, OffsetDateTime now) {
		UUID id = UUID.randomUUID();
		jdbcClient.sql("""
				INSERT INTO inventory_transfer (
				 id, organization_id, origin_branch_id, destination_branch_id, status, notes, created_at, updated_at, created_by, version
				) VALUES (
				 :id, :organizationId, :originBranchId, :destinationBranchId, 'REQUESTED', :notes, :now, :now, :actorUserId, 0
				)
				""")
				.param("id", id).param("organizationId", organizationId)
				.param("originBranchId", originBranchId).param("destinationBranchId", destinationBranchId)
				.param("notes", notes).param("now", now).param("actorUserId", actorUserId).update();
		return id;
	}

	void addItem(UUID transferId, UUID productId, int requestedQuantity, Long unitCostCents) {
		jdbcClient.sql("""
				INSERT INTO inventory_transfer_item (
				 id, transfer_id, product_id, requested_quantity, unit_cost_cents
				) VALUES (
				 :id, :transferId, :productId, :requestedQuantity, :unitCostCents
				)
				""")
				.param("id", UUID.randomUUID()).param("transferId", transferId)
				.param("productId", productId).param("requestedQuantity", requestedQuantity)
				.param("unitCostCents", unitCostCents).update();
	}

	void updateStatus(UUID organizationId, UUID transferId, String status, UUID actorUserId, OffsetDateTime now) {
		boolean isShipped = "SHIPPED".equals(status);
		boolean isReceived = "RECEIVED".equals(status);
		jdbcClient.sql("""
				UPDATE inventory_transfer
				SET status = :status,
				    updated_at = :now,
				    shipped_at = CASE WHEN :isShipped THEN :now ELSE shipped_at END,
				    shipped_by = CASE WHEN :isShipped THEN :actorUserId ELSE shipped_by END,
				    received_at = CASE WHEN :isReceived THEN :now ELSE received_at END,
				    received_by = CASE WHEN :isReceived THEN :actorUserId ELSE received_by END,
				    version = version + 1
				WHERE id = :id AND organization_id = :organizationId
				""")
				.param("status", status).param("now", now).param("isShipped", isShipped).param("isReceived", isReceived)
				.param("actorUserId", actorUserId).param("id", transferId).param("organizationId", organizationId).update();
	}

	void updateTransferItemQuantities(UUID transferId, UUID productId, Integer shippedQuantity, Integer receivedQuantity) {
		jdbcClient.sql("""
				UPDATE inventory_transfer_item
				SET shipped_quantity = COALESCE(:shippedQuantity, shipped_quantity),
				    received_quantity = COALESCE(:receivedQuantity, received_quantity)
				WHERE transfer_id = :transferId AND product_id = :productId
				""")
				.param("shippedQuantity", shippedQuantity).param("receivedQuantity", receivedQuantity)
				.param("transferId", transferId).param("productId", productId).update();
	}
}
