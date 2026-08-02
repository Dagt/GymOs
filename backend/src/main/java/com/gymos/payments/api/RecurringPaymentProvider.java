package com.gymos.payments.api;

import java.time.OffsetDateTime;
import java.util.Map;

public interface RecurringPaymentProvider {
    boolean available();

    String provider();

    SetupResult createSetup(SetupRequest request);

    void revoke(String providerPaymentMethodId);

    record SetupRequest(
            String idempotencyKey,
            String customerReference,
            String description,
            Map<String, String> metadata,
            String successUrl,
            String cancelUrl
    ) {}

    record SetupResult(
            String providerCustomerId,
            String providerSetupId,
            String checkoutUrl,
            String clientSecret,
            OffsetDateTime expiresAt
    ) {}
}
