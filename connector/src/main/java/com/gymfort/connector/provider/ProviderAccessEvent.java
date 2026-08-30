package com.gymfort.connector.provider;

import java.time.OffsetDateTime;
import java.util.Map;

public record ProviderAccessEvent(
        String externalEventId,
        String externalPersonRef,
        String externalDeviceRef,
        OffsetDateTime occurredAt,
        String providerDecision,
        String eventType,
        Map<String, String> metadata
) {
    public ProviderAccessEvent {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
