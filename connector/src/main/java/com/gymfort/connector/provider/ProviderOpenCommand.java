package com.gymfort.connector.provider;

import java.util.UUID;

public record ProviderOpenCommand(
        UUID authorizationId,
        String externalEventId,
        String externalDeviceRef,
        String idempotencyKey
) {
}
