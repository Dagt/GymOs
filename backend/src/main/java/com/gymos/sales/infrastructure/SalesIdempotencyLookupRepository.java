package com.gymos.sales.infrastructure;

import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class SalesIdempotencyLookupRepository {
	private final JdbcClient jdbcClient;

	SalesIdempotencyLookupRepository(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	Optional<String> saleRequestHashByIdempotencyKey(UUID organizationId, String idempotencyKey) {
		return idempotencyHash("sale", organizationId, idempotencyKey);
	}

	Optional<String> paymentRequestHashByIdempotencyKey(UUID organizationId, String idempotencyKey) {
		return idempotencyHash("payment", organizationId, idempotencyKey);
	}

	private Optional<String> idempotencyHash(String table, UUID organizationId, String idempotencyKey) {
		return jdbcClient.sql("SELECT idempotency_request_hash FROM " + table + " WHERE organization_id = :organizationId AND idempotency_key = :idempotencyKey AND deleted_at IS NULL")
				.param("organizationId", organizationId)
				.param("idempotencyKey", idempotencyKey)
				.query(String.class)
				.optional();
	}
}
