package com.gymos.sales.infrastructure;

import com.gymos.sales.model.PaymentDetail;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class SalesPaymentLookupRepository {
	private final JdbcClient jdbcClient;

	SalesPaymentLookupRepository(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	Optional<PaymentDetail> payment(UUID organizationId, UUID paymentId) {
		return jdbcClient.sql("""
				SELECT id, method, status, amount_cents, currency, reference,
				       receipt_object_key, received_at, validated_at, notes
				FROM payment
				WHERE organization_id = :organizationId
				  AND id = :paymentId
				  AND deleted_at IS NULL
				""")
				.param("organizationId", organizationId)
				.param("paymentId", paymentId)
				.query(SalesRowMappers::paymentDetail)
				.optional();
	}

	Optional<UUID> saleIdForPayment(UUID organizationId, UUID paymentId) {
		return jdbcClient.sql("""
				SELECT sale_id
				FROM payment
				WHERE organization_id = :organizationId
				  AND id = :paymentId
				  AND deleted_at IS NULL
				""")
				.param("organizationId", organizationId)
				.param("paymentId", paymentId)
				.query(UUID.class)
				.optional();
	}
}
