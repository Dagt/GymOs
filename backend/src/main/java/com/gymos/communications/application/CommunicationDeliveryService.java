package com.gymos.communications.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommunicationDeliveryService {
    private static final Set<String> EVENTS = Set.of(
            "ACCEPTED", "DELIVERED", "READ", "BOUNCED", "REJECTED", "FAILED"
    );

    private final JdbcClient jdbcClient;

    public CommunicationDeliveryService(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Transactional
    public boolean record(DeliveryEvent event) {
        String eventType = event.eventType().trim().toUpperCase(Locale.ROOT);
        if (!EVENTS.contains(eventType)) {
            throw new IllegalArgumentException("Unsupported communication delivery event " + eventType);
        }
        UUID messageId = resolveMessage(event);
        try {
            jdbcClient.sql("""
                    INSERT INTO communication_delivery_event (
                        id, outbound_message_id, provider, provider_event_id,
                        event_type, payload_hash, occurred_at, received_at
                    ) VALUES (
                        :id, :messageId, :provider, :providerEventId,
                        :eventType, :payloadHash, :occurredAt, now()
                    )
                    """)
                    .param("id", UUID.randomUUID())
                    .param("messageId", messageId)
                    .param("provider", event.provider())
                    .param("providerEventId", event.providerEventId())
                    .param("eventType", eventType)
                    .param("payloadHash", sha256(event.rawPayload()))
                    .param("occurredAt", event.occurredAt() == null ? OffsetDateTime.now() : event.occurredAt())
                    .update();
        } catch (DuplicateKeyException duplicate) {
            return false;
        }
        apply(messageId, eventType, event.errorMessage());
        return true;
    }

    private UUID resolveMessage(DeliveryEvent event) {
        if (event.outboundMessageId() != null) return event.outboundMessageId();
        if (event.provider() == null || event.providerMessageId() == null) {
            throw new IllegalArgumentException("Delivery event requires message id or provider message identity");
        }
        return jdbcClient.sql("""
                SELECT id FROM outbound_message
                WHERE provider = :provider AND provider_message_id = :providerMessageId
                """)
                .param("provider", event.provider())
                .param("providerMessageId", event.providerMessageId())
                .query(UUID.class)
                .optional()
                .orElseThrow(() -> new IllegalArgumentException("Outbound message not found"));
    }

    private void apply(UUID messageId, String eventType, String errorMessage) {
        switch (eventType) {
            case "ACCEPTED" -> jdbcClient.sql("""
                    UPDATE outbound_message
                    SET status = CASE WHEN status IN ('PENDING','SENDING','FAILED') THEN 'ACCEPTED' ELSE status END,
                        accepted_at = COALESCE(accepted_at, now()), updated_at = now()
                    WHERE id = :id
                    """).param("id", messageId).update();
            case "DELIVERED", "READ" -> jdbcClient.sql("""
                    UPDATE outbound_message
                    SET status = 'DELIVERED', delivered_at = COALESCE(delivered_at, now()),
                        last_error = NULL, next_attempt_at = NULL, updated_at = now()
                    WHERE id = :id AND status NOT IN ('CANCELLED','UNDELIVERABLE')
                    """).param("id", messageId).update();
            case "BOUNCED", "REJECTED", "FAILED" -> jdbcClient.sql("""
                    UPDATE outbound_message
                    SET status = 'UNDELIVERABLE', failed_at = COALESCE(failed_at, now()),
                        last_error = :error, next_attempt_at = NULL, updated_at = now()
                    WHERE id = :id AND status <> 'DELIVERED'
                    """)
                    .param("id", messageId)
                    .param("error", errorMessage == null ? eventType : eventType + ": " + errorMessage)
                    .update();
            default -> throw new IllegalStateException(eventType);
        }
    }

    private String sha256(String value) {
        if (value == null) return null;
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    public record DeliveryEvent(
            UUID outboundMessageId,
            String provider,
            String providerMessageId,
            String providerEventId,
            String eventType,
            OffsetDateTime occurredAt,
            String errorMessage,
            String rawPayload
    ) {}
}
