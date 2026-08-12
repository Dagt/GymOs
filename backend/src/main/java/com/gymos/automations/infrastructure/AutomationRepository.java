package com.gymos.automations.infrastructure;

import com.gymos.common.api.PageResponse;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class AutomationRepository {
	private final JdbcClient jdbcClient;

	public AutomationRepository(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	public List<AutomationRuleRecord> rules(UUID organizationId, UUID branchId) {
		return jdbcClient.sql("""
				SELECT organization_id, branch_id, id, name, trigger_type, condition_json::text AS condition_json,
				       action_type, action_config_json::text AS action_config_json,
				       status, retry_limit, schedule_cron, next_run_at, locked_until,
			       current_trigger_reference, created_at, updated_at, version
				FROM automation_rule
				WHERE organization_id = :organizationId
				  AND (branch_id = :branchId OR branch_id IS NULL)
				  AND deleted_at IS NULL
				ORDER BY created_at DESC
				""")
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.query(this::mapRule)
				.list();
	}

	public Optional<AutomationRuleRecord> rule(UUID organizationId, UUID branchId, UUID ruleId) {
		return jdbcClient.sql("""
				SELECT organization_id, branch_id, id, name, trigger_type, condition_json::text AS condition_json,
				       action_type, action_config_json::text AS action_config_json,
				       status, retry_limit, schedule_cron, next_run_at, locked_until,
			       current_trigger_reference, created_at, updated_at, version
				FROM automation_rule
				WHERE organization_id = :organizationId
				  AND (branch_id = :branchId OR branch_id IS NULL)
				  AND id = :ruleId
				  AND deleted_at IS NULL
				""")
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("ruleId", ruleId)
				.query(this::mapRule)
				.optional();
	}

	public UUID createRule(UUID organizationId, UUID branchId, UUID actorUserId, CreateRuleData data) {
		OffsetDateTime now = OffsetDateTime.now();
		UUID ruleId = UUID.randomUUID();
		jdbcClient.sql("""
				INSERT INTO automation_rule (
				    id, organization_id, branch_id, name, trigger_type, condition_json,
				    action_type, action_config_json, status, retry_limit, schedule_cron, next_run_at,
				    created_at, updated_at, created_by, updated_by, version
				)
				VALUES (
				    :id, :organizationId, :branchId, :name, :triggerType, CAST(:conditionJson AS jsonb),
				    :actionType, CAST(:actionConfigJson AS jsonb), 'ENABLED', :retryLimit, :scheduleCron, :nextRunAt,
				    :createdAt, :updatedAt, :createdBy, :updatedBy, 0
				)
				""")
				.param("id", ruleId)
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("name", data.name())
				.param("triggerType", data.triggerType())
				.param("conditionJson", data.conditionJson())
				.param("actionType", data.actionType())
				.param("actionConfigJson", data.actionConfigJson())
				.param("retryLimit", data.retryLimit())
				.param("scheduleCron", data.scheduleCron())
				.param("nextRunAt", data.nextRunAt())
				.param("createdAt", now)
				.param("updatedAt", now)
				.param("createdBy", actorUserId)
				.param("updatedBy", actorUserId)
				.update();
		return ruleId;
	}

	public void updateStatus(UUID organizationId, UUID branchId, UUID ruleId, String status, UUID actorUserId) {
		jdbcClient.sql("""
				UPDATE automation_rule
				SET status = :status,
				    updated_at = :updatedAt,
				    updated_by = :updatedBy,
				    version = version + 1
				WHERE organization_id = :organizationId
				  AND (branch_id = :branchId OR branch_id IS NULL)
				  AND id = :ruleId
				  AND deleted_at IS NULL
				""")
				.param("status", status)
				.param("updatedAt", OffsetDateTime.now())
				.param("updatedBy", actorUserId)
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("ruleId", ruleId)
				.update();
	}

	public UUID startExecution(
			UUID organizationId,
			UUID branchId,
			UUID ruleId,
			long ruleVersion,
			String triggerReference,
			String occurrenceReference,
			String idempotencyKey
	) {
		OffsetDateTime now = OffsetDateTime.now();
		UUID executionId = UUID.randomUUID();
		var inserted = jdbcClient.sql("""
				INSERT INTO automation_execution (
				    id, organization_id, branch_id, rule_id, status, trigger_reference, occurrence_reference,
			    error_message, idempotency_key, attempt_number, started_at, finished_at, rule_version,
			    occurred_at, created_at
				)
				VALUES (
				    :id, :organizationId, :branchId, :ruleId, 'PROCESSING', :triggerReference, :occurrenceReference,
			    NULL, :idempotencyKey, 1, :occurredAt, NULL, :ruleVersion,
				    :occurredAt, :createdAt
				)
				ON CONFLICT (organization_id, rule_id, idempotency_key)
				    WHERE idempotency_key IS NOT NULL
				DO UPDATE SET
				    status = 'PROCESSING',
				    error_message = NULL,
				    trigger_reference = EXCLUDED.trigger_reference,
				    occurrence_reference = EXCLUDED.occurrence_reference,
				    attempt_number = automation_execution.attempt_number + 1,
				    started_at = EXCLUDED.started_at,
				    finished_at = NULL,
				    occurred_at = EXCLUDED.occurred_at
				RETURNING id
				""")
				.param("id", executionId)
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("ruleId", ruleId)
				.param("triggerReference", triggerReference)
				.param("occurrenceReference", occurrenceReference)
				.param("idempotencyKey", idempotencyKey)
				.param("ruleVersion", ruleVersion)
				.param("occurredAt", now)
				.param("createdAt", now)
				.query(UUID.class)
				.optional();
		return inserted.orElseThrow();
	}

	public void finishExecution(UUID executionId, String status, String errorMessage) {
		jdbcClient.sql("""
				UPDATE automation_execution
				SET status = :status,
				    error_message = :errorMessage,
				    finished_at = :finishedAt
				WHERE id = :executionId
				""")
				.param("executionId", executionId)
				.param("status", status)
				.param("errorMessage", errorMessage)
				.param("finishedAt", OffsetDateTime.now())
				.update();
	}

	public List<AutomationExecutionRecord> executions(UUID organizationId, UUID branchId, UUID ruleId) {
		return jdbcClient.sql("""
				SELECT id, rule_id, status, trigger_reference, error_message, idempotency_key,
				       attempt_number, rule_version, started_at, finished_at, occurred_at
				FROM automation_execution
				WHERE organization_id = :organizationId
				  AND (branch_id = :branchId OR branch_id IS NULL)
				  AND rule_id = :ruleId
				ORDER BY occurred_at DESC
				LIMIT 100
				""")
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("ruleId", ruleId)
				.query((resultSet, rowNumber) -> new AutomationExecutionRecord(
						resultSet.getObject("id", UUID.class),
						resultSet.getObject("rule_id", UUID.class),
						resultSet.getString("status"),
						resultSet.getString("trigger_reference"),
						resultSet.getString("error_message"),
						resultSet.getString("idempotency_key"),
						resultSet.getInt("attempt_number"),
						resultSet.getLong("rule_version"),
						resultSet.getObject("started_at", OffsetDateTime.class),
						resultSet.getObject("finished_at", OffsetDateTime.class),
						resultSet.getObject("occurred_at", OffsetDateTime.class)
				))
				.list();
	}

	public PageResponse<AutomationExecutionRecord> executionHistory(
			UUID organizationId, UUID branchId, UUID ruleId, int page, int size
	) {
		long total = jdbcClient.sql("""
				SELECT COUNT(*)
				FROM automation_execution
				WHERE organization_id = :organizationId
				  AND (branch_id = :branchId OR branch_id IS NULL)
				  AND rule_id = :ruleId
				""")
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("ruleId", ruleId)
				.query(Long.class)
				.single();
		List<AutomationExecutionRecord> items = jdbcClient.sql("""
				SELECT id, rule_id, status, trigger_reference, error_message, idempotency_key,
				       attempt_number, rule_version, started_at, finished_at, occurred_at
				FROM automation_execution
				WHERE organization_id = :organizationId
				  AND (branch_id = :branchId OR branch_id IS NULL)
				  AND rule_id = :ruleId
				ORDER BY occurred_at DESC, id DESC
				LIMIT :size OFFSET :offset
				""")
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("ruleId", ruleId)
				.param("size", size)
				.param("offset", page * size)
				.query(this::mapExecution)
				.list();
		return PageResponse.of(items, page, size, total);
	}

	public List<AutomationRuleRecord> dueScheduledRules(OffsetDateTime now, int limit) {
		return jdbcClient.sql("""
				SELECT organization_id, branch_id, id, name, trigger_type, condition_json, action_type,
				       action_config_json, status, retry_limit, schedule_cron, next_run_at, locked_until,
				       current_trigger_reference, created_at, updated_at, version
				FROM (
				    SELECT organization_id, branch_id, id, name, trigger_type,
				           condition_json::text AS condition_json,
				       action_type, action_config_json::text AS action_config_json,
				       status, retry_limit, schedule_cron, next_run_at, locked_until,
				           current_trigger_reference, created_at, updated_at, version,
				           row_number() OVER (
				               PARTITION BY organization_id ORDER BY next_run_at, id
				           ) AS tenant_rank
				    FROM automation_rule rule
				    WHERE status = 'ENABLED'
				      AND trigger_type = 'SCHEDULED'
				      AND next_run_at IS NOT NULL
				      AND next_run_at <= :now
				      AND (locked_until IS NULL OR locked_until < :now)
				      AND deleted_at IS NULL
				      AND NOT EXISTS (
				          SELECT 1 FROM automation_scheduler_control control
				          WHERE control.organization_id = rule.organization_id AND control.paused
				      )
				) fair_queue
				ORDER BY tenant_rank, next_run_at, organization_id, id
				LIMIT :limit
				""")
				.param("now", now)
				.param("limit", limit)
				.query(this::mapRule)
				.list();
	}

	public boolean claimScheduledRule(UUID ruleId, OffsetDateTime now, OffsetDateTime lockedUntil, String triggerReference) {
		int updated = jdbcClient.sql("""
				UPDATE automation_rule
				SET locked_until = :lockedUntil,
				    current_trigger_reference = COALESCE(current_trigger_reference, :triggerReference),
				    updated_at = :updatedAt
				WHERE id = :ruleId
				  AND status = 'ENABLED'
				  AND trigger_type = 'SCHEDULED'
				  AND next_run_at IS NOT NULL
				  AND next_run_at <= :now
				  AND (locked_until IS NULL OR locked_until < :now)
				  AND deleted_at IS NULL
				  AND NOT EXISTS (
				      SELECT 1 FROM automation_scheduler_control control
				      WHERE control.organization_id = automation_rule.organization_id AND control.paused
				  )
				""")
				.param("ruleId", ruleId)
				.param("now", now)
				.param("lockedUntil", lockedUntil)
				.param("triggerReference", triggerReference)
				.param("updatedAt", now)
				.update();
		return updated == 1;
	}

	public void rescheduleRule(UUID ruleId, OffsetDateTime nextRunAt, boolean clearTriggerReference) {
		jdbcClient.sql("""
				UPDATE automation_rule
				SET next_run_at = :nextRunAt,
				    locked_until = NULL,
				    current_trigger_reference = CASE WHEN :clearTriggerReference THEN NULL ELSE current_trigger_reference END,
				    updated_at = :updatedAt
				WHERE id = :ruleId
				  AND deleted_at IS NULL
				""")
				.param("ruleId", ruleId)
				.param("nextRunAt", nextRunAt)
				.param("clearTriggerReference", clearTriggerReference)
				.param("updatedAt", OffsetDateTime.now())
				.update();
	}

	public void updateExecutionStatus(UUID executionId, String status) {
		jdbcClient.sql("""
				UPDATE automation_execution
				SET status = :status, finished_at = :finishedAt
				WHERE id = :executionId
				""")
				.param("executionId", executionId)
				.param("status", status)
				.param("finishedAt", OffsetDateTime.now())
				.update();
	}

	public Optional<AutomationExecutionRecord> execution(
			UUID organizationId,
			UUID branchId,
			UUID executionId
	) {
		return jdbcClient.sql("""
				SELECT id, rule_id, status, trigger_reference, error_message, idempotency_key,
				       attempt_number, rule_version, started_at, finished_at, occurred_at
				FROM automation_execution
				WHERE organization_id = :organizationId
				  AND (branch_id = :branchId OR branch_id IS NULL)
				  AND id = :executionId
				""")
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("executionId", executionId)
				.query(this::mapExecution)
				.optional();
	}

	public PageResponse<AutomationDeadLetterRecord> deadLetters(
			UUID organizationId, UUID branchId, int page, int size
	) {
		String deadLetterCte = """
				WITH dead_letter AS (
				    SELECT execution.id,
				           'EXECUTION'::varchar AS source,
				           execution.rule_id,
				           rule.name AS rule_name,
				           rule.trigger_type,
				           execution.trigger_reference,
				           execution.error_message,
				           execution.attempt_number,
				           execution.occurred_at
				    FROM automation_execution execution
				    JOIN automation_rule rule ON rule.id = execution.rule_id
				    WHERE execution.organization_id = :organizationId
				      AND (execution.branch_id = :branchId OR execution.branch_id IS NULL)
				      AND execution.status = 'DEAD_LETTER'
				    UNION ALL
				    SELECT event.id,
				           'EVENT'::varchar AS source,
				           NULL::uuid AS rule_id,
				           NULL::varchar AS rule_name,
				           event.trigger_type,
				           event.trigger_reference,
				           event.last_error AS error_message,
				           event.attempt_number,
				           event.updated_at AS occurred_at
				    FROM automation_trigger_event event
				    WHERE event.organization_id = :organizationId
				      AND event.branch_id = :branchId
				      AND event.status = 'DEAD_LETTER'
				      AND NOT EXISTS (
				          SELECT 1
				          FROM automation_execution execution
				          JOIN automation_rule execution_rule ON execution_rule.id = execution.rule_id
				          WHERE execution.organization_id = event.organization_id
				            AND (execution.branch_id = event.branch_id OR execution.branch_id IS NULL)
				            AND execution.trigger_reference = event.trigger_reference
				            AND execution_rule.trigger_type = event.trigger_type
				            AND execution.status = 'DEAD_LETTER'
				      )
				)
				""";
		long total = jdbcClient.sql(deadLetterCte + "SELECT COUNT(*) FROM dead_letter")
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.query(Long.class)
				.single();
		List<AutomationDeadLetterRecord> items = jdbcClient.sql(deadLetterCte + """
				SELECT * FROM dead_letter
				ORDER BY occurred_at DESC, id DESC
				LIMIT :size OFFSET :offset
				""")
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("size", size)
				.param("offset", page * size)
				.query((resultSet, rowNumber) -> new AutomationDeadLetterRecord(
						resultSet.getObject("id", UUID.class),
						resultSet.getString("source"),
						resultSet.getObject("rule_id", UUID.class),
						resultSet.getString("rule_name"),
						resultSet.getString("trigger_type"),
						resultSet.getString("trigger_reference"),
						resultSet.getString("error_message"),
						resultSet.getInt("attempt_number"),
						resultSet.getObject("occurred_at", OffsetDateTime.class)
				))
				.list();
		return PageResponse.of(items, page, size, total);
	}

	public boolean requeueTriggerEvent(
			UUID organizationId,
			UUID branchId,
			String triggerType,
			String triggerReference
	) {
		OffsetDateTime now = OffsetDateTime.now();
		return jdbcClient.sql("""
				UPDATE automation_trigger_event
				SET status = 'RETRY_SCHEDULED', available_at = :now, locked_until = NULL,
				    completed_at = NULL, updated_at = :now, version = version + 1
				WHERE organization_id = :organizationId
				  AND branch_id = :branchId
				  AND trigger_type = :triggerType
				  AND trigger_reference = :triggerReference
				  AND status IN ('DEAD_LETTER', 'RETRY_SCHEDULED')
				""")
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("triggerType", triggerType)
				.param("triggerReference", triggerReference)
				.param("now", now)
				.update() == 1;
	}

	public boolean requeueTriggerEvent(UUID organizationId, UUID branchId, UUID eventId) {
		OffsetDateTime now = OffsetDateTime.now();
		return jdbcClient.sql("""
				UPDATE automation_trigger_event
				SET status = 'RETRY_SCHEDULED', available_at = :now, locked_until = NULL,
				    completed_at = NULL, updated_at = :now, version = version + 1
				WHERE organization_id = :organizationId
				  AND branch_id = :branchId
				  AND id = :eventId
				  AND status IN ('DEAD_LETTER', 'RETRY_SCHEDULED')
				""")
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("eventId", eventId)
				.param("now", now)
				.update() == 1;
	}

	public boolean discardTriggerEvent(UUID organizationId, UUID branchId, UUID eventId, String reason) {
		OffsetDateTime now = OffsetDateTime.now();
		return jdbcClient.sql("""
				UPDATE automation_trigger_event
				SET status = 'DISCARDED', locked_until = NULL, completed_at = :now,
				    last_error = :reason, updated_at = :now, version = version + 1
				WHERE organization_id = :organizationId
				  AND branch_id = :branchId
				  AND id = :eventId
				  AND status = 'DEAD_LETTER'
				""")
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("eventId", eventId)
				.param("reason", reason)
				.param("now", now)
				.update() == 1;
	}

	public boolean requeueScheduledRule(UUID ruleId, String triggerReference) {
		OffsetDateTime now = OffsetDateTime.now();
		return jdbcClient.sql("""
				UPDATE automation_rule
				SET next_run_at = :now, locked_until = NULL,
				    current_trigger_reference = :triggerReference,
				    updated_at = :now
				WHERE id = :ruleId
				  AND trigger_type = 'SCHEDULED'
				  AND status = 'ENABLED'
				  AND deleted_at IS NULL
				""")
				.param("ruleId", ruleId)
				.param("triggerReference", triggerReference)
				.param("now", now)
				.update() == 1;
	}

	public List<AutomationRuleRecord> enabledRulesForTrigger(UUID organizationId, UUID branchId, String triggerType) {
		return jdbcClient.sql("""
				SELECT organization_id, branch_id, id, name, trigger_type, condition_json::text AS condition_json,
				       action_type, action_config_json::text AS action_config_json,
				       status, retry_limit, schedule_cron, next_run_at, locked_until,
				       current_trigger_reference, created_at, updated_at, version
				FROM automation_rule
				WHERE organization_id = :organizationId
				  AND (branch_id IS NULL OR branch_id = :branchId)
				  AND trigger_type = :triggerType
				  AND status = 'ENABLED'
				  AND deleted_at IS NULL
				ORDER BY created_at, id
				""")
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("triggerType", triggerType)
				.query(this::mapRule)
				.list();
	}

	public List<BookingReminderCandidate> dueBookingReminders(OffsetDateTime now, int limit) {
		return jdbcClient.sql("""
				WITH reminder_candidates AS (
				    SELECT rule.organization_id,
				           booking.branch_id,
				           rule.id AS rule_id,
				           rule.version AS rule_version,
				           booking.id AS booking_id,
				           booking.client_id,
				           booking.resource_id,
				           booking.starts_at,
				           booking.ends_at,
				           COALESCE((rule.condition_json ->> 'minutesBefore')::integer, 1440) AS minutes_before,
				           row_number() OVER (
				               PARTITION BY rule.organization_id ORDER BY booking.starts_at, rule.id, booking.id
				           ) AS tenant_rank
				    FROM automation_rule rule
				    JOIN booking
				      ON booking.organization_id = rule.organization_id
				     AND (rule.branch_id IS NULL OR booking.branch_id = rule.branch_id)
				    WHERE rule.trigger_type = 'BOOKING_REMINDER'
				      AND rule.status = 'ENABLED'
				      AND rule.deleted_at IS NULL
				      AND booking.deleted_at IS NULL
				      AND booking.status IN ('RESERVED', 'CONFIRMED')
				      AND booking.starts_at > :now
				      AND booking.starts_at <= :now + make_interval(
				          mins => COALESCE((rule.condition_json ->> 'minutesBefore')::integer, 1440)
				      )
				      AND NOT EXISTS (
				          SELECT 1
				          FROM automation_trigger_event event
				          WHERE event.organization_id = rule.organization_id
				            AND event.trigger_type = 'BOOKING_REMINDER'
				            AND event.trigger_reference = CONCAT(
				                'booking-reminder:', booking.id, ':', rule.id, ':v', rule.version
				            )
				      )
				      AND NOT EXISTS (
				          SELECT 1
				          FROM automation_scheduler_control control
				          WHERE control.organization_id = rule.organization_id AND control.paused
				      )
				)
				SELECT organization_id, branch_id, rule_id, rule_version, booking_id,
				       client_id, resource_id, starts_at, ends_at, minutes_before
				FROM reminder_candidates
				ORDER BY tenant_rank, starts_at, organization_id, rule_id, booking_id
				LIMIT :limit
				""")
				.param("now", now)
				.param("limit", limit)
				.query((rs, row) -> new BookingReminderCandidate(
						rs.getObject("organization_id", UUID.class),
						rs.getObject("branch_id", UUID.class),
						rs.getObject("rule_id", UUID.class),
						rs.getLong("rule_version"),
						rs.getObject("booking_id", UUID.class),
						rs.getObject("client_id", UUID.class),
						rs.getObject("resource_id", UUID.class),
						rs.getObject("starts_at", OffsetDateTime.class),
						rs.getObject("ends_at", OffsetDateTime.class),
						rs.getInt("minutes_before")
				))
				.list();
	}

	public Optional<AutomationExecutionRecord> executionForTrigger(
			UUID organizationId,
			UUID ruleId,
			String triggerReference
	) {
		return jdbcClient.sql("""
				SELECT id, rule_id, status, trigger_reference, error_message, idempotency_key,
				       attempt_number, rule_version, started_at, finished_at, occurred_at
				FROM automation_execution
				WHERE organization_id = :organizationId
				  AND rule_id = :ruleId
				  AND trigger_reference = :triggerReference
				  AND idempotency_key IS NOT NULL
				ORDER BY occurred_at DESC
				LIMIT 1
				""")
				.param("organizationId", organizationId)
				.param("ruleId", ruleId)
				.param("triggerReference", triggerReference)
				.query(this::mapExecution)
				.optional();
	}

	public Optional<AutomationExecutionRecord> executionForOccurrence(
			UUID organizationId,
			UUID ruleId,
			String occurrenceReference
	) {
		return jdbcClient.sql("""
				SELECT id, rule_id, status, trigger_reference, error_message, idempotency_key,
				       attempt_number, rule_version, started_at, finished_at, occurred_at
				FROM automation_execution
				WHERE organization_id = :organizationId
				  AND rule_id = :ruleId
				  AND occurrence_reference = :occurrenceReference
				ORDER BY occurred_at DESC
				LIMIT 1
				""")
				.param("organizationId", organizationId)
				.param("ruleId", ruleId)
				.param("occurrenceReference", occurrenceReference)
				.query(this::mapExecution)
				.optional();
	}

	public void enqueueTriggerEvent(
			UUID organizationId,
			UUID branchId,
			String triggerType,
			String triggerReference,
			String payloadJson
	) {
		OffsetDateTime now = OffsetDateTime.now();
		jdbcClient.sql("""
				INSERT INTO automation_trigger_event (
				    id, organization_id, branch_id, trigger_type, trigger_reference,
				    payload_json, status, attempt_number, available_at, created_at, updated_at, version
				)
				VALUES (
				    :id, :organizationId, :branchId, :triggerType, :triggerReference,
				    CAST(:payloadJson AS jsonb), 'PENDING', 0, :now, :now, :now, 0
				)
				ON CONFLICT (organization_id, trigger_type, trigger_reference) DO NOTHING
				""")
				.param("id", UUID.randomUUID())
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("triggerType", triggerType)
				.param("triggerReference", triggerReference)
				.param("payloadJson", payloadJson)
				.param("now", now)
				.update();
	}

	public List<AutomationTriggerEventRecord> dueTriggerEvents(OffsetDateTime now, int limit) {
		return jdbcClient.sql("""
				SELECT id, organization_id, branch_id, trigger_type, trigger_reference,
				       payload_json::text AS payload_json, status, attempt_number,
				       available_at, locked_until, last_error
				FROM (
				    SELECT event.*,
				           row_number() OVER (
				               PARTITION BY organization_id ORDER BY available_at, created_at, id
				           ) AS tenant_rank
				    FROM automation_trigger_event event
				    WHERE status IN ('PENDING', 'RETRY_SCHEDULED', 'PROCESSING')
				      AND available_at <= :now
				      AND (locked_until IS NULL OR locked_until < :now)
				      AND NOT EXISTS (
				          SELECT 1 FROM automation_scheduler_control control
				          WHERE control.organization_id = event.organization_id AND control.paused
				      )
				) fair_queue
				ORDER BY tenant_rank, available_at, organization_id, id
				LIMIT :limit
				""")
				.param("now", now)
				.param("limit", limit)
				.query(this::mapTriggerEvent)
				.list();
	}

	public SchedulerStatusRecord schedulerStatus(UUID organizationId) {
		return jdbcClient.sql("""
				SELECT CAST(:organizationId AS uuid) AS organization_id,
				       COALESCE(control.paused, false) AS paused,
				       control.reason, control.paused_at, control.paused_by, control.updated_at
				FROM (SELECT 1) seed
				LEFT JOIN automation_scheduler_control control
				  ON control.organization_id = :organizationId
				""")
				.param("organizationId", organizationId)
				.query((rs, row) -> new SchedulerStatusRecord(
						rs.getObject("organization_id", UUID.class), rs.getBoolean("paused"),
						rs.getString("reason"), rs.getObject("paused_at", OffsetDateTime.class),
						rs.getObject("paused_by", UUID.class), rs.getObject("updated_at", OffsetDateTime.class)
				))
				.single();
	}

	public SchedulerStatusRecord setSchedulerPaused(
			UUID organizationId, UUID actorUserId, boolean paused, String reason
	) {
		OffsetDateTime now = OffsetDateTime.now();
		jdbcClient.sql("""
				INSERT INTO automation_scheduler_control (
				    organization_id, paused, reason, paused_at, paused_by, updated_at, version
				) VALUES (
				    :organizationId, :paused, :reason,
				    CASE WHEN :paused THEN :now ELSE NULL END,
				    CASE WHEN :paused THEN :actorUserId ELSE NULL END,
				    :now, 0
				)
				ON CONFLICT (organization_id) DO UPDATE
				SET paused = EXCLUDED.paused,
				    reason = EXCLUDED.reason,
				    paused_at = EXCLUDED.paused_at,
				    paused_by = EXCLUDED.paused_by,
				    updated_at = EXCLUDED.updated_at,
				    version = automation_scheduler_control.version + 1
				""")
				.param("organizationId", organizationId)
				.param("paused", paused)
				.param("reason", reason)
				.param("actorUserId", actorUserId)
				.param("now", now)
				.update();
		return schedulerStatus(organizationId);
	}

	public AutomationOperationsRecord operations(UUID organizationId, UUID branchId, OffsetDateTime now) {
		return jdbcClient.sql("""
				SELECT
				  (SELECT COUNT(*) FROM automation_rule rule
				   WHERE rule.organization_id = :organizationId
				     AND (rule.branch_id = :branchId OR rule.branch_id IS NULL)
				     AND rule.status = 'ENABLED' AND rule.trigger_type = 'SCHEDULED'
				     AND rule.next_run_at <= :now AND rule.deleted_at IS NULL) AS due_rules,
				  (SELECT COUNT(*) FROM automation_trigger_event event
				   WHERE event.organization_id = :organizationId AND event.branch_id = :branchId
				     AND event.status = 'PENDING' AND event.available_at <= :now) AS pending_events,
				  (SELECT COUNT(*) FROM automation_trigger_event event
				   WHERE event.organization_id = :organizationId AND event.branch_id = :branchId
				     AND event.status = 'RETRY_SCHEDULED') AS retry_events,
				  (SELECT COUNT(*) FROM automation_trigger_event event
				   WHERE event.organization_id = :organizationId AND event.branch_id = :branchId
				     AND event.status = 'PROCESSING' AND event.locked_until < :now) AS expired_leases,
				  (SELECT COUNT(*) FROM automation_trigger_event event
				   WHERE event.organization_id = :organizationId AND event.branch_id = :branchId
				     AND event.status = 'DEAD_LETTER') AS dead_letter_events,
				  (SELECT COUNT(*) FROM automation_execution execution
				   WHERE execution.organization_id = :organizationId
				     AND (execution.branch_id = :branchId OR execution.branch_id IS NULL)
				     AND execution.status = 'DEAD_LETTER') AS dead_letter_executions,
				  (SELECT MIN(event.available_at) FROM automation_trigger_event event
				   WHERE event.organization_id = :organizationId AND event.branch_id = :branchId
				     AND event.status IN ('PENDING', 'RETRY_SCHEDULED')) AS oldest_pending_at
				""")
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("now", now)
				.query((rs, row) -> new AutomationOperationsRecord(
						rs.getLong("due_rules"), rs.getLong("pending_events"), rs.getLong("retry_events"),
						rs.getLong("expired_leases"), rs.getLong("dead_letter_events"),
						rs.getLong("dead_letter_executions"),
						rs.getObject("oldest_pending_at", OffsetDateTime.class)
				))
				.single();
	}

	public List<AutomationOperationContext> operationContexts() {
		return jdbcClient.sql("""
				SELECT branch.organization_id, branch.id AS branch_id
				FROM branch
				JOIN organization ON organization.id = branch.organization_id
				WHERE branch.status = 'ACTIVE' AND branch.deleted_at IS NULL
				  AND organization.status = 'ACTIVE' AND organization.deleted_at IS NULL
				ORDER BY branch.organization_id, branch.id
				""")
				.query((rs, row) -> new AutomationOperationContext(
						rs.getObject("organization_id", UUID.class), rs.getObject("branch_id", UUID.class)
				))
				.list();
	}

	public boolean claimTriggerEvent(UUID eventId, OffsetDateTime now, OffsetDateTime lockedUntil) {
		return jdbcClient.sql("""
				UPDATE automation_trigger_event
				SET status = 'PROCESSING', attempt_number = attempt_number + 1,
				    locked_until = :lockedUntil, updated_at = :now, version = version + 1
				WHERE id = :eventId
				  AND status IN ('PENDING', 'RETRY_SCHEDULED', 'PROCESSING')
				  AND available_at <= :now
				  AND (locked_until IS NULL OR locked_until < :now)
				  AND NOT EXISTS (
				      SELECT 1 FROM automation_scheduler_control control
				      WHERE control.organization_id = automation_trigger_event.organization_id AND control.paused
				  )
				""")
				.param("eventId", eventId)
				.param("now", now)
				.param("lockedUntil", lockedUntil)
				.update() == 1;
	}

	public void finishTriggerEvent(UUID eventId, String status, OffsetDateTime availableAt, String lastError) {
		OffsetDateTime now = OffsetDateTime.now();
		boolean terminal = Set.of("COMPLETED", "DEAD_LETTER", "DISCARDED").contains(status);
		jdbcClient.sql("""
				UPDATE automation_trigger_event
				SET status = :status,
				    available_at = :availableAt,
				    locked_until = NULL,
				    last_error = :lastError,
				    completed_at = CASE WHEN :terminal THEN :now ELSE NULL END,
				    updated_at = :now,
				    version = version + 1
				WHERE id = :eventId
				""")
				.param("eventId", eventId)
				.param("status", status)
				.param("availableAt", availableAt == null ? now : availableAt)
				.param("lastError", lastError)
				.param("terminal", terminal)
				.param("now", now)
				.update();
	}

	private AutomationRuleRecord mapRule(java.sql.ResultSet resultSet, int rowNumber) throws java.sql.SQLException {
		return new AutomationRuleRecord(
				resultSet.getObject("organization_id", UUID.class),
				resultSet.getObject("branch_id", UUID.class),
				resultSet.getObject("id", UUID.class),
				resultSet.getString("name"),
				resultSet.getString("trigger_type"),
				resultSet.getString("condition_json"),
				resultSet.getString("action_type"),
				resultSet.getString("action_config_json"),
				resultSet.getString("status"),
				resultSet.getInt("retry_limit"),
				resultSet.getString("schedule_cron"),
				resultSet.getObject("next_run_at", OffsetDateTime.class),
				resultSet.getObject("locked_until", OffsetDateTime.class),
				resultSet.getString("current_trigger_reference"),
				resultSet.getObject("created_at", OffsetDateTime.class),
				resultSet.getObject("updated_at", OffsetDateTime.class),
				resultSet.getLong("version")
		);
	}

	private AutomationExecutionRecord mapExecution(java.sql.ResultSet resultSet, int rowNumber) throws java.sql.SQLException {
		return new AutomationExecutionRecord(
				resultSet.getObject("id", UUID.class),
				resultSet.getObject("rule_id", UUID.class),
				resultSet.getString("status"),
				resultSet.getString("trigger_reference"),
				resultSet.getString("error_message"),
				resultSet.getString("idempotency_key"),
				resultSet.getInt("attempt_number"),
				resultSet.getLong("rule_version"),
				resultSet.getObject("started_at", OffsetDateTime.class),
				resultSet.getObject("finished_at", OffsetDateTime.class),
				resultSet.getObject("occurred_at", OffsetDateTime.class)
		);
	}

	private AutomationTriggerEventRecord mapTriggerEvent(java.sql.ResultSet resultSet, int rowNumber) throws java.sql.SQLException {
		return new AutomationTriggerEventRecord(
				resultSet.getObject("id", UUID.class),
				resultSet.getObject("organization_id", UUID.class),
				resultSet.getObject("branch_id", UUID.class),
				resultSet.getString("trigger_type"),
				resultSet.getString("trigger_reference"),
				resultSet.getString("payload_json"),
				resultSet.getString("status"),
				resultSet.getInt("attempt_number"),
				resultSet.getObject("available_at", OffsetDateTime.class),
				resultSet.getObject("locked_until", OffsetDateTime.class),
				resultSet.getString("last_error")
		);
	}

	public record CreateRuleData(String name, String triggerType, String conditionJson, String actionType, String actionConfigJson, int retryLimit, String scheduleCron, OffsetDateTime nextRunAt) {
	}

	public record AutomationRuleRecord(
			UUID organizationId,
			UUID branchId,
			UUID id,
			String name,
			String triggerType,
			String conditionJson,
			String actionType,
			String actionConfigJson,
			String status,
			int retryLimit,
			String scheduleCron,
			OffsetDateTime nextRunAt,
			OffsetDateTime lockedUntil,
			String currentTriggerReference,
			OffsetDateTime createdAt,
			OffsetDateTime updatedAt,
			long version
	) {
	}

	public record AutomationExecutionRecord(
			UUID id, UUID ruleId, String status, String triggerReference, String errorMessage,
			String idempotencyKey, int attemptNumber, long ruleVersion,
			OffsetDateTime startedAt, OffsetDateTime finishedAt, OffsetDateTime occurredAt
	) {
	}

	public record AutomationTriggerEventRecord(
			UUID id, UUID organizationId, UUID branchId, String triggerType, String triggerReference,
			String payloadJson, String status, int attemptNumber, OffsetDateTime availableAt,
			OffsetDateTime lockedUntil, String lastError
	) {}

	public record AutomationDeadLetterRecord(
			UUID id, String source, UUID ruleId, String ruleName, String triggerType,
			String triggerReference, String errorMessage, int attemptNumber,
			OffsetDateTime occurredAt
	) {}

	public record SchedulerStatusRecord(
			UUID organizationId, boolean paused, String reason, OffsetDateTime pausedAt,
			UUID pausedBy, OffsetDateTime updatedAt
	) {}

	public record AutomationOperationsRecord(
			long dueRules, long pendingEvents, long retryEvents, long expiredLeases,
			long deadLetterEvents, long deadLetterExecutions, OffsetDateTime oldestPendingAt
	) {}

	public record AutomationOperationContext(UUID organizationId, UUID branchId) {}

	public record BookingReminderCandidate(
			UUID organizationId, UUID branchId, UUID ruleId, long ruleVersion,
			UUID bookingId, UUID clientId, UUID resourceId,
			OffsetDateTime startsAt, OffsetDateTime endsAt, int minutesBefore
	) {}
}
