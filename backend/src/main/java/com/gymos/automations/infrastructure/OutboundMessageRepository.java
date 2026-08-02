package com.gymos.automations.infrastructure;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
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

    public List<OutboundMessageRecord> due(int limit) {
        return jdbcClient.sql("""
                SELECT id, organization_id, branch_id, client_id, channel, recipient,
                       subject, body, idempotency_key, attempt_count
                FROM outbound_message
                WHERE status IN ('PENDING', 'FAILED')
                  AND recipient IS NOT NULL
                  AND next_attempt_at IS NOT NULL
                  AND next_attempt_at <= now()
                ORDER BY next_attempt_at, created_at, id
                LIMIT :limit
                """)
                .param("limit", Math.max(1, Math.min(limit, 500)))
                .query((rs, rowNum) -> new OutboundMessageRecord(
                        rs.getObject("id", UUID.class),
                        rs.getObject("organization_id", UUID.class),
                        rs.getObject("branch_id", UUID.class),
                        rs.getObject("client_id", UUID.class),
                        rs.getString("channel"),
                        rs.getString("recipient"),
                        rs.getString("subject"),
                        rs.getString("body"),
                        rs.getString("idempotency_key"),
                        rs.getInt("attempt_count")
                ))
                .list();
    }

    public boolean claim(UUID id) {
        return jdbcClient.sql("""
                UPDATE outbound_message
                SET status = 'SENDING',
                    attempt_count = attempt_count + 1,
                    updated_at = now()
                WHERE id = :id
                  AND status IN ('PENDING', 'FAILED')
                  AND next_attempt_at <= now()
                """)
                .param("id", id)
                .update() == 1;
    }

    public void markAccepted(UUID id, String provider, String providerMessageId) {
        jdbcClient.sql("""
                UPDATE outbound_message
                SET status = 'ACCEPTED', provider = :provider,
                    provider_message_id = :providerMessageId,
                    accepted_at = now(), next_attempt_at = NULL,
                    last_error = NULL, updated_at = now()
                WHERE id = :id AND status = 'SENDING'
                """)
                .param("id", id)
                .param("provider", provider)
                .param("providerMessageId", providerMessageId)
                .update();
    }

    public void markDelivered(UUID id, String provider, String providerMessageId) {
        jdbcClient.sql("""
                UPDATE outbound_message
                SET status = 'DELIVERED', provider = :provider,
                    provider_message_id = :providerMessageId,
                    accepted_at = COALESCE(accepted_at, now()), delivered_at = now(),
                    next_attempt_at = NULL, last_error = NULL, updated_at = now()
                WHERE id = :id AND status IN ('SENDING', 'ACCEPTED')
                """)
                .param("id", id)
                .param("provider", provider)
                .param("providerMessageId", providerMessageId)
                .update();
    }

    public void markFailed(UUID id, String error, boolean retryable, int attemptCount) {
        OffsetDateTime nextAttempt = retryable && attemptCount < 6
                ? OffsetDateTime.now().plus(backoff(attemptCount))
                : null;
        jdbcClient.sql("""
                UPDATE outbound_message
                SET status = 'FAILED', failed_at = now(), last_error = :error,
                    next_attempt_at = :nextAttemptAt, updated_at = now()
                WHERE id = :id AND status = 'SENDING'
                """)
                .param("id", id)
                .param("error", error)
                .param("nextAttemptAt", nextAttempt)
                .update();
    }

    public Optional<OutboundMessageRecord> byProviderReference(String provider, String providerMessageId) {
        return jdbcClient.sql("""
                SELECT id, organization_id, branch_id, client_id, channel, recipient,
                       subject, body, idempotency_key, attempt_count
                FROM outbound_message
                WHERE provider = :provider AND provider_message_id = :providerMessageId
                """)
                .param("provider", provider)
                .param("providerMessageId", providerMessageId)
                .query((rs, rowNum) -> new OutboundMessageRecord(
                        rs.getObject("id", UUID.class),
                        rs.getObject("organization_id", UUID.class),
                        rs.getObject("branch_id", UUID.class),
                        rs.getObject("client_id", UUID.class),
                        rs.getString("channel"),
                        rs.getString("recipient"),
                        rs.getString("subject"),
                        rs.getString("body"),
                        rs.getString("idempotency_key"),
                        rs.getInt("attempt_count")
                ))
                .optional();
    }

    private Duration backoff(int attemptCount) {
        long minutes = Math.min(60, 1L << Math.max(0, Math.min(attemptCount - 1, 6)));
        return Duration.ofMinutes(minutes);
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

    public record OutboundMessageRecord(
            UUID id,
            UUID organizationId,
            UUID branchId,
            UUID clientId,
            String channel,
            String recipient,
            String subject,
            String body,
            String idempotencyKey,
            int attemptCount
    ) {}
}
