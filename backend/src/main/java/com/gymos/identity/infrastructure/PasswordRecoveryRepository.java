package com.gymos.identity.infrastructure;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class PasswordRecoveryRepository {
	private final JdbcClient jdbc;
	public PasswordRecoveryRepository(JdbcClient jdbc) { this.jdbc = jdbc; }

	public Optional<RecoveryUser> lockUser(UUID organizationId, UUID userId) {
		return jdbc.sql("""
				SELECT u.id, u.organization_id, p.email,
				       trim(concat(p.first_name, ' ', p.last_name)) AS full_name
				FROM user_account u JOIN person p ON p.id = u.person_id AND p.organization_id = u.organization_id
				WHERE u.id = :userId AND u.account_scope = 'TENANT'
				  AND (CAST(:organizationId AS UUID) IS NULL OR u.organization_id = CAST(:organizationId AS UUID))
				  AND u.status = 'ACTIVE' AND u.deleted_at IS NULL AND p.deleted_at IS NULL
				FOR UPDATE OF u
				""").param("userId", userId).param("organizationId", organizationId)
				.query((rs, row) -> new RecoveryUser(rs.getObject("id", UUID.class), rs.getObject("organization_id", UUID.class),
						rs.getString("full_name"), rs.getString("email"))).optional();
	}

	public Optional<OffsetDateTime> latestCreatedAt(UUID userId) {
		return jdbc.sql("SELECT created_at FROM user_password_reset WHERE user_id = :userId ORDER BY created_at DESC LIMIT 1")
				.param("userId", userId).query(OffsetDateTime.class).optional();
	}

	public void revokePending(UUID userId) {
		jdbc.sql("UPDATE user_password_reset SET status = 'REVOKED' WHERE user_id = :userId AND status = 'PENDING'")
				.param("userId", userId).update();
	}

	public void create(UUID id, UUID userId, String tokenHash, OffsetDateTime expiresAt, OffsetDateTime now, UUID actorId) {
		jdbc.sql("""
				INSERT INTO user_password_reset(id, user_id, token_hash, status, expires_at, created_at, created_by)
				VALUES (:id, :userId, :hash, 'PENDING', :expiresAt, :now, :actorId)
				""").param("id", id).param("userId", userId).param("hash", tokenHash)
				.param("expiresAt", expiresAt).param("now", now).param("actorId", actorId).update();
	}

	public Optional<UUID> userIdForToken(String hash) {
		return jdbc.sql("SELECT user_id FROM user_password_reset WHERE token_hash = :hash")
				.param("hash", hash).query(UUID.class).optional();
	}

	public Optional<RecoveryToken> lockToken(String hash) {
		return jdbc.sql("SELECT id, user_id, status, expires_at FROM user_password_reset WHERE token_hash = :hash FOR UPDATE")
				.param("hash", hash).query((rs, row) -> new RecoveryToken(rs.getObject("id", UUID.class),
						rs.getObject("user_id", UUID.class), rs.getString("status"), rs.getObject("expires_at", OffsetDateTime.class))).optional();
	}

	public void consume(RecoveryToken token, String passwordHash, OffsetDateTime now) {
		jdbc.sql("""
				UPDATE user_account SET password_hash = :hash, credential_version = credential_version + 1,
				    failed_attempts = 0, locked_until = NULL, updated_at = :now, version = version + 1
				WHERE id = :userId AND status = 'ACTIVE' AND account_scope = 'TENANT' AND deleted_at IS NULL
				""").param("hash", passwordHash).param("now", now).param("userId", token.userId()).update();
		jdbc.sql("UPDATE user_password_reset SET status = 'CONSUMED', consumed_at = :now WHERE id = :id")
				.param("now", now).param("id", token.id()).update();
		revokePending(token.userId());
	}

	public void recordDelivery(UUID id, String status) {
		jdbc.sql("UPDATE user_password_reset SET email_delivery_status = :status WHERE id = :id")
				.param("status", status).param("id", id).update();
	}

	public record RecoveryUser(UUID id, UUID organizationId, String name, String email) {}
	public record RecoveryToken(UUID id, UUID userId, String status, OffsetDateTime expiresAt) {}
}
