package com.gymos.automations.infrastructure;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class AutomationRuleManagementRepository {
    private final JdbcClient jdbcClient;

    public AutomationRuleManagementRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public boolean updateDefinition(
            UUID organizationId,
            UUID branchId,
            UUID ruleId,
            UUID actorUserId,
            String name,
            String triggerType,
            String conditionJson,
            String actionType,
            String actionConfigJson,
            int retryLimit,
            String scheduleCron,
            OffsetDateTime nextRunAt
    ) {
        OffsetDateTime now = OffsetDateTime.now();
        int updated = jdbcClient.sql("""
                UPDATE automation_rule
                SET name = :name,
                    trigger_type = :triggerType,
                    condition_json = CAST(:conditionJson AS jsonb),
                    action_type = :actionType,
                    action_config_json = CAST(:actionConfigJson AS jsonb),
                    retry_limit = :retryLimit,
                    schedule_cron = :scheduleCron,
                    next_run_at = :nextRunAt,
                    definition_version = definition_version + 1,
                    updated_at = :updatedAt,
                    updated_by = :updatedBy,
                    version = version + 1
                WHERE organization_id = :organizationId
                  AND (branch_id = :branchId OR branch_id IS NULL)
                  AND id = :ruleId
                  AND deleted_at IS NULL
                  AND archived_at IS NULL
                """)
                .param("name", name)
                .param("triggerType", triggerType)
                .param("conditionJson", conditionJson)
                .param("actionType", actionType)
                .param("actionConfigJson", actionConfigJson)
                .param("retryLimit", retryLimit)
                .param("scheduleCron", scheduleCron)
                .param("nextRunAt", nextRunAt)
                .param("updatedAt", now)
                .param("updatedBy", actorUserId)
                .param("organizationId", organizationId)
                .param("branchId", branchId)
                .param("ruleId", ruleId)
                .update();
        if (updated == 1) {
            captureRevision(organizationId, branchId, ruleId, actorUserId, "UPDATED");
        }
        return updated == 1;
    }

    public UUID duplicate(UUID organizationId, UUID branchId, UUID sourceRuleId, UUID actorUserId) {
        UUID id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        int inserted = jdbcClient.sql("""
                INSERT INTO automation_rule (
                    id, organization_id, branch_id, name, trigger_type, condition_json,
                    action_type, action_config_json, status, retry_limit, schedule_cron,
                    next_run_at, locked_until, created_at, updated_at, created_by,
                    updated_by, version, definition_version
                )
                SELECT :id, organization_id, branch_id, name || ' (copia)', trigger_type,
                       condition_json, action_type, action_config_json, 'DISABLED', retry_limit,
                       schedule_cron, NULL, NULL, :now, :now, :actorUserId, :actorUserId, 0, 1
                FROM automation_rule
                WHERE organization_id = :organizationId
                  AND (branch_id = :branchId OR branch_id IS NULL)
                  AND id = :sourceRuleId
                  AND deleted_at IS NULL
                """)
                .param("id", id)
                .param("now", now)
                .param("actorUserId", actorUserId)
                .param("organizationId", organizationId)
                .param("branchId", branchId)
                .param("sourceRuleId", sourceRuleId)
                .update();
        if (inserted != 1) {
            throw new IllegalStateException("Source automation rule is not available");
        }
        captureRevision(organizationId, branchId, id, actorUserId, "DUPLICATED");
        return id;
    }

    public boolean archive(UUID organizationId, UUID branchId, UUID ruleId, UUID actorUserId) {
        return jdbcClient.sql("""
                UPDATE automation_rule
                SET status = 'DISABLED', archived_at = :now, locked_until = NULL,
                    updated_at = :now, updated_by = :actorUserId, version = version + 1
                WHERE organization_id = :organizationId
                  AND (branch_id = :branchId OR branch_id IS NULL)
                  AND id = :ruleId
                  AND deleted_at IS NULL
                  AND archived_at IS NULL
                """)
                .param("now", OffsetDateTime.now())
                .param("actorUserId", actorUserId)
                .param("organizationId", organizationId)
                .param("branchId", branchId)
                .param("ruleId", ruleId)
                .update() == 1;
    }

    public boolean restore(UUID organizationId, UUID branchId, UUID ruleId, UUID actorUserId) {
        return jdbcClient.sql("""
                UPDATE automation_rule
                SET archived_at = NULL, updated_at = :now, updated_by = :actorUserId,
                    version = version + 1
                WHERE organization_id = :organizationId
                  AND (branch_id = :branchId OR branch_id IS NULL)
                  AND id = :ruleId
                  AND deleted_at IS NULL
                  AND archived_at IS NOT NULL
                """)
                .param("now", OffsetDateTime.now())
                .param("actorUserId", actorUserId)
                .param("organizationId", organizationId)
                .param("branchId", branchId)
                .param("ruleId", ruleId)
                .update() == 1;
    }

    public List<RuleRevision> revisions(UUID organizationId, UUID branchId, UUID ruleId) {
        return jdbcClient.sql("""
                SELECT id, rule_id, definition_version, name, trigger_type,
                       condition_json::text AS condition_json, action_type,
                       action_config_json::text AS action_config_json, retry_limit,
                       schedule_cron, next_run_at, change_type, changed_by, changed_at
                FROM automation_rule_revision
                WHERE organization_id = :organizationId
                  AND (branch_id = :branchId OR branch_id IS NULL)
                  AND rule_id = :ruleId
                ORDER BY definition_version DESC
                """)
                .param("organizationId", organizationId)
                .param("branchId", branchId)
                .param("ruleId", ruleId)
                .query((rs, rowNum) -> new RuleRevision(
                        rs.getObject("id", UUID.class),
                        rs.getObject("rule_id", UUID.class),
                        rs.getLong("definition_version"),
                        rs.getString("name"),
                        rs.getString("trigger_type"),
                        rs.getString("condition_json"),
                        rs.getString("action_type"),
                        rs.getString("action_config_json"),
                        rs.getInt("retry_limit"),
                        rs.getString("schedule_cron"),
                        rs.getObject("next_run_at", OffsetDateTime.class),
                        rs.getString("change_type"),
                        rs.getObject("changed_by", UUID.class),
                        rs.getObject("changed_at", OffsetDateTime.class)
                ))
                .list();
    }

    public Optional<ArchivedRule> archivedRule(UUID organizationId, UUID branchId, UUID ruleId) {
        return jdbcClient.sql("""
                SELECT id, name, archived_at
                FROM automation_rule
                WHERE organization_id = :organizationId
                  AND (branch_id = :branchId OR branch_id IS NULL)
                  AND id = :ruleId
                  AND deleted_at IS NULL
                  AND archived_at IS NOT NULL
                """)
                .param("organizationId", organizationId)
                .param("branchId", branchId)
                .param("ruleId", ruleId)
                .query((rs, rowNum) -> new ArchivedRule(
                        rs.getObject("id", UUID.class),
                        rs.getString("name"),
                        rs.getObject("archived_at", OffsetDateTime.class)
                ))
                .optional();
    }

    private void captureRevision(UUID organizationId, UUID branchId, UUID ruleId, UUID actorUserId, String changeType) {
        jdbcClient.sql("""
                INSERT INTO automation_rule_revision (
                    id, organization_id, branch_id, rule_id, definition_version,
                    name, trigger_type, condition_json, action_type, action_config_json,
                    retry_limit, schedule_cron, next_run_at, change_type, changed_by, changed_at
                )
                SELECT :id, organization_id, branch_id, id, definition_version,
                       name, trigger_type, condition_json, action_type, action_config_json,
                       retry_limit, schedule_cron, next_run_at, :changeType, :actorUserId, :changedAt
                FROM automation_rule
                WHERE organization_id = :organizationId
                  AND (branch_id = :branchId OR branch_id IS NULL)
                  AND id = :ruleId
                ON CONFLICT (rule_id, definition_version) DO NOTHING
                """)
                .param("id", UUID.randomUUID())
                .param("changeType", changeType)
                .param("actorUserId", actorUserId)
                .param("changedAt", OffsetDateTime.now())
                .param("organizationId", organizationId)
                .param("branchId", branchId)
                .param("ruleId", ruleId)
                .update();
    }

    public record RuleRevision(
            UUID id,
            UUID ruleId,
            long definitionVersion,
            String name,
            String triggerType,
            String conditionJson,
            String actionType,
            String actionConfigJson,
            int retryLimit,
            String scheduleCron,
            OffsetDateTime nextRunAt,
            String changeType,
            UUID changedBy,
            OffsetDateTime changedAt
    ) {}

    public record ArchivedRule(UUID id, String name, OffsetDateTime archivedAt) {}
}
