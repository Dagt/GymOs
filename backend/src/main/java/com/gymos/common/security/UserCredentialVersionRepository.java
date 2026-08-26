package com.gymos.common.security;

import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Single primary-key lookup; no cache so password recovery invalidates JWTs immediately. */
@Repository
public class UserCredentialVersionRepository {
	private final JdbcClient jdbc;
	public UserCredentialVersionRepository(JdbcClient jdbc) { this.jdbc = jdbc; }

	public boolean isCurrent(UUID userId, long version) {
		return jdbc.sql("""
				SELECT credential_version FROM user_account
				WHERE id = :userId AND status = 'ACTIVE' AND deleted_at IS NULL
				""").param("userId", userId).query(Long.class).optional()
				.filter(current -> current == version).isPresent();
	}
}
