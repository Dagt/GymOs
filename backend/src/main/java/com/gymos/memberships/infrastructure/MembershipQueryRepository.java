package com.gymos.memberships.infrastructure;

import com.gymos.memberships.infrastructure.MembershipRepository.MembershipSummary;
import com.gymos.memberships.infrastructure.MembershipRepository.MembershipPeriodSummary;
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

	Optional<MembershipSummary> byIdForUpdate(UUID organizationId, UUID branchId, UUID membershipId) {
		Optional<UUID> lockedId = jdbcClient.sql("""
				SELECT m.id
				FROM membership m
				JOIN membership_plan mp ON mp.id = m.plan_id
				WHERE m.organization_id = :organizationId
				  AND (m.branch_id = :branchId OR mp.multi_branch_access = true)
				  AND m.id = :membershipId
				  AND m.deleted_at IS NULL
				FOR UPDATE OF m
				""")
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("membershipId", membershipId)
				.query(UUID.class)
				.optional();
		return lockedId.isEmpty()
				? Optional.empty()
				: byId(organizationId, branchId, membershipId);
	}

	List<MembershipPeriodSummary> periods(UUID organizationId, UUID branchId, UUID membershipId) {
		return jdbcClient.sql("""
				SELECT period.id, period.membership_id, period.plan_id, cp.name AS plan_name,
				       period.sale_id, sale.sale_number, period.operation_type,
				       period.starts_on, period.ends_on, period.created_at
				FROM membership_period period
				JOIN membership membership ON membership.id = period.membership_id
				JOIN membership_plan plan ON plan.id = period.plan_id
				JOIN catalog_product cp ON cp.id = plan.product_id
				JOIN sale sale ON sale.id = period.sale_id
				WHERE period.organization_id = :organizationId
				  AND period.membership_id = :membershipId
				  AND (membership.branch_id = :branchId OR plan.multi_branch_access = true)
				  AND membership.deleted_at IS NULL
				ORDER BY period.created_at DESC, period.id DESC
				""")
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("membershipId", membershipId)
				.query((resultSet, rowNumber) -> new MembershipPeriodSummary(
						resultSet.getObject("id", UUID.class),
						resultSet.getObject("membership_id", UUID.class),
						resultSet.getObject("plan_id", UUID.class),
						resultSet.getString("plan_name"),
						resultSet.getObject("sale_id", UUID.class),
						resultSet.getString("sale_number"),
						resultSet.getString("operation_type"),
						resultSet.getObject("starts_on", java.time.LocalDate.class),
						resultSet.getObject("ends_on", java.time.LocalDate.class),
						resultSet.getObject("created_at", java.time.OffsetDateTime.class)
				))
				.list();
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
