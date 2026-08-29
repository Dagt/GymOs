package com.gymfort.connector.provider;

import java.util.Set;

public record ProviderHealth(
        boolean healthy,
        String providerVersion,
        Set<ProviderCapability> capabilities,
        String detail
) {
    public ProviderHealth {
        capabilities = capabilities == null ? Set.of() : Set.copyOf(capabilities);
        detail = sanitize(detail);
    }

    private static String sanitize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.replaceAll("(?i)(secret|password|token|authorization)\\s*[:=]\\s*\\S+", "$1=<redacted>");
        return normalized.substring(0, Math.min(normalized.length(), 500));
    }
}
