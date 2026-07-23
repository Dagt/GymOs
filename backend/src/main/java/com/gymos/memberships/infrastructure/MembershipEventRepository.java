package com.gymos.memberships.infrastructure;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class MembershipEventRepository {
	private final JdbcClient jdbcClient;

	MembershipEventRepository(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	boolean markProcessed(UUID organizationId, String eventKey, String eventType) {
		try {
			jdbcClient.sql("""
					INSERT INTO processed_domain_event (id, organization_id, event_key, event_type, processed_at, created_at)
					VALUES (:id, :organizationId, :eventKey, :eventType, :processedAt, :createdAt)
					""")
					.param("id", UUID.randomUUID())
					.param("organizationId", organizationId)
					.param("eventKey", eventKey)
					.param("eventType", eventType)
					.param("processedAt", OffsetDateTime.now())
					.param("createdAt", OffsetDateTime.now())
					.update();
			return true;
		} catch (DuplicateKeyException exception) {
			return false;
		}
	}
}
