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

	public void markProviderSubmissionPrepared(UUID organizationId, UUID paymentId) {
		jdbcClient.sql("""
				UPDATE payment SET provider_submission_prepared_at = now()
				WHERE organization_id = :org AND id = :id AND provider_submission_prepared_at IS NULL
				""").param("org", organizationId).param("id", paymentId).update();
	}

	public boolean providerSubmissionMayHaveStarted(UUID organizationId, UUID paymentId) {
		return jdbcClient.sql("""
				SELECT EXISTS (SELECT 1 FROM payment p WHERE p.organization_id = :org AND p.id = :id
				  AND (p.provider_submission_prepared_at IS NOT NULL OR EXISTS (
					SELECT 1 FROM payment_provider_operation o WHERE o.organization_id = p.organization_id AND o.payment_id = p.id)))
				""").param("org", organizationId).param("id", paymentId).query(Boolean.class).single();
	}

	public boolean hasRemainingPointExposure(UUID organizationId, UUID saleId) {
		return jdbcClient.sql("""
				SELECT EXISTS (SELECT 1 FROM payment p
				 WHERE p.organization_id = :org AND p.sale_id = :sale AND p.method = 'CARD_MERCADO_PAGO'
				   AND p.deleted_at IS NULL AND (p.status = 'COMPLETED' OR (p.status = 'PENDING_VALIDATION' AND
				     (p.provider_submission_prepared_at IS NOT NULL OR EXISTS (SELECT 1 FROM payment_provider_operation o
				       WHERE o.organization_id = p.organization_id AND o.payment_id = p.id)))))
				""").param("org", organizationId).param("sale", saleId).query(Boolean.class).single();
	}

	public boolean hasCompletedProviderPayment(UUID organizationId, UUID saleId) {
		return jdbcClient.sql("""
				SELECT EXISTS (SELECT 1 FROM payment
				 WHERE organization_id = :organizationId AND sale_id = :saleId AND status = 'COMPLETED'
				   AND method IN ('CARD_ONLINE', 'CARD_MERCADO_PAGO') AND deleted_at IS NULL)
				""").param("organizationId", organizationId).param("saleId", saleId).query(Boolean.class).single();
	}

	public long pendingPaymentTotal(UUID organizationId, UUID saleId) {
		return jdbcClient.sql("""
				SELECT COALESCE(SUM(amount_cents), 0) FROM payment
				WHERE organization_id = :organizationId AND sale_id = :saleId
				  AND status = 'PENDING_VALIDATION' AND deleted_at IS NULL
				""")
				.param("organizationId", organizationId).param("saleId", saleId).query(Long.class).single();
	}
}
