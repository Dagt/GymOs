package com.gymos.selfservice.infrastructure;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class SelfServiceSessionStatusRepository {
	private final JdbcClient jdbcClient;

	public SelfServiceSessionStatusRepository(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	public Optional<SessionStatusRecord> find(UUID sessionId) {
		return jdbcClient.sql("""
				SELECT id, client_id, status, expires_at, ended_at, updated_at,
				       session_token_hash
				FROM self_service_session
				WHERE id = :sessionId
				  AND deleted_at IS NULL
				""")
				.param("sessionId", sessionId)
				.query((resultSet, rowNumber) -> new SessionStatusRecord(
						resultSet.getObject("id", UUID.class),
						resultSet.getObject("client_id", UUID.class),
						resultSet.getString("status"),
						resultSet.getObject("expires_at", OffsetDateTime.class),
						resultSet.getObject("ended_at", OffsetDateTime.class),
						resultSet.getObject("updated_at", OffsetDateTime.class),
						resultSet.getString("session_token_hash")
				))
				.optional();
	}

	public record SessionStatusRecord(
			UUID id,
			UUID clientId,
			String status,
			OffsetDateTime expiresAt,
			OffsetDateTime endedAt,
			OffsetDateTime updatedAt,
			String sessionTokenHash
	) {
		public boolean verified() {
			return clientId != null;
		}
	}
}
