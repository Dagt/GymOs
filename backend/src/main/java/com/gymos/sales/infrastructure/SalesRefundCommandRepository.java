package com.gymos.sales.infrastructure;

import java.time.OffsetDateTime;
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
}
