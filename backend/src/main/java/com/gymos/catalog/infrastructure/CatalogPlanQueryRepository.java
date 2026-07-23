package com.gymos.catalog.infrastructure;

import com.gymos.catalog.application.query.PlanView;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class CatalogPlanQueryRepository {
	private final JdbcClient jdbcClient;

	CatalogPlanQueryRepository(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	List<PlanView> list(UUID organizationId) {
		return jdbcClient.sql("""
				SELECT mp.id, mp.product_id, cp.sku, cp.name, cp.price_cents, cp.currency,
				       mp.duration_days, mp.grace_days, mp.benefits_json::text AS benefits_json,
				       mp.multi_branch_access, mp.status, mp.created_at, mp.updated_at
				FROM membership_plan mp
				JOIN catalog_product cp ON cp.id = mp.product_id
				WHERE mp.organization_id = :organizationId
				  AND mp.deleted_at IS NULL
				  AND cp.deleted_at IS NULL
				ORDER BY cp.name
				""")
				.param("organizationId", organizationId)
				.query(CatalogRowMappers.planSummary())
				.list();
	}

	Optional<PlanView> find(UUID organizationId, UUID planId) {
		return jdbcClient.sql("""
				SELECT mp.id, mp.product_id, cp.sku, cp.name, cp.price_cents, cp.currency,
				       mp.duration_days, mp.grace_days, mp.benefits_json::text AS benefits_json,
				       mp.multi_branch_access, mp.status, mp.created_at, mp.updated_at
				FROM membership_plan mp
				JOIN catalog_product cp ON cp.id = mp.product_id
				WHERE mp.organization_id = :organizationId
				  AND mp.id = :planId
				  AND mp.deleted_at IS NULL
				  AND cp.deleted_at IS NULL
				""")
				.param("organizationId", organizationId)
				.param("planId", planId)
				.query(CatalogRowMappers.planSummary())
				.optional();
	}
}
