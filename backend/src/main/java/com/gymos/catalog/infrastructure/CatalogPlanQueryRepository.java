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
		return list(organizationId, null);
	}

	List<PlanView> list(UUID organizationId, UUID branchId) {
		String availability = availability(branchId);
		JdbcClient.StatementSpec spec = jdbcClient.sql("""
				SELECT mp.id, mp.product_id, cp.sku, cp.name, cp.price_cents, cp.currency,
				       mp.duration_days, mp.grace_days, mp.benefits_json::text AS benefits_json,
				       mp.multi_branch_access, mp.status, mp.created_at, mp.updated_at
				FROM membership_plan mp
				JOIN catalog_product cp ON cp.id = mp.product_id
				WHERE mp.organization_id = :organizationId
				  AND mp.deleted_at IS NULL
				  AND cp.deleted_at IS NULL
				""" + availability + " ORDER BY cp.name")
				.param("organizationId", organizationId);
		if (branchId != null) spec = spec.param("branchId", branchId);
		return spec.query(CatalogRowMappers.planSummary()).list();
	}

	Optional<PlanView> find(UUID organizationId, UUID planId) {
		return find(organizationId, null, planId);
	}

	Optional<PlanView> find(UUID organizationId, UUID branchId, UUID planId) {
		String availability = availability(branchId);
		JdbcClient.StatementSpec spec = jdbcClient.sql("""
				SELECT mp.id, mp.product_id, cp.sku, cp.name, cp.price_cents, cp.currency,
				       mp.duration_days, mp.grace_days, mp.benefits_json::text AS benefits_json,
				       mp.multi_branch_access, mp.status, mp.created_at, mp.updated_at
				FROM membership_plan mp
				JOIN catalog_product cp ON cp.id = mp.product_id
				WHERE mp.organization_id = :organizationId
				  AND mp.id = :planId
				  AND mp.deleted_at IS NULL
				  AND cp.deleted_at IS NULL
				""" + availability)
				.param("organizationId", organizationId)
				.param("planId", planId);
		if (branchId != null) spec = spec.param("branchId", branchId);
		return spec.query(CatalogRowMappers.planSummary()).optional();
	}

	Optional<PlanView> findByProduct(UUID organizationId, UUID productId) {
		return jdbcClient.sql("""
				SELECT mp.id, mp.product_id, cp.sku, cp.name, cp.price_cents, cp.currency,
				       mp.duration_days, mp.grace_days, mp.benefits_json::text AS benefits_json,
				       mp.multi_branch_access, mp.status, mp.created_at, mp.updated_at
				FROM membership_plan mp
				JOIN catalog_product cp ON cp.id = mp.product_id
				WHERE mp.organization_id = :organizationId
				  AND mp.product_id = :productId
				  AND mp.deleted_at IS NULL
				  AND cp.deleted_at IS NULL
				""")
				.param("organizationId", organizationId)
				.param("productId", productId)
				.query(CatalogRowMappers.planSummary())
				.optional();
	}

	private String availability(UUID branchId) {
		if (branchId == null) return "";
		return " AND EXISTS (SELECT 1 FROM catalog_product_branch cpb"
				+ " WHERE cpb.organization_id=mp.organization_id AND cpb.product_id=mp.product_id"
				+ " AND cpb.branch_id=:branchId)";
	}
}
