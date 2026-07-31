package com.gymos.bookings.infrastructure;

import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Validates resource configuration. Client eligibility is owned by the
 * transversal eligibility policy.
 */
@Repository
public class BookingPlanRepository {
	private final JdbcClient jdbcClient;

	public BookingPlanRepository(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	public int activePlanCount(UUID organizationId, List<UUID> planIds) {
		if (planIds == null || planIds.isEmpty()) {
			return 0;
		}
		return jdbcClient.sql("""
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
	}
}
