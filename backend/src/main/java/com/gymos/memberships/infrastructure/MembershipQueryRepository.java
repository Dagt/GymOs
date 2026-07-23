package com.gymos.memberships.infrastructure;

import com.gymos.memberships.infrastructure.MembershipRepository.MembershipSummary;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class MembershipQueryRepository {
	private final JdbcClient jdbcClient;

	MembershipQueryRepository(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	List<MembershipSummary> forClient(UUID organizationId, UUID branchId, UUID clientId) {
		return jdbcClient.sql("""
				SELECT m.id, m.client_id, m.plan_id, cp.name AS plan_name,
				       m.sale_id, s.sale_number, m.status, m.activated_at,
				       m.starts_on, m.ends_on, m.grace_ends_on
				FROM membership m
				JOIN membership_plan mp ON mp.id = m.plan_id
				JOIN catalog_product cp ON cp.id = mp.product_id
				JOIN sale s ON s.id = m.sale_id
				WHERE m.organization_id = :organizationId
				  AND (m.branch_id = :branchId OR mp.multi_branch_access = true)
				  AND m.client_id = :clientId
				  AND m.deleted_at IS NULL
				ORDER BY m.starts_on DESC, m.activated_at DESC
				""")
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("clientId", clientId)
				.query(MembershipRowMappers::membershipSummary)
				.list();
	}

	Optional<MembershipSummary> byId(UUID organizationId, UUID branchId, UUID membershipId) {
		return jdbcClient.sql("""
				SELECT m.id, m.client_id, m.plan_id, cp.name AS plan_name,
				       m.sale_id, s.sale_number, m.status, m.activated_at,
				       m.starts_on, m.ends_on, m.grace_ends_on
				FROM membership m
				JOIN membership_plan mp ON mp.id = m.plan_id
				JOIN catalog_product cp ON cp.id = mp.product_id
				JOIN sale s ON s.id = m.sale_id
				WHERE m.organization_id = :organizationId
				  AND (m.branch_id = :branchId OR mp.multi_branch_access = true)
				  AND m.id = :membershipId
				  AND m.deleted_at IS NULL
				""")
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("membershipId", membershipId)
				.query(MembershipRowMappers::membershipSummary)
				.optional();
	}

	Optional<MembershipSummary> bySaleAndPlan(UUID organizationId, UUID branchId, UUID saleId, UUID planId) {
		return jdbcClient.sql("""
				SELECT m.id, m.client_id, m.plan_id, cp.name AS plan_name,
				       m.sale_id, s.sale_number, m.status, m.activated_at,
				       m.starts_on, m.ends_on, m.grace_ends_on
				FROM membership m
				JOIN membership_plan mp ON mp.id = m.plan_id
				JOIN catalog_product cp ON cp.id = mp.product_id
				JOIN sale s ON s.id = m.sale_id
				WHERE m.organization_id = :organizationId
				  AND m.branch_id = :branchId
				  AND m.sale_id = :saleId
				  AND m.plan_id = :planId
				  AND m.deleted_at IS NULL
				""")
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("saleId", saleId)
				.param("planId", planId)
				.query(MembershipRowMappers::membershipSummary)
				.optional();
	}
}
