package com.gymos.communications.api;

import java.util.UUID;

/**
 * Provider-neutral contract for one outbound communication channel.
 * Implementations may use SMTP, a transactional email API, Meta Cloud API,
 * Twilio or another provider without leaking provider details into Automations.
 */
public interface OutboundChannelSender {
    String channel();

    boolean available();

    SendResult send(SendCommand command);

    record SendCommand(
            UUID messageId,
            UUID organizationId,
            UUID branchId,
            UUID clientId,
            String recipient,
            String subject,
            String body,
            String idempotencyKey
    ) {}

    record SendResult(
            String provider,
            String providerMessageId,
            DeliveryState state,
            String errorCode,
            String errorMessage,
            boolean retryable
    ) {
        public static SendResult accepted(String provider, String providerMessageId) {
            return new SendResult(provider, providerMessageId, DeliveryState.ACCEPTED, null, null, false);
        }

        public static SendResult delivered(String provider, String providerMessageId) {
            return new SendResult(provider, providerMessageId, DeliveryState.DELIVERED, null, null, false);
        }

        public static SendResult failed(String provider, String code, String message, boolean retryable) {
            return new SendResult(provider, null, DeliveryState.FAILED, code, message, retryable);
        }
    }

    enum DeliveryState {
        ACCEPTED,
        DELIVERED,
        FAILED
    }
}
