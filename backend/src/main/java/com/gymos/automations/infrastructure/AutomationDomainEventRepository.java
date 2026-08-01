package com.gymos.automations.infrastructure;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class AutomationDomainEventRepository {
    private final JdbcClient jdbcClient;

    public AutomationDomainEventRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public DomainEventRecord append(
            UUID organizationId,
            UUID branchId,
            String eventType,
            String eventId,
            int eventVersion,
            OffsetDateTime occurredAt,
            String payloadJson
    ) {
        UUID id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        try {
            jdbcClient.sql("""
                    INSERT INTO automation_domain_event (
                        id, organization_id, branch_id, event_type, event_id,
                        event_version, occurred_at, payload_json, status,
                        created_at, updated_at
                    ) VALUES (
                        :id, :organizationId, :branchId, :eventType, :eventId,
                        :eventVersion, :occurredAt, CAST(:payloadJson AS jsonb), 'PENDING',
                        :createdAt, :updatedAt
                    )
                    """)
                    .param("id", id)
                    .param("organizationId", organizationId)
                    .param("branchId", branchId)
                    .param("eventType", eventType)
                    .param("eventId", eventId)
                    .param("eventVersion", eventVersion)
                    .param("occurredAt", occurredAt)
                    .param("payloadJson", payloadJson)
                    .param("createdAt", now)
                    .param("updatedAt", now)
                    .update();
        } catch (DuplicateKeyException ignored) {
            return byIdentity(organizationId, eventType, eventId).orElseThrow();
        }
        return event(id).orElseThrow();
    }

    public Optional<DomainEventRecord> byIdentity(UUID organizationId, String eventType, String eventId) {
        return jdbcClient.sql(selectSql() + """
                WHERE organization_id = :organizationId
                  AND event_type = :eventType
                  AND event_id = :eventId
                """)
                .param("organizationId", organizationId)
                .param("eventType", eventType)
                .param("eventId", eventId)
                .query(this::map)
                .optional();
    }

    public Optional<DomainEventRecord> event(UUID id) {
        return jdbcClient.sql(selectSql() + " WHERE id = :id")
                .param("id", id)
                .query(this::map)
                .optional();
    }

    public List<DomainEventRecord> pending(int limit) {
        return jdbcClient.sql(selectSql() + """
                WHERE status = 'PENDING'
                ORDER BY occurred_at, created_at
                LIMIT :limit
                """)
                .param("limit", Math.max(1, Math.min(limit, 500)))
                .query(this::map)
                .list();
    }

    public boolean markProcessed(UUID id) {
        OffsetDateTime now = OffsetDateTime.now();
        return jdbcClient.sql("""
                UPDATE automation_domain_event
                SET status = 'PROCESSED', processed_at = :now,
                    last_error = NULL, updated_at = :now
                WHERE id = :id AND status = 'PENDING'
                """)
                .param("id", id)
                .param("now", now)
                .update() == 1;
    }

    public boolean markFailed(UUID id, String error) {
        return jdbcClient.sql("""
                UPDATE automation_domain_event
                SET status = 'FAILED', last_error = :error, updated_at = :now
                WHERE id = :id AND status = 'PENDING'
                """)
                .param("id", id)
                .param("error", truncate(error))
                .param("now", OffsetDateTime.now())
                .update() == 1;
    }

    private String selectSql() {
        return """
                SELECT id, organization_id, branch_id, event_type, event_id,
                       event_version, occurred_at, payload_json::text AS payload_json,
                       status, processed_at, last_error, created_at, updated_at
                FROM automation_domain_event
                """;
    }

    private DomainEventRecord map(java.sql.ResultSet rs, int rowNumber) throws java.sql.SQLException {
        return new DomainEventRecord(
                rs.getObject("id", UUID.class),
                rs.getObject("organization_id", UUID.class),
                rs.getObject("branch_id", UUID.class),
                rs.getString("event_type"),
                rs.getString("event_id"),
                rs.getInt("event_version"),
                rs.getObject("occurred_at", OffsetDateTime.class),
                rs.getString("payload_json"),
                rs.getString("status"),
                rs.getObject("processed_at", OffsetDateTime.class),
                rs.getString("last_error"),
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("updated_at", OffsetDateTime.class)
        );
    }

    private String truncate(String value) {
        if (value == null) return null;
        return value.length() <= 2000 ? value : value.substring(0, 2000);
    }

    public record DomainEventRecord(
            UUID id,
            UUID organizationId,
            UUID branchId,
            String eventType,
            String eventId,
            int eventVersion,
            OffsetDateTime occurredAt,
            String payloadJson,
            String status,
            OffsetDateTime processedAt,
            String lastError,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {}
}
