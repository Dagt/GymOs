package com.gymos.sales.infrastructure;

import com.gymos.sales.model.SaleHeader;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class SalesRecordLookupRepository {
	private final JdbcClient jdbcClient;

	SalesRecordLookupRepository(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	Optional<SaleHeader> saleHeader(UUID organizationId, UUID saleId) {
		return jdbcClient.sql("""
				SELECT s.id, s.sale_number, s.branch_id, s.client_id,
				       COALESCE(p.first_name || ' ' || p.last_name, 'Venta mostrador') AS client_name,
				       s.status, COALESCE(s.business_context, 'RETAIL_SALE') AS business_context,
				       s.subtotal_cents, s.discount_cents, s.total_cents,
				       s.paid_cents, s.balance_cents, s.currency, s.notes, s.sold_at,
				       s.created_at, s.updated_at
				FROM sale s
				LEFT JOIN client c ON c.id = s.client_id
				LEFT JOIN person p ON p.id = c.person_id
				WHERE s.organization_id = :organizationId
				  AND s.id = :saleId
				  AND s.deleted_at IS NULL
				""")
				.param("organizationId", organizationId)
				.param("saleId", saleId)
				.query(SalesRowMappers::saleHeader)
				.optional();
	}

	void lockSaleForRefund(UUID organizationId, UUID saleId) {
		jdbcClient.sql("""
				SELECT id
				FROM sale
				WHERE organization_id = :organizationId
				  AND id = :saleId
				  AND deleted_at IS NULL
				FOR UPDATE
				""")
				.param("organizationId", organizationId)
				.param("saleId", saleId)
				.query(UUID.class)
				.single();
	}

	void lockIdempotency(UUID organizationId, String operation, String key) {
		jdbcClient.sql("SELECT pg_advisory_xact_lock(hashtextextended(:key, 0))")
				.param("key", "sales:" + organizationId + ":" + operation + ":" + key)
				.query((result, row) -> Boolean.TRUE).single();
	}

	Optional<SaleHeader> saleHeaderByIdempotencyKey(UUID organizationId, String idempotencyKey) {
		return jdbcClient.sql("""
				SELECT s.id, s.sale_number, s.branch_id, s.client_id,
				       COALESCE(p.first_name || ' ' || p.last_name, 'Cliente general') AS client_name,
				       s.status, COALESCE(s.business_context, 'RETAIL_SALE') AS business_context,
				       s.subtotal_cents, s.discount_cents, s.total_cents,
				       s.paid_cents, s.balance_cents, s.currency, s.notes, s.sold_at,
				       s.created_at, s.updated_at
				FROM sale s
				LEFT JOIN client c ON c.id = s.client_id
				LEFT JOIN person p ON p.id = c.person_id
				WHERE s.organization_id = :organizationId
				  AND s.idempotency_key = :idempotencyKey
				  AND s.deleted_at IS NULL
				""")
				.param("organizationId", organizationId)
				.param("idempotencyKey", idempotencyKey)
				.query(SalesRowMappers::saleHeader)
				.optional();
	}
}
