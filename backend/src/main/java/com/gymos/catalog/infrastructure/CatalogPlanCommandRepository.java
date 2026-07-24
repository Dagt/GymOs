package com.gymos.catalog.infrastructure;

import com.gymos.catalog.application.port.CatalogPlanCommandPort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class CatalogPlanCommandRepository implements CatalogPlanCommandPort {
	private final JdbcClient jdbcClient;

	public CatalogPlanCommandRepository(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	@Override
	public void create(PlanMutation plan) {
		jdbcClient.sql("""
				INSERT INTO membership_plan (
				    id, organization_id, product_id, duration_days, grace_days, benefits_json,
				    multi_branch_access, status, created_at, updated_at, created_by, updated_by, version
				)
				VALUES (
				    :id, :organizationId, :productId, :durationDays, :graceDays, CAST(:benefitsJson AS jsonb),
				    :multiBranchAccess, :status, :now, :now, :userId, :userId, 0
				)
				""")
				.param("id", plan.planId()).param("organizationId", plan.organizationId())
				.param("productId", plan.productId()).param("durationDays", plan.durationDays())
				.param("graceDays", plan.graceDays()).param("benefitsJson", plan.benefitsJson())
				.param("multiBranchAccess", plan.multiBranchAccess()).param("status", plan.status())
				.param("now", plan.now()).param("userId", plan.userId()).update();
	}

	@Override
	public void update(PlanMutation plan) {
		jdbcClient.sql("""
				UPDATE membership_plan
				SET duration_days = :durationDays, grace_days = :graceDays,
				    benefits_json = CAST(:benefitsJson AS jsonb), multi_branch_access = :multiBranchAccess,
				    status = :status, updated_at = :now, updated_by = :userId, version = version + 1
				WHERE organization_id = :organizationId AND id = :id AND deleted_at IS NULL
				""")
				.param("durationDays", plan.durationDays()).param("graceDays", plan.graceDays())
				.param("benefitsJson", plan.benefitsJson()).param("multiBranchAccess", plan.multiBranchAccess())
				.param("status", plan.status()).param("now", plan.now()).param("userId", plan.userId())
				.param("organizationId", plan.organizationId()).param("id", plan.planId()).update();
	}
}
