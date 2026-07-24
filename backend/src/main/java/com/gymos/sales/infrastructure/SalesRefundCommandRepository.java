package com.gymos.sales.infrastructure;

import com.gymos.sales.application.command.RefundItemCommand;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class SalesRefundCommandRepository {
	private final JdbcClient jdbcClient;

	public SalesRefundCommandRepository(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	public UUID createProcessed(
			UUID organizationId,
			UUID branchId,
			UUID saleId,
			UUID paymentId,
			long amountCents,
			String currency,
			String reason,
			String idempotencyKey,
			UUID requestedBy
	) {
		OffsetDateTime now = OffsetDateTime.now();
		UUID refundId = UUID.randomUUID();
		jdbcClient.sql("""
				INSERT INTO sale_refund (
				    id, organization_id, branch_id, sale_id, payment_id, status, amount_cents,
				    currency, reason, idempotency_key, requested_by, requested_at, processed_at,
				    created_at, updated_at, version
				)
				VALUES (
				    :id, :organizationId, :branchId, :saleId, :paymentId, 'PROCESSED', :amountCents,
				    :currency, :reason, :idempotencyKey, :requestedBy, :now, :now, :now, :now, 0
				)
				""")
				.param("id", refundId)
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("saleId", saleId)
				.param("paymentId", paymentId)
				.param("amountCents", amountCents)
				.param("currency", currency)
				.param("reason", reason)
				.param("idempotencyKey", idempotencyKey)
				.param("requestedBy", requestedBy)
				.param("now", now)
				.update();
		return refundId;
	}

	public void createItems(UUID organizationId, UUID branchId, UUID saleId, UUID refundId,
			List<RefundItemCommand> items, UUID requestedBy) {
		OffsetDateTime now = OffsetDateTime.now();
		for (RefundItemCommand item : items) {
			int inserted = jdbcClient.sql("""
					INSERT INTO sale_refund_item (
					    id, organization_id, branch_id, sale_id, refund_id, sale_item_id,
					    product_id, quantity, restock, created_at, created_by
					)
					SELECT :id, :organizationId, :branchId, :saleId, :refundId, si.id,
					       si.product_id, :quantity, :restock, :now, :requestedBy
					FROM sale_item si
					WHERE si.organization_id = :organizationId
					  AND si.sale_id = :saleId
					  AND si.id = :saleItemId
					""")
					.param("id", UUID.randomUUID())
					.param("organizationId", organizationId)
					.param("branchId", branchId)
					.param("saleId", saleId)
					.param("refundId", refundId)
					.param("saleItemId", item.saleItemId())
					.param("quantity", item.quantity())
					.param("restock", item.restock())
					.param("now", now)
					.param("requestedBy", requestedBy)
					.update();
			if (inserted != 1) {
				throw new IllegalStateException("No fue posible persistir el producto devuelto.");
			}
		}
	}
}
