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
				    idempotency_key, idempotency_request_hash
				)
				VALUES (
				    :id, :organizationId, :branchId, :clientId, :saleNumber, 'PENDING_PAYMENT',
				    :subtotalCents, :discountCents, :totalCents, 0, :totalCents,
				    :currency, :notes, :occurredAt, :occurredAt, :occurredAt, :actorUserId, :actorUserId, 0,
				    :idempotencyKey, :idempotencyRequestHash
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
