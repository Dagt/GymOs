package com.gymfort.connector.provider;

import java.util.Map;

public record ProviderDevice(
        String externalDeviceRef,
        String name,
        String deviceType,
        Map<String, String> metadata
) {
    public ProviderDevice {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
