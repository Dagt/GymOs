package com.gymos.sales.infrastructure;

import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class SalesFinancialIntegrityRepository {
	private final JdbcClient jdbcClient;

	public SalesFinancialIntegrityRepository(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	public Optional<UUID> paymentSaleByIdempotencyKey(UUID organizationId, String idempotencyKey) {
		return jdbcClient.sql("""
				SELECT sale_id
				FROM payment
				WHERE organization_id = :organizationId
				  AND idempotency_key = :idempotencyKey
				  AND deleted_at IS NULL
				""")
				.param("organizationId", organizationId)
				.param("idempotencyKey", idempotencyKey)
				.query(UUID.class)
				.optional();
	}

	public long processedRefundTotalForPayment(UUID organizationId, UUID paymentId) {
		Long total = jdbcClient.sql("""
				SELECT COALESCE(SUM(amount_cents), 0)
				FROM sale_refund
				WHERE organization_id = :organizationId
				  AND payment_id = :paymentId
				  AND status = 'PROCESSED'
				""")
				.param("organizationId", organizationId)
				.param("paymentId", paymentId)
				.query(Long.class)
				.single();
		return total == null ? 0 : total;
	}
}
