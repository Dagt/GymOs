package com.gymos.payments.api;

import java.time.OffsetDateTime;
import java.util.Map;

public interface OnlinePaymentGateway {
    String provider();
    boolean available();
    CreateIntentResult createIntent(CreateIntentRequest request);
    WebhookResult verifyWebhook(String payload, String signature);

    record CreateIntentRequest(
            String idempotencyKey,
            long amountCents,
            String currency,
            String description,
            Map<String, String> metadata,
            String successUrl,
            String cancelUrl
    ) {}

    record CreateIntentResult(
            String providerIntentId,
            String status,
            String checkoutUrl,
            String clientSecret,
            OffsetDateTime expiresAt
    ) {}

    record WebhookResult(
            String providerIntentId,
            String status,
            long amountCents,
            String currency,
            String eventId,
            OffsetDateTime occurredAt
    ) {}
}
