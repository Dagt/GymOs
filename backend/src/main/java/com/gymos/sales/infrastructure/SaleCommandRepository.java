package com.gymos.sales.infrastructure;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class SaleCommandRepository {
	private final JdbcClient jdbcClient;

	public SaleCommandRepository(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	public void createSale(SaleRecord sale) {
		jdbcClient.sql("""
				INSERT INTO sale (
				    id, organization_id, branch_id, client_id, sale_number, status,
				    subtotal_cents, discount_cents, total_cents, paid_cents, balance_cents,
				    currency, notes, sold_at, created_at, updated_at, created_by, updated_by, version,
				    idempotency_key, idempotency_request_hash, business_context
				)
				VALUES (
				    :id, :organizationId, :branchId, :clientId, :saleNumber, 'PENDING_PAYMENT',
				    :subtotalCents, :discountCents, :totalCents, 0, :totalCents,
				    :currency, :notes, :occurredAt, :occurredAt, :occurredAt, :actorUserId, :actorUserId, 0,
				    :idempotencyKey, :idempotencyRequestHash, 'RETAIL_SALE'
				)
				""")
				.param("id", sale.id())
				.param("organizationId", sale.organizationId())
				.param("branchId", sale.branchId())
				.param("clientId", sale.clientId())
				.param("saleNumber", sale.saleNumber())
				.param("subtotalCents", sale.subtotalCents())
				.param("discountCents", sale.discountCents())
				.param("totalCents", sale.totalCents())
				.param("currency", sale.currency())
				.param("notes", sale.notes())
				.param("occurredAt", sale.occurredAt())
				.param("actorUserId", sale.actorUserId())
				.param("idempotencyKey", sale.idempotencyKey())
				.param("idempotencyRequestHash", sale.idempotencyRequestHash())
				.update();
	}

	public void createItems(UUID organizationId, UUID saleId, List<SaleItemRecord> items, OffsetDateTime createdAt) {
		if (items.isEmpty()) {
			return;
		}
		String values = IntStream.range(0, items.size())
				.mapToObj(index -> "(:id" + index + ", :saleId, :organizationId, :productId" + index
						+ ", :itemType" + index + ", :name" + index + ", :quantity" + index
						+ ", :unitPrice" + index + ", :discount" + index + ", :total" + index + ", :createdAt)")
				.collect(java.util.stream.Collectors.joining(",\n"));
		JdbcClient.StatementSpec statement = jdbcClient.sql("""
				INSERT INTO sale_item (
				    id, sale_id, organization_id, product_id, item_type, name, quantity,
				    unit_price_cents, discount_cents, total_cents, created_at
				)
				VALUES
				""" + values)
				.param("saleId", saleId)
				.param("organizationId", organizationId)
				.param("createdAt", createdAt);
		for (int index = 0; index < items.size(); index++) {
			SaleItemRecord item = items.get(index);
			statement = statement
					.param("id" + index, UUID.randomUUID())
					.param("productId" + index, item.productId())
					.param("itemType" + index, item.itemType())
					.param("name" + index, item.name())
					.param("quantity" + index, item.quantity())
					.param("unitPrice" + index, item.unitPriceCents())
					.param("discount" + index, item.discountCents())
					.param("total" + index, item.totalCents());
		}
		statement.update();
	}

	public boolean cancelUnpaidRetailSale(UUID organizationId, UUID branchId, UUID saleId, UUID actor, String reason) {
		return jdbcClient.sql("""
				UPDATE sale s SET status = 'CANCELLED', cancellation_reason = :reason, balance_cents = 0,
				    updated_at = now(), updated_by = :actor, version = version + 1
				WHERE s.organization_id = :organizationId AND s.branch_id = :branchId AND s.id = :saleId
				  AND s.status = 'PENDING_PAYMENT' AND s.paid_cents = 0 AND s.payment_completed_at IS NULL
				  AND COALESCE(s.business_context, 'RETAIL_SALE') = 'RETAIL_SALE' AND s.deleted_at IS NULL
				  AND NOT EXISTS (
				    SELECT 1 FROM payment p WHERE p.organization_id = s.organization_id AND p.sale_id = s.id
				    AND p.status <> 'REJECTED' AND p.deleted_at IS NULL
				  )
				""").param("organizationId", organizationId).param("branchId", branchId).param("saleId", saleId)
				.param("actor", actor).param("reason", reason).update() == 1;
	}

	public java.util.Optional<UUID> initialPaymentId(UUID organizationId, UUID saleId) {
		return jdbcClient.sql("""
				SELECT initial_payment_id FROM sale WHERE organization_id = :organizationId AND id = :saleId
				  AND deleted_at IS NULL AND initial_payment_id IS NOT NULL
				""").param("organizationId", organizationId).param("saleId", saleId).query(UUID.class).optional();
	}

	public void setInitialPayment(UUID organizationId, UUID saleId, UUID paymentId) {
		jdbcClient.sql("UPDATE sale SET initial_payment_id = :paymentId WHERE organization_id = :organizationId AND id = :saleId")
				.param("paymentId", paymentId).param("organizationId", organizationId).param("saleId", saleId).update();
	}

	public boolean sameCancellation(UUID organizationId, UUID branchId, UUID saleId, String reason) {
		return jdbcClient.sql("""
				SELECT EXISTS (SELECT 1 FROM sale WHERE organization_id = :organizationId AND branch_id = :branchId
				  AND id = :saleId AND status = 'CANCELLED' AND cancellation_reason = :reason AND deleted_at IS NULL)
				""").param("organizationId", organizationId).param("branchId", branchId).param("saleId", saleId)
				.param("reason", reason).query(Boolean.class).single();
	}

	public record SaleRecord(
			UUID id,
			UUID organizationId,
			UUID branchId,
			UUID clientId,
			String saleNumber,
			long subtotalCents,
			long discountCents,
			long totalCents,
			String currency,
			String notes,
			OffsetDateTime occurredAt,
			UUID actorUserId,
			String idempotencyKey,
			String idempotencyRequestHash
	) {
	}

	public record SaleItemRecord(
			UUID productId,
			String itemType,
			String name,
			int quantity,
			long unitPriceCents,
			long discountCents,
			long totalCents
	) {
	}
}
