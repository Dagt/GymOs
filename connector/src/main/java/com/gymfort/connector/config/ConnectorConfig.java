package com.gymfort.connector.config;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;

public record ConnectorConfig(
        URI cloudBaseUrl,
        String connectorKey,
        String providerKey,
        String instanceId,
        Path stateDirectory,
        Duration pollInterval,
        Duration heartbeatInterval,
        Duration discoveryInterval,
        int claimLimit,
        int leaseSeconds,
        HikCentralConfig hikCentral
) {
    public static ConnectorConfig load(Path path) {
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(path)) {
            properties.load(input);
        } catch (IOException exception) {
            throw new IllegalArgumentException("No fue posible leer la configuración: " + path, exception);
        }

        URI cloudBaseUrl = requireHttpUri(properties, "cloud.baseUrl");
        String connectorKey = required(properties, "connector.key", 120);
        String providerKey = required(properties, "connector.provider", 60).toUpperCase(Locale.ROOT);
        String instanceId = value(properties, "connector.instanceId", defaultInstanceId(), 120);
        Path configuredStateDirectory = Path.of(value(properties, "connector.stateDirectory", "runtime", 500));
        Path stateDirectory = configuredStateDirectory.isAbsolute()
                ? configuredStateDirectory.normalize()
                : path.toAbsolutePath().getParent().resolve(configuredStateDirectory).normalize();
        Duration pollInterval = duration(properties, "connector.pollInterval", Duration.ofSeconds(2));
        Duration heartbeatInterval = duration(properties, "connector.heartbeatInterval", Duration.ofMinutes(1));
        Duration discoveryInterval = duration(properties, "connector.discoveryInterval", Duration.ofMinutes(15));
        int claimLimit = integer(properties, "connector.claimLimit", 20, 1, 50);
        int leaseSeconds = integer(properties, "connector.leaseSeconds", 90, 30, 300);

        HikCentralConfig hikCentral = null;
        if ("HIKCENTRAL".equals(providerKey)) {
            hikCentral = new HikCentralConfig(
                    requireHttpUri(properties, "hikcentral.baseUrl"),
                    required(properties, "hikcentral.appKey", 180),
                    csv(properties.getProperty("hikcentral.managedAccessLevelRefs")),
                    optionalPath(properties, "hikcentral.trustedCertificatePath", path),
                    optionalPath(properties, "hikcentral.capabilityProfilePath", path));
            if (!"https".equals(hikCentral.baseUrl().getScheme())) {
                throw new IllegalArgumentException("hikcentral.baseUrl debe utilizar https.");
            }
        } else if (!"SIMULATOR".equals(providerKey)) {
            throw new IllegalArgumentException("Proveedor no soportado por este build: " + providerKey);
        }

        if (pollInterval.isNegative() || pollInterval.isZero()) {
            throw new IllegalArgumentException("connector.pollInterval debe ser positivo.");
        }
        if (heartbeatInterval.compareTo(Duration.ofMinutes(5)) >= 0) {
            throw new IllegalArgumentException("connector.heartbeatInterval debe ser menor a cinco minutos.");
        }
        if (discoveryInterval.isNegative() || discoveryInterval.isZero()
                || discoveryInterval.compareTo(Duration.ofHours(24)) > 0) {
            throw new IllegalArgumentException("connector.discoveryInterval debe estar entre PT1S y PT24H.");
        }
        return new ConnectorConfig(
                cloudBaseUrl, connectorKey, providerKey, instanceId, stateDirectory,
                pollInterval, heartbeatInterval, discoveryInterval, claimLimit, leaseSeconds, hikCentral);
    }

    private static URI requireHttpUri(Properties properties, String key) {
        URI uri;
        try {
            uri = URI.create(required(properties, key, 500));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(key + " debe ser una URL válida.", exception);
        }
        if (!Set.of("http", "https").contains(uri.getScheme()) || uri.getHost() == null) {
            throw new IllegalArgumentException(key + " debe usar http o https y contener un host.");
        }
        if (uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null) {
            throw new IllegalArgumentException(key + " no debe incluir credenciales, query ni fragmento.");
        }
        String text = uri.toString();
        return URI.create(text.endsWith("/") ? text.substring(0, text.length() - 1) : text);
    }

    private static String required(Properties properties, String key, int max) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Falta la propiedad requerida " + key + ".");
        }
        return checked(value.trim(), key, max);
    }

    private static String value(Properties properties, String key, String fallback, int max) {
        String value = properties.getProperty(key);
        return checked(value == null || value.isBlank() ? fallback : value.trim(), key, max);
    }

    private static String checked(String value, String key, int max) {
        if (value.length() > max) {
            throw new IllegalArgumentException(key + " excede " + max + " caracteres.");
        }
        return value;
    }

    private static Duration duration(Properties properties, String key, Duration fallback) {
        String value = properties.getProperty(key);
        try {
            return value == null || value.isBlank() ? fallback : Duration.parse(value.trim());
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(key + " debe utilizar formato ISO-8601, por ejemplo PT30S.", exception);
        }
    }

    private static int integer(Properties properties, String key, int fallback, int min, int max) {
        String value = properties.getProperty(key);
        try {
            int parsed = value == null || value.isBlank() ? fallback : Integer.parseInt(value.trim());
            if (parsed < min || parsed > max) {
                throw new IllegalArgumentException(key + " debe estar entre " + min + " y " + max + ".");
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(key + " debe ser un entero.", exception);
        }
    }

    private static Set<String> csv(String value) {
        if (value == null || value.isBlank()) {
            return Set.of();
        }
        LinkedHashSet<String> entries = new LinkedHashSet<>();
        Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(entry -> !entry.isBlank())
                .forEach(entry -> {
                    if (entry.length() > 180) {
                        throw new IllegalArgumentException("Una referencia HikCentral excede 180 caracteres.");
                    }
                    entries.add(entry);
                });
        return Set.copyOf(entries);
    }

    private static Path optionalPath(Properties properties, String key, Path configPath) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            return null;
        }
        Path candidate = Path.of(checked(value.trim(), key, 500));
        return candidate.isAbsolute()
                ? candidate.normalize()
                : configPath.toAbsolutePath().getParent().resolve(candidate).normalize();
    }

    private static String defaultInstanceId() {
        String computer = System.getenv("COMPUTERNAME");
        return computer == null || computer.isBlank() ? "gymfort-local" : computer.trim();
    }

    public record HikCentralConfig(
            URI baseUrl,
            String appKey,
            Set<String> managedAccessLevelRefs,
            Path trustedCertificatePath,
            Path capabilityProfilePath
    ) {
        public HikCentralConfig {
            managedAccessLevelRefs = Set.copyOf(managedAccessLevelRefs);
        }
    }
}
