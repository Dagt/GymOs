package com.gymos.automations.infrastructure;

import com.gymos.automations.infrastructure.AutomationRepository.AutomationRuleRecord;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class AutomationRuleSnapshotRepository {
	private final JdbcClient jdbcClient;

	public AutomationRuleSnapshotRepository(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	public AutomationRuleSnapshot store(UUID workItemId, AutomationRuleRecord rule) {
		jdbcClient.sql("""
				INSERT INTO automation_rule_snapshot (
				    work_item_id, organization_id, branch_id, rule_id, rule_version,
				    rule_name, trigger_type, condition_json, action_type,
				    action_config_json, retry_limit, created_at
				)
				VALUES (
				    :workItemId, :organizationId, :branchId, :ruleId, :ruleVersion,
				    :ruleName, :triggerType, CAST(:conditionJson AS jsonb), :actionType,
				    CAST(:actionConfigJson AS jsonb), :retryLimit, :createdAt
				)
				ON CONFLICT (work_item_id) DO NOTHING
				""")
				.param("workItemId", workItemId)
				.param("organizationId", rule.organizationId())
				.param("branchId", rule.branchId())
				.param("ruleId", rule.id())
				.param("ruleVersion", rule.version())
				.param("ruleName", rule.name())
				.param("triggerType", rule.triggerType())
				.param("conditionJson", rule.conditionJson())
				.param("actionType", rule.actionType())
				.param("actionConfigJson", rule.actionConfigJson())
				.param("retryLimit", rule.retryLimit())
				.param("createdAt", OffsetDateTime.now())
				.update();
		return snapshot(workItemId).orElseThrow();
	}

	public Optional<AutomationRuleSnapshot> snapshot(UUID workItemId) {
		return jdbcClient.sql("""
				SELECT work_item_id, organization_id, branch_id, rule_id, rule_version,
				       rule_name, trigger_type, condition_json::text AS condition_json,
				       action_type, action_config_json::text AS action_config_json,
				       retry_limit, created_at
				FROM automation_rule_snapshot
				WHERE work_item_id = :workItemId
				""")
				.param("workItemId", workItemId)
				.query((rs, rowNum) -> new AutomationRuleSnapshot(
						rs.getObject("work_item_id", UUID.class),
						rs.getObject("organization_id", UUID.class),
						rs.getObject("branch_id", UUID.class),
						rs.getObject("rule_id", UUID.class),
						rs.getLong("rule_version"),
						rs.getString("rule_name"),
						rs.getString("trigger_type"),
						rs.getString("condition_json"),
						rs.getString("action_type"),
						rs.getString("action_config_json"),
						rs.getInt("retry_limit"),
						rs.getObject("created_at", OffsetDateTime.class)
				))
				.optional();
	}

	public record AutomationRuleSnapshot(
			UUID workItemId,
			UUID organizationId,
			UUID branchId,
			UUID ruleId,
			long ruleVersion,
			String ruleName,
			String triggerType,
			String conditionJson,
			String actionType,
			String actionConfigJson,
			int retryLimit,
			OffsetDateTime createdAt
	) {}
}
