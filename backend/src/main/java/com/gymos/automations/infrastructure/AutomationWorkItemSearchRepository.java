package com.gymos.automations.infrastructure;

import com.gymos.automations.infrastructure.DurableAutomationRepository.WorkItemRecord;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class AutomationWorkItemSearchRepository {
    private final JdbcClient jdbcClient;

    public AutomationWorkItemSearchRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public Page search(
            UUID organizationId,
            UUID branchId,
            UUID ruleId,
            String status,
            String triggerReference,
            OffsetDateTime from,
            OffsetDateTime to,
            OffsetDateTime cursorCreatedAt,
            UUID cursorId,
            int limit
    ) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        List<WorkItemRecord> rows = jdbcClient.sql("""
                SELECT id, organization_id, branch_id, rule_id, idempotency_key,
                       trigger_reference, status, attempt_count, max_attempts,
                       scheduled_at, next_attempt_at, locked_until, started_at,
                       finished_at, last_error, effect_reference_id, created_at, updated_at
                FROM automation_work_item
                WHERE organization_id = :organizationId
                  AND (branch_id = :branchId OR branch_id IS NULL)
                  AND (:ruleId IS NULL OR rule_id = :ruleId)
                  AND (:status IS NULL OR status = :status)
                  AND (:triggerReference IS NULL OR trigger_reference ILIKE '%' || :triggerReference || '%')
                  AND (:fromDate IS NULL OR created_at >= :fromDate)
                  AND (:toDate IS NULL OR created_at < :toDate)
                  AND (
                      :cursorCreatedAt IS NULL
                      OR created_at < :cursorCreatedAt
                      OR (created_at = :cursorCreatedAt AND id < :cursorId)
                  )
                ORDER BY created_at DESC, id DESC
                LIMIT :fetchLimit
                """)
                .param("organizationId", organizationId)
                .param("branchId", branchId)
                .param("ruleId", ruleId)
                .param("status", status)
                .param("triggerReference", triggerReference)
                .param("fromDate", from)
                .param("toDate", to)
                .param("cursorCreatedAt", cursorCreatedAt)
                .param("cursorId", cursorId)
                .param("fetchLimit", safeLimit + 1)
                .query(this::map)
                .list();
        boolean hasMore = rows.size() > safeLimit;
        List<WorkItemRecord> items = hasMore ? rows.subList(0, safeLimit) : rows;
        WorkItemRecord last = items.isEmpty() ? null : items.get(items.size() - 1);
        return new Page(
                List.copyOf(items),
                hasMore,
                last == null ? null : last.createdAt(),
                last == null ? null : last.id()
        );
    }

    private WorkItemRecord map(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
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

    public record Page(
            List<WorkItemRecord> items,
            boolean hasMore,
            OffsetDateTime nextCursorCreatedAt,
            UUID nextCursorId
    ) {}
}
