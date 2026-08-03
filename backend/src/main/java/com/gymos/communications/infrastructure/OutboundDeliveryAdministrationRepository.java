package com.gymos.communications.infrastructure;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class OutboundDeliveryAdministrationRepository {
    private final JdbcClient jdbcClient;

    public OutboundDeliveryAdministrationRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public int recoverStuckSending(OffsetDateTime threshold) {
        return jdbcClient.sql("""
                UPDATE outbound_message
                SET status = 'FAILED',
                    next_attempt_at = now(),
                    failed_at = now(),
                    last_error = 'DELIVERY_LEASE_EXPIRED: envio recuperado despues de una interrupcion',
                    updated_at = now()
                WHERE status = 'SENDING'
                  AND updated_at < :threshold
                """)
                .param("threshold", threshold)
                .update();
    }

    public List<OutboundDeliveryRecord> search(
            UUID organizationId,
            UUID branchId,
            String channel,
            String status,
            UUID clientId,
            UUID ruleId,
            int limit
    ) {
        return jdbcClient.sql("""
                SELECT id, batch_id, rule_id, work_item_id, client_id, channel,
                       recipient, subject, body, template_code, template_version,
                       communication_type, status, provider, provider_message_id,
                       attempt_count, next_attempt_at, accepted_at, delivered_at,
                       failed_at, last_error, created_at, updated_at
                FROM outbound_message
                WHERE organization_id = :organizationId
                  AND (:branchId IS NULL OR branch_id = :branchId)
                  AND (:channel IS NULL OR channel = :channel)
                  AND (:status IS NULL OR status = :status)
                  AND (:clientId IS NULL OR client_id = :clientId)
                  AND (:ruleId IS NULL OR rule_id = :ruleId)
                ORDER BY created_at DESC, id DESC
                LIMIT :limit
                """)
                .param("organizationId", organizationId)
                .param("branchId", branchId)
                .param("channel", blankToNull(channel))
                .param("status", blankToNull(status))
                .param("clientId", clientId)
                .param("ruleId", ruleId)
                .param("limit", Math.max(1, Math.min(limit, 250)))
                .query((rs, rowNum) -> new OutboundDeliveryRecord(
                        rs.getObject("id", UUID.class),
                        rs.getObject("batch_id", UUID.class),
                        rs.getObject("rule_id", UUID.class),
                        rs.getObject("work_item_id", UUID.class),
                        rs.getObject("client_id", UUID.class),
                        rs.getString("channel"),
                        rs.getString("recipient"),
                        rs.getString("subject"),
                        rs.getString("body"),
                        rs.getString("template_code"),
                        rs.getInt("template_version"),
                        rs.getString("communication_type"),
                        rs.getString("status"),
                        rs.getString("provider"),
                        rs.getString("provider_message_id"),
                        rs.getInt("attempt_count"),
                        rs.getObject("next_attempt_at", OffsetDateTime.class),
                        rs.getObject("accepted_at", OffsetDateTime.class),
                        rs.getObject("delivered_at", OffsetDateTime.class),
                        rs.getObject("failed_at", OffsetDateTime.class),
                        rs.getString("last_error"),
                        rs.getObject("created_at", OffsetDateTime.class),
                        rs.getObject("updated_at", OffsetDateTime.class)
                ))
                .list();
    }

    public boolean retry(UUID organizationId, UUID branchId, UUID messageId) {
        return jdbcClient.sql("""
                UPDATE outbound_message
                SET status = 'PENDING', next_attempt_at = now(), failed_at = NULL,
                    last_error = NULL, updated_at = now()
                WHERE id = :messageId
                  AND organization_id = :organizationId
                  AND (:branchId IS NULL OR branch_id = :branchId)
                  AND status IN ('FAILED', 'UNDELIVERABLE')
                  AND recipient IS NOT NULL
                """)
                .param("messageId", messageId)
                .param("organizationId", organizationId)
                .param("branchId", branchId)
                .update() == 1;
    }

    public boolean cancel(UUID organizationId, UUID branchId, UUID messageId) {
        return jdbcClient.sql("""
                UPDATE outbound_message
                SET status = 'CANCELLED', next_attempt_at = NULL, updated_at = now()
                WHERE id = :messageId
                  AND organization_id = :organizationId
                  AND (:branchId IS NULL OR branch_id = :branchId)
                  AND status IN ('PENDING', 'FAILED')
                """)
                .param("messageId", messageId)
                .param("organizationId", organizationId)
                .param("branchId", branchId)
                .update() == 1;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase();
    }

    public record OutboundDeliveryRecord(
            UUID id,
            UUID batchId,
            UUID ruleId,
            UUID workItemId,
            UUID clientId,
            String channel,
            String recipient,
            String subject,
            String body,
            String templateCode,
            int templateVersion,
            String communicationType,
            String status,
            String provider,
            String providerMessageId,
            int attemptCount,
            OffsetDateTime nextAttemptAt,
            OffsetDateTime acceptedAt,
            OffsetDateTime deliveredAt,
            OffsetDateTime failedAt,
            String lastError,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {}
}
