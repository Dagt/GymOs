package com.gymos.bookings.infrastructure;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class BookingEligibilityRepository {
	private final JdbcClient jdbcClient;

	public BookingEligibilityRepository(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	public int activePlanCount(UUID organizationId, List<UUID> planIds) {
		if (planIds == null || planIds.isEmpty()) {
			return 0;
		}
		Integer count = jdbcClient.sql("""
				SELECT COUNT(*)
				FROM membership_plan mp
				JOIN catalog_product cp ON cp.id = mp.product_id
				WHERE mp.organization_id = :organizationId
				  AND mp.id IN (:planIds)
				  AND mp.status = 'ACTIVE'
				  AND cp.status = 'ACTIVE'
				  AND mp.deleted_at IS NULL
				  AND cp.deleted_at IS NULL
				""")
				.param("organizationId", organizationId)
				.param("planIds", planIds)
				.query(Integer.class)
				.single();
		return count;
	}

	public boolean clientExists(UUID organizationId, UUID clientId) {
		Integer count = jdbcClient.sql("""
				SELECT COUNT(*)
				FROM client
				WHERE organization_id = :organizationId
				  AND id = :clientId
				  AND status = 'ACTIVE'
				  AND deleted_at IS NULL
				""")
				.param("organizationId", organizationId)
				.param("clientId", clientId)
				.query(Integer.class)
				.single();
		return count > 0;
	}

	public boolean hasEligibleMembershipForResource(
			UUID organizationId,
			UUID branchId,
			UUID resourceId,
			UUID clientId,
			LocalDate bookingDate
	) {
		Integer restrictions = jdbcClient.sql("""
				SELECT COUNT(*)
				FROM resource_allowed_membership_plan
				WHERE organization_id = :organizationId
				  AND resource_id = :resourceId
				""")
				.param("organizationId", organizationId)
				.param("resourceId", resourceId)
				.query(Integer.class)
				.single();
		if (restrictions == 0) {
			return true;
		}
		Integer count = jdbcClient.sql("""
				SELECT COUNT(*)
				FROM membership m
				JOIN membership_plan mp ON mp.id = m.plan_id
				JOIN resource_allowed_membership_plan rap ON rap.plan_id = m.plan_id
				WHERE m.organization_id = :organizationId
				  AND rap.organization_id = :organizationId
				  AND rap.resource_id = :resourceId
				  AND m.client_id = :clientId
				  AND (m.branch_id = :branchId OR mp.multi_branch_access = true)
				  AND m.status = 'ACTIVE'
				  AND m.starts_on <= :bookingDate
				  AND m.grace_ends_on >= :bookingDate
				  AND m.deleted_at IS NULL
				""")
				.param("organizationId", organizationId)
				.param("resourceId", resourceId)
				.param("clientId", clientId)
				.param("branchId", branchId)
				.param("bookingDate", bookingDate)
				.query(Integer.class)
				.single();
		return count > 0;
	}
}
