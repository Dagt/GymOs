package com.gymos.automations.infrastructure;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
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
				       status, retry_limit, schedule_cron, next_run_at, locked_until, created_at, updated_at
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
				       status, retry_limit, schedule_cron, next_run_at, locked_until, created_at, updated_at
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

	public UUID createExecution(UUID organizationId, UUID branchId, UUID ruleId, String status, String triggerReference, String errorMessage) {
		OffsetDateTime now = OffsetDateTime.now();
		UUID executionId = UUID.randomUUID();
		jdbcClient.sql("""
				INSERT INTO automation_execution (
				    id, organization_id, branch_id, rule_id, status, trigger_reference,
				    error_message, occurred_at, created_at
				)
				VALUES (
				    :id, :organizationId, :branchId, :ruleId, :status, :triggerReference,
				    :errorMessage, :occurredAt, :createdAt
				)
				""")
				.param("id", executionId)
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("ruleId", ruleId)
				.param("status", status)
				.param("triggerReference", triggerReference)
				.param("errorMessage", errorMessage)
				.param("occurredAt", now)
				.param("createdAt", now)
				.update();
		return executionId;
	}

	public List<AutomationExecutionRecord> executions(UUID organizationId, UUID branchId, UUID ruleId) {
		return jdbcClient.sql("""
				SELECT id, rule_id, status, trigger_reference, error_message, occurred_at
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
						resultSet.getObject("occurred_at", OffsetDateTime.class)
				))
				.list();
	}

	public List<AutomationRuleRecord> dueScheduledRules(OffsetDateTime now, int limit) {
		return jdbcClient.sql("""
				SELECT organization_id, branch_id, id, name, trigger_type, condition_json::text AS condition_json,
				       action_type, action_config_json::text AS action_config_json,
				       status, retry_limit, schedule_cron, next_run_at, locked_until, created_at, updated_at
				FROM automation_rule
				WHERE status = 'ENABLED'
				  AND trigger_type = 'SCHEDULED'
				  AND next_run_at IS NOT NULL
				  AND next_run_at <= :now
				  AND (locked_until IS NULL OR locked_until < :now)
				  AND deleted_at IS NULL
				ORDER BY next_run_at
				LIMIT :limit
				""")
				.param("now", now)
				.param("limit", limit)
				.query(this::mapRule)
				.list();
	}

	public boolean claimScheduledRule(UUID ruleId, OffsetDateTime now, OffsetDateTime lockedUntil) {
		int updated = jdbcClient.sql("""
				UPDATE automation_rule
				SET locked_until = :lockedUntil,
				    updated_at = :updatedAt,
				    version = version + 1
				WHERE id = :ruleId
				  AND status = 'ENABLED'
				  AND trigger_type = 'SCHEDULED'
				  AND next_run_at IS NOT NULL
				  AND next_run_at <= :now
				  AND (locked_until IS NULL OR locked_until < :now)
				  AND deleted_at IS NULL
				""")
				.param("ruleId", ruleId)
				.param("now", now)
				.param("lockedUntil", lockedUntil)
				.param("updatedAt", now)
				.update();
		return updated == 1;
	}

	public void rescheduleRule(UUID ruleId, OffsetDateTime nextRunAt) {
		jdbcClient.sql("""
				UPDATE automation_rule
				SET next_run_at = :nextRunAt,
				    locked_until = NULL,
				    updated_at = :updatedAt,
				    version = version + 1
				WHERE id = :ruleId
				  AND deleted_at IS NULL
				""")
				.param("ruleId", ruleId)
				.param("nextRunAt", nextRunAt)
				.param("updatedAt", OffsetDateTime.now())
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
				resultSet.getObject("created_at", OffsetDateTime.class),
				resultSet.getObject("updated_at", OffsetDateTime.class)
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
			OffsetDateTime createdAt,
			OffsetDateTime updatedAt
	) {
	}

	public record AutomationExecutionRecord(UUID id, UUID ruleId, String status, String triggerReference, String errorMessage, OffsetDateTime occurredAt) {
	}
}
