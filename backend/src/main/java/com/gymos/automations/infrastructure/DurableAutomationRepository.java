package com.gymos.automations.infrastructure;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class DurableAutomationRepository {
	private final JdbcClient jdbcClient;

	public DurableAutomationRepository(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	public WorkItemRecord enqueue(
			UUID organizationId,
			UUID branchId,
			UUID ruleId,
			String idempotencyKey,
			String triggerReference,
			int maxAttempts,
			OffsetDateTime scheduledAt
	) {
		UUID id = UUID.randomUUID();
		OffsetDateTime now = OffsetDateTime.now();
		try {
			jdbcClient.sql("""
					INSERT INTO automation_work_item (
					    id, organization_id, branch_id, rule_id, idempotency_key,
					    trigger_reference, status, attempt_count, max_attempts,
					    scheduled_at, next_attempt_at, created_at, updated_at, version
					)
					VALUES (
					    :id, :organizationId, :branchId, :ruleId, :idempotencyKey,
					    :triggerReference, 'PENDING', 0, :maxAttempts,
					    :scheduledAt, :scheduledAt, :createdAt, :updatedAt, 0
					)
					""")
					.param("id", id)
					.param("organizationId", organizationId)
					.param("branchId", branchId)
					.param("ruleId", ruleId)
					.param("idempotencyKey", idempotencyKey)
					.param("triggerReference", triggerReference)
					.param("maxAttempts", Math.max(1, maxAttempts))
					.param("scheduledAt", scheduledAt)
					.param("createdAt", now)
					.param("updatedAt", now)
					.update();
		} catch (DuplicateKeyException ignored) {
			return byIdempotencyKey(organizationId, idempotencyKey).orElseThrow();
		}
		return workItem(id).orElseThrow();
	}

	public Optional<WorkItemRecord> byIdempotencyKey(UUID organizationId, String idempotencyKey) {
		return jdbcClient.sql("""
				SELECT id, organization_id, branch_id, rule_id, idempotency_key,
				       trigger_reference, status, attempt_count, max_attempts,
				       scheduled_at, next_attempt_at, locked_until, started_at,
				       finished_at, last_error, effect_reference_id, created_at, updated_at
				FROM automation_work_item
				WHERE organization_id = :organizationId
				  AND idempotency_key = :idempotencyKey
				""")
				.param("organizationId", organizationId)
				.param("idempotencyKey", idempotencyKey)
				.query(this::map)
				.optional();
	}

	public Optional<WorkItemRecord> workItem(UUID id) {
		return jdbcClient.sql("""
				SELECT id, organization_id, branch_id, rule_id, idempotency_key,
				       trigger_reference, status, attempt_count, max_attempts,
				       scheduled_at, next_attempt_at, locked_until, started_at,
				       finished_at, last_error, effect_reference_id, created_at, updated_at
				FROM automation_work_item
				WHERE id = :id
				""")
				.param("id", id)
				.query(this::map)
				.optional();
	}

	public List<WorkItemRecord> due(OffsetDateTime now, int limit) {
		return jdbcClient.sql("""
				SELECT id, organization_id, branch_id, rule_id, idempotency_key,
				       trigger_reference, status, attempt_count, max_attempts,
				       scheduled_at, next_attempt_at, locked_until, started_at,
				       finished_at, last_error, effect_reference_id, created_at, updated_at
				FROM automation_work_item
				WHERE status IN ('PENDING', 'RETRY_SCHEDULED')
				  AND next_attempt_at <= :now
				  AND (locked_until IS NULL OR locked_until < :now)
				ORDER BY next_attempt_at, created_at
				LIMIT :limit
				""")
				.param("now", now)
				.param("limit", limit)
				.query(this::map)
				.list();
	}

	public boolean claim(UUID id, OffsetDateTime now, OffsetDateTime lockedUntil) {
		return jdbcClient.sql("""
				UPDATE automation_work_item
				SET status = 'RUNNING',
				    attempt_count = attempt_count + 1,
				    started_at = COALESCE(started_at, :now),
				    locked_until = :lockedUntil,
				    updated_at = :now,
				    version = version + 1
				WHERE id = :id
				  AND status IN ('PENDING', 'RETRY_SCHEDULED')
				  AND next_attempt_at <= :now
				  AND (locked_until IS NULL OR locked_until < :now)
				""")
				.param("id", id)
				.param("now", now)
				.param("lockedUntil", lockedUntil)
				.update() == 1;
	}

	public void markSucceeded(UUID id, UUID effectReferenceId) {
		OffsetDateTime now = OffsetDateTime.now();
		jdbcClient.sql("""
				UPDATE automation_work_item
				SET status = 'SUCCEEDED',
				    effect_reference_id = COALESCE(effect_reference_id, :effectReferenceId),
				    finished_at = :now,
				    locked_until = NULL,
				    last_error = NULL,
				    updated_at = :now,
				    version = version + 1
				WHERE id = :id
				  AND status = 'RUNNING'
				""")
				.param("id", id)
				.param("effectReferenceId", effectReferenceId)
				.param("now", now)
				.update();
	}

	public void markRetry(UUID id, OffsetDateTime nextAttemptAt, String error) {
		jdbcClient.sql("""
				UPDATE automation_work_item
				SET status = 'RETRY_SCHEDULED',
				    next_attempt_at = :nextAttemptAt,
				    locked_until = NULL,
				    last_error = :error,
				    updated_at = :now,
				    version = version + 1
				WHERE id = :id
				  AND status = 'RUNNING'
				""")
				.param("id", id)
				.param("nextAttemptAt", nextAttemptAt)
				.param("error", truncate(error))
				.param("now", OffsetDateTime.now())
				.update();
	}

	public void markDeadLetter(UUID id, String error) {
		OffsetDateTime now = OffsetDateTime.now();
		jdbcClient.sql("""
				UPDATE automation_work_item
				SET status = 'DEAD_LETTER',
				    finished_at = :now,
				    locked_until = NULL,
				    last_error = :error,
				    updated_at = :now,
				    version = version + 1
				WHERE id = :id
				  AND status = 'RUNNING'
				""")
				.param("id", id)
				.param("error", truncate(error))
				.param("now", now)
				.update();
	}

	private WorkItemRecord map(java.sql.ResultSet rs, int rowNumber) throws java.sql.SQLException {
		return new WorkItemRecord(
				rs.getObject("id", UUID.class),
				rs.getObject("organization_id", UUID.class),
				rs.getObject("branch_id", UUID.class),
				rs.getObject("rule_id", UUID.class),
				rs.getString("idempotency_key"),
				rs.getString("trigger_reference"),
				rs.getString("status"),
				rs.getInt("attempt_count"),
				rs.getInt("max_attempts"),
				rs.getObject("scheduled_at", OffsetDateTime.class),
				rs.getObject("next_attempt_at", OffsetDateTime.class),
				rs.getObject("locked_until", OffsetDateTime.class),
				rs.getObject("started_at", OffsetDateTime.class),
				rs.getObject("finished_at", OffsetDateTime.class),
				rs.getString("last_error"),
				rs.getObject("effect_reference_id", UUID.class),
				rs.getObject("created_at", OffsetDateTime.class),
				rs.getObject("updated_at", OffsetDateTime.class)
		);
	}

	private String truncate(String value) {
		if (value == null) return null;
		return value.length() <= 2000 ? value : value.substring(0, 2000);
	}

	public record WorkItemRecord(
			UUID id,
			UUID organizationId,
			UUID branchId,
			UUID ruleId,
			String idempotencyKey,
			String triggerReference,
			String status,
			int attemptCount,
			int maxAttempts,
			OffsetDateTime scheduledAt,
			OffsetDateTime nextAttemptAt,
			OffsetDateTime lockedUntil,
			OffsetDateTime startedAt,
			OffsetDateTime finishedAt,
			String lastError,
			UUID effectReferenceId,
			OffsetDateTime createdAt,
			OffsetDateTime updatedAt
	) {}
}
