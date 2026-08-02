package com.gymos.automations.infrastructure;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class OutboundMessageRepository {
    private final JdbcClient jdbcClient;

    public OutboundMessageRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public UUID enqueue(CreateOutboundMessageData data) {
        UUID id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        try {
            jdbcClient.sql("""
                    INSERT INTO outbound_message (
                        id, batch_id, organization_id, branch_id, rule_id, work_item_id,
                        client_id, channel, recipient, subject, body, template_code,
                        template_version, communication_type, status, idempotency_key,
                        next_attempt_at, created_at, updated_at
                    ) VALUES (
                        :id, :batchId, :organizationId, :branchId, :ruleId, :workItemId,
                        :clientId, :channel, :recipient, :subject, :body, :templateCode,
                        :templateVersion, :communicationType, :status, :idempotencyKey,
                        :nextAttemptAt, :createdAt, :updatedAt
                    )
                    """)
                    .param("id", id)
                    .param("batchId", data.batchId())
                    .param("organizationId", data.organizationId())
                    .param("branchId", data.branchId())
                    .param("ruleId", data.ruleId())
                    .param("workItemId", data.workItemId())
                    .param("clientId", data.clientId())
                    .param("channel", data.channel())
                    .param("recipient", data.recipient())
                    .param("subject", data.subject())
                    .param("body", data.body())
                    .param("templateCode", data.templateCode())
                    .param("templateVersion", Math.max(1, data.templateVersion()))
                    .param("communicationType", data.communicationType())
                    .param("status", data.status())
                    .param("idempotencyKey", data.idempotencyKey())
                    .param("nextAttemptAt", "PENDING".equals(data.status()) ? now : null)
                    .param("createdAt", now)
                    .param("updatedAt", now)
                    .update();
            return id;
        } catch (DuplicateKeyException ignored) {
            return jdbcClient.sql("""
                    SELECT id FROM outbound_message
                    WHERE organization_id = :organizationId
                      AND idempotency_key = :idempotencyKey
                    """)
                    .param("organizationId", data.organizationId())
                    .param("idempotencyKey", data.idempotencyKey())
                    .query(UUID.class)
                    .single();
        }
    }

    public record CreateOutboundMessageData(
            UUID batchId,
            UUID organizationId,
            UUID branchId,
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
            String idempotencyKey
    ) {}
}
