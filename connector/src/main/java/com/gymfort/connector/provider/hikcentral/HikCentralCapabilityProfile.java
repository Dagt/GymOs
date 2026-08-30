package com.gymfort.connector.provider.hikcentral;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Path;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Declarative adapter for OpenAPI operations whose path or JSON shape changes
 * between HikCentral packages. A site profile is validated locally and can be
 * updated without compiling a provider-specific Connector build.
 */
public record HikCentralCapabilityProfile(
        SearchOperation persons,
        SearchOperation devices,
        SearchOperation events,
        MutationOperation remoteOpen
) {
    public static HikCentralCapabilityProfile empty() {
        return new HikCentralCapabilityProfile(null, null, null, null);
    }

    public static HikCentralCapabilityProfile load(Path path, ObjectMapper mapper) {
        if (path == null) return empty();
        try {
            HikCentralCapabilityProfile profile = mapper.readValue(path.toFile(), HikCentralCapabilityProfile.class);
            profile.validate();
            return profile;
        } catch (IOException exception) {
            throw new IllegalArgumentException("No fue posible leer el perfil OpenAPI de HikCentral.", exception);
        }
    }

    private void validate() {
        if (persons != null) persons.validate("persons");
        if (devices != null) devices.validate("devices");
        if (events != null) events.validate("events");
        if (remoteOpen != null) remoteOpen.validate("remoteOpen");
    }

    public record SearchOperation(
            String path,
            JsonNode request,
            String listPointer,
            String nextCursorPointer,
            String hasMorePointer,
            String paginationMode,
            String totalPointer,
            Map<String, String> fields,
            Map<String, Map<String, String>> valueMappings,
            String timestampFormat,
            String timestampZone
    ) {
        private void validate(String name) {
            validatePath(path, name);
            if (request == null || !request.isObject()) {
                throw new IllegalArgumentException("hikcentral profile " + name + ".request debe ser un objeto JSON.");
            }
            validatePointer(listPointer, name + ".listPointer");
            if (nextCursorPointer != null) validatePointer(nextCursorPointer, name + ".nextCursorPointer");
            if (hasMorePointer != null) validatePointer(hasMorePointer, name + ".hasMorePointer");
            if (totalPointer != null) validatePointer(totalPointer, name + ".totalPointer");
            String normalizedPagination = paginationMode == null
                    ? "NEXT_CURSOR" : paginationMode.trim().toUpperCase(Locale.ROOT);
            if (!Set.of("NEXT_CURSOR", "PAGE_NUMBER").contains(normalizedPagination)) {
                throw new IllegalArgumentException(
                        "hikcentral profile " + name + ".paginationMode no es compatible.");
            }
            if (fields == null || !fields.containsKey("id")) {
                throw new IllegalArgumentException("hikcentral profile " + name + " requiere fields.id.");
            }
            fields.forEach((field, pointer) -> validatePointer(pointer, name + ".fields." + field));
            if (valueMappings != null) {
                valueMappings.forEach((field, mappings) -> {
                    if (field == null || field.isBlank() || field.length() > 120 || mappings == null) {
                        throw new IllegalArgumentException(
                                "hikcentral profile " + name + ".valueMappings es inválido.");
                    }
                    mappings.forEach((source, target) -> {
                        if (source == null || target == null || source.length() > 240 || target.length() > 240) {
                            throw new IllegalArgumentException(
                                    "hikcentral profile " + name + ".valueMappings excede el límite.");
                        }
                    });
                });
            }
            if (timestampFormat != null) {
                String normalized = timestampFormat.trim().toUpperCase(Locale.ROOT);
                if (!Set.of("ISO_OFFSET_DATE_TIME", "ISO_INSTANT", "ISO_LOCAL_DATE_TIME",
                        "ISO_ZONED_DATE_TIME", "EPOCH_MILLIS", "EPOCH_SECONDS").contains(normalized)) {
                    throw new IllegalArgumentException(
                            "hikcentral profile " + name + ".timestampFormat no es compatible.");
                }
                if ("ISO_LOCAL_DATE_TIME".equals(normalized)) {
                    if (timestampZone == null || timestampZone.isBlank()) {
                        throw new IllegalArgumentException(
                                "hikcentral profile " + name + ".timestampZone es obligatorio para fecha local.");
                    }
                    try {
                        ZoneId.of(timestampZone);
                    } catch (RuntimeException exception) {
                        throw new IllegalArgumentException(
                                "hikcentral profile " + name + ".timestampZone no es válido.", exception);
                    }
                }
            }
        }
    }

    public record MutationOperation(
            String path,
            JsonNode request,
            String operationRefPointer
    ) {
        private void validate(String name) {
            validatePath(path, name);
            if (request == null || !request.isObject()) {
                throw new IllegalArgumentException("hikcentral profile " + name + ".request debe ser un objeto JSON.");
            }
            if (operationRefPointer != null) {
                validatePointer(operationRefPointer, name + ".operationRefPointer");
            }
        }
    }

    private static void validatePath(String path, String name) {
        if (path == null || !path.startsWith("/artemis/") || path.contains("..") || path.contains("?")
                || path.contains("#") || path.contains("\\") || path.chars().anyMatch(Character::isWhitespace)
                || path.chars().anyMatch(Character::isISOControl) || path.length() > 300) {
            throw new IllegalArgumentException("hikcentral profile " + name + ".path debe iniciar con /artemis/.");
        }
    }

    private static void validatePointer(String pointer, String name) {
        if (pointer == null || !pointer.startsWith("/") || pointer.contains("..") || pointer.length() > 300) {
            throw new IllegalArgumentException("hikcentral profile " + name + " debe ser un JSON Pointer válido.");
        }
    }
}
