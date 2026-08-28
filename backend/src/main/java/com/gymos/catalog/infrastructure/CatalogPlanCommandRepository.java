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
				    multi_branch_access, self_service_acquisition_enabled, registration_fee_policy,
				    status, created_at, updated_at, created_by, updated_by, version
				)
				VALUES (
				    :id, :organizationId, :productId, :durationDays, :graceDays, CAST(:benefitsJson AS jsonb),
				    :multiBranchAccess, :selfServiceAcquisitionEnabled, :registrationFeePolicy,
				    :status, :now, :now, :userId, :userId, 0
				)
				""")
				.param("id", plan.planId()).param("organizationId", plan.organizationId())
				.param("productId", plan.productId()).param("durationDays", plan.durationDays())
				.param("graceDays", plan.graceDays()).param("benefitsJson", plan.benefitsJson())
				.param("multiBranchAccess", plan.multiBranchAccess())
				.param("selfServiceAcquisitionEnabled", plan.selfServiceAcquisitionEnabled())
				.param("registrationFeePolicy", plan.registrationFeePolicy()).param("status", plan.status())
				.param("now", plan.now()).param("userId", plan.userId()).update();
	}

	@Override
	public void update(PlanMutation plan) {
		jdbcClient.sql("""
				UPDATE membership_plan
				SET duration_days = :durationDays, grace_days = :graceDays,
				    benefits_json = CAST(:benefitsJson AS jsonb), multi_branch_access = :multiBranchAccess,
				    self_service_acquisition_enabled = :selfServiceAcquisitionEnabled,
				    registration_fee_policy = :registrationFeePolicy,
				    status = :status, updated_at = :now, updated_by = :userId, version = version + 1
				WHERE organization_id = :organizationId AND id = :id AND deleted_at IS NULL
				""")
				.param("durationDays", plan.durationDays()).param("graceDays", plan.graceDays())
				.param("benefitsJson", plan.benefitsJson()).param("multiBranchAccess", plan.multiBranchAccess())
				.param("selfServiceAcquisitionEnabled", plan.selfServiceAcquisitionEnabled())
				.param("registrationFeePolicy", plan.registrationFeePolicy())
				.param("status", plan.status()).param("now", plan.now()).param("userId", plan.userId())
				.param("organizationId", plan.organizationId()).param("id", plan.planId()).update();
	}
}
