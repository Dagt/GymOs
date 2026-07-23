package com.gymos.memberships.infrastructure;

import com.gymos.memberships.infrastructure.MembershipRepository.PlanForRenewal;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class MembershipPlanLookupRepository {
	private final JdbcClient jdbcClient;

	MembershipPlanLookupRepository(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	Optional<PlanForRenewal> forRenewal(UUID organizationId, UUID planId) {
		return jdbcClient.sql("""
				SELECT mp.id AS plan_id, mp.product_id, cp.name AS plan_name,
				       mp.duration_days, mp.grace_days, mp.multi_branch_access, cp.price_cents, cp.currency
				FROM membership_plan mp
				JOIN catalog_product cp ON cp.id = mp.product_id
				WHERE mp.organization_id = :organizationId
				  AND mp.id = :planId
				  AND mp.status = 'ACTIVE'
				  AND mp.deleted_at IS NULL
				  AND cp.status = 'ACTIVE'
				  AND cp.deleted_at IS NULL
				""")
				.param("organizationId", organizationId)
				.param("planId", planId)
				.query(MembershipRowMappers::planForRenewal)
				.optional();
	}

	Optional<PlanForRenewal> forProduct(UUID organizationId, UUID productId) {
		return jdbcClient.sql("""
				SELECT mp.id AS plan_id, mp.product_id, cp.name AS plan_name,
				       mp.duration_days, mp.grace_days, mp.multi_branch_access, cp.price_cents, cp.currency
				FROM membership_plan mp
				JOIN catalog_product cp ON cp.id = mp.product_id
				WHERE mp.organization_id = :organizationId
				  AND mp.product_id = :productId
				  AND mp.status = 'ACTIVE'
				  AND mp.deleted_at IS NULL
				  AND cp.status = 'ACTIVE'
				  AND cp.deleted_at IS NULL
				""")
				.param("organizationId", organizationId)
				.param("productId", productId)
				.query(MembershipRowMappers::planForRenewal)
				.optional();
	}
}
