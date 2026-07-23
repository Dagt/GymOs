package com.gymos.inventory.infrastructure;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class InventoryMovementCommandRepository {
	private final JdbcClient jdbcClient;

	InventoryMovementCommandRepository(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	UUID createMovement(UUID organizationId, UUID branchId, UUID stockId, UUID productId,
			String movementType, int quantityDelta, int quantityBefore, int quantityAfter,
			Long unitCostCents, String referenceType, UUID referenceId, String reason,
			OffsetDateTime occurredAt, UUID actorUserId, UUID correlationId) {
		UUID movementId = UUID.randomUUID();
		jdbcClient.sql("""
				INSERT INTO inventory_movement (
				 id, organization_id, branch_id, stock_id, product_id, movement_type,
				 quantity_delta, quantity_before, quantity_after, unit_cost_cents,
				 reference_type, reference_id, reason, occurred_at, created_at, created_by, correlation_id)
				VALUES (
				 :id, :organizationId, :branchId, :stockId, :productId, :movementType,
				 :quantityDelta, :quantityBefore, :quantityAfter, :unitCostCents,
				 :referenceType, :referenceId, :reason, :occurredAt, :occurredAt, :actorUserId, :correlationId)
				""")
				.param("id", movementId).param("organizationId", organizationId).param("branchId", branchId)
				.param("stockId", stockId).param("productId", productId).param("movementType", movementType)
				.param("quantityDelta", quantityDelta).param("quantityBefore", quantityBefore)
				.param("quantityAfter", quantityAfter).param("unitCostCents", unitCostCents)
				.param("referenceType", referenceType).param("referenceId", referenceId).param("reason", reason)
				.param("occurredAt", occurredAt).param("actorUserId", actorUserId).param("correlationId", correlationId)
				.update();
		return movementId;
	}
}
