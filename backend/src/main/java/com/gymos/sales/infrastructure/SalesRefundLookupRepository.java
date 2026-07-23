package com.gymos.sales.infrastructure;

import com.gymos.sales.model.RefundDetail;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class SalesRefundLookupRepository {
	private final JdbcClient jdbcClient;

	SalesRefundLookupRepository(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	Optional<RefundDetail> byIdempotencyKey(UUID organizationId, String idempotencyKey) {
		return jdbcClient.sql("""
				SELECT id, sale_id, payment_id, status, amount_cents, currency, reason,
				       idempotency_key, requested_by, requested_at, processed_at, created_at, updated_at
				FROM sale_refund
				WHERE organization_id = :organizationId
				  AND idempotency_key = :idempotencyKey
				""")
				.param("organizationId", organizationId)
				.param("idempotencyKey", idempotencyKey)
				.query(SalesRowMappers::refundDetail)
				.optional();
	}

	long processedTotal(UUID organizationId, UUID saleId) {
		Long total = jdbcClient.sql("""
				SELECT COALESCE(SUM(amount_cents), 0)
				FROM sale_refund
				WHERE organization_id = :organizationId
				  AND sale_id = :saleId
				  AND status = 'PROCESSED'
				""")
				.param("organizationId", organizationId)
				.param("saleId", saleId)
				.query(Long.class)
				.single();
		return total == null ? 0 : total;
	}
}
