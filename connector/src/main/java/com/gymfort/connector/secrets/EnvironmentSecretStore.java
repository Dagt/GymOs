package com.gymfort.connector.secrets;

import java.util.Map;
import java.util.Optional;

public final class EnvironmentSecretStore implements SecretStore {
    private static final Map<String, String> ENVIRONMENT_KEYS = Map.of(
            "cloud.connectorSecret", "GYMFORT_CONNECTOR_SECRET",
            "hikcentral.appSecret", "GYMFORT_HIKCENTRAL_APP_SECRET");

    @Override
    public Optional<char[]> read(String name) {
        String key = ENVIRONMENT_KEYS.get(name);
        if (key == null) {
            return Optional.empty();
        }
        String value = System.getenv(key);
        return value == null || value.isBlank() ? Optional.empty() : Optional.of(value.toCharArray());
    }

    @Override
    public void write(String name, char[] value) {
        throw new UnsupportedOperationException("Los secretos de entorno son de sólo lectura.");
    }
}
