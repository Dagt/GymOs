package com.gymos.memberships.infrastructure;

import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class MembershipClientRepository {
	private final JdbcClient jdbcClient;

	MembershipClientRepository(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	boolean exists(UUID organizationId, UUID clientId) {
		Integer count = jdbcClient.sql("""
				SELECT COUNT(*)
				FROM client
				WHERE organization_id = :organizationId
				  AND id = :clientId
				  AND deleted_at IS NULL
				""")
				.param("organizationId", organizationId)
				.param("clientId", clientId)
				.query(Integer.class)
				.single();
		return count > 0;
	}
}
