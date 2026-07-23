package com.gymos.catalog.infrastructure;

import com.gymos.catalog.application.port.CatalogStorageCleanupPort;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class CatalogStorageCleanupRepository implements CatalogStorageCleanupPort {
	private final JdbcClient jdbcClient;

	CatalogStorageCleanupRepository(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	@Override
	public void enqueue(String objectKey, OffsetDateTime now) {
		if (objectKey == null || objectKey.isBlank()) return;
		jdbcClient.sql("""
				INSERT INTO catalog_storage_cleanup (
				 id, object_key, status, attempts, next_attempt_at, created_at, updated_at)
				VALUES (:id, :objectKey, 'PENDING', 0, :now, :now, :now)
				ON CONFLICT (object_key) DO UPDATE
				SET status='PENDING', next_attempt_at=LEAST(catalog_storage_cleanup.next_attempt_at, EXCLUDED.next_attempt_at),
				    updated_at=EXCLUDED.updated_at, completed_at=null
				""")
				.param("id", UUID.randomUUID()).param("objectKey", objectKey).param("now", now).update();
	}

	List<CleanupTask> pending(OffsetDateTime now, int limit) {
		return jdbcClient.sql("""
				SELECT id, object_key, attempts
				FROM catalog_storage_cleanup
				WHERE status='PENDING' AND next_attempt_at<=:now
				ORDER BY next_attempt_at, created_at
				LIMIT :limit
				FOR UPDATE SKIP LOCKED
				""")
				.param("now", now).param("limit", limit)
				.query((rs, row) -> new CleanupTask(
						rs.getObject("id", UUID.class), rs.getString("object_key"), rs.getInt("attempts")))
				.list();
	}

	void complete(UUID id, OffsetDateTime now) {
		jdbcClient.sql("""
				UPDATE catalog_storage_cleanup
				SET status='COMPLETED', completed_at=:now, updated_at=:now, last_error=null
				WHERE id=:id
				""").param("now", now).param("id", id).update();
	}

	void retry(UUID id, int attempts, OffsetDateTime nextAttemptAt, String error, OffsetDateTime now) {
		jdbcClient.sql("""
				UPDATE catalog_storage_cleanup
				SET attempts=:attempts, next_attempt_at=:nextAttemptAt, last_error=:error, updated_at=:now
				WHERE id=:id AND status='PENDING'
				""")
				.param("attempts", attempts).param("nextAttemptAt", nextAttemptAt)
				.param("error", error == null ? "Error desconocido" : error.substring(0, Math.min(error.length(), 1000)))
				.param("now", now).param("id", id).update();
	}

	record CleanupTask(UUID id, String objectKey, int attempts) {
	}
}
