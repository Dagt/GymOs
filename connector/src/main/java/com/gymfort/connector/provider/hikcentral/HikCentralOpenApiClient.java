package com.gymfort.connector.provider.hikcentral;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.gymfort.connector.provider.ProviderPage;
import com.gymfort.connector.provider.ProviderPerson;
import com.gymfort.connector.provider.ProviderDevice;
import com.gymfort.connector.provider.ProviderAccessEvent;
import com.gymfort.connector.provider.ProviderOpenCommand;
import com.gymfort.connector.provider.ProviderOpenResult;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class HikCentralOpenApiClient implements HikCentralApi {
    private static final String VERSION_PATH = "/artemis/api/common/v1/version";
    private static final String ACCESS_LEVELS_PATH = "/artemis/api/acs/v1/privilege/group";
    private static final String ACCESS_LEVEL_PERSONS_PATH =
            "/artemis/api/acs/v1/privilege/group/single/personList";
    private static final String ADD_PERSON_PATH =
            "/artemis/api/acs/v1/privilege/group/single/addPersons";
    private static final String DELETE_PERSON_PATH =
            "/artemis/api/acs/v1/privilege/group/single/deletePersons";

    private final URI baseUrl;
    private final String appKey;
    private final char[] appSecret;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final HikCentralOpenApiSigner signer;
    private final HikCentralCapabilityProfile profile;

    public HikCentralOpenApiClient(URI baseUrl, String appKey, char[] appSecret) {
        this(baseUrl, appKey, appSecret, null);
    }

    public HikCentralOpenApiClient(
            URI baseUrl,
            String appKey,
            char[] appSecret,
            Path trustedCertificatePath
    ) {
        this(baseUrl, appKey, appSecret, trustedCertificatePath, null);
    }

    public HikCentralOpenApiClient(
            URI baseUrl,
            String appKey,
            char[] appSecret,
            Path trustedCertificatePath,
            Path capabilityProfilePath
    ) {
        this(baseUrl, appKey, appSecret,
                HikCentralTls.httpClient(trustedCertificatePath),
                new ObjectMapper(), new HikCentralOpenApiSigner(), capabilityProfilePath);
    }

    HikCentralOpenApiClient(
            URI baseUrl,
            String appKey,
            char[] appSecret,
            HttpClient httpClient,
            ObjectMapper objectMapper,
            HikCentralOpenApiSigner signer
    ) {
        this(baseUrl, appKey, appSecret, httpClient, objectMapper, signer, null);
    }

    HikCentralOpenApiClient(
            URI baseUrl,
            String appKey,
            char[] appSecret,
            HttpClient httpClient,
            ObjectMapper objectMapper,
            HikCentralOpenApiSigner signer,
            Path capabilityProfilePath
    ) {
        this.baseUrl = baseUrl;
        this.appKey = appKey;
        this.appSecret = appSecret.clone();
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.signer = signer;
        this.profile = HikCentralCapabilityProfile.load(capabilityProfilePath, objectMapper);
    }

    @Override
    public String version() {
        JsonNode response = post(VERSION_PATH, Map.of());
        JsonNode data = response.path("data");
        for (String field : List.of("version", "versionName", "platformVersion")) {
            if (data.hasNonNull(field)) {
                return data.get(field).asText();
            }
        }
        return data.isTextual() ? data.asText() : "unknown";
    }

    @Override
    public Set<String> accessLevelIds() {
        Set<String> result = new HashSet<>();
        int page = 1;
        while (true) {
            JsonNode data = post(ACCESS_LEVELS_PATH, Map.of("pageNo", page, "pageSize", 500, "type", 1))
                    .path("data");
            JsonNode list = data.path("list");
            if (list.isArray()) {
                for (JsonNode item : list) {
                    if (item.hasNonNull("privilegeGroupId")) {
                        result.add(item.get("privilegeGroupId").asText());
                    }
                }
            }
            if (result.size() >= data.path("total").asInt(result.size()) || list.size() == 0) {
                return Set.copyOf(result);
            }
            page++;
        }
    }

    @Override
    public boolean accessLevelContains(String accessLevelRef, String personRef) {
        int page = 1;
        int seen = 0;
        while (true) {
            JsonNode data = post(ACCESS_LEVEL_PERSONS_PATH,
                    Map.of("pageNo", page, "pageSize", 500, "type", 1,
                            "privilegeGroupId", accessLevelRef)).path("data");
            JsonNode list = data.path("list");
            if (list.isArray()) {
                for (JsonNode item : list) {
                    seen++;
                    if (personRef.equals(item.path("id").asText())) {
                        return true;
                    }
                }
            }
            if (seen >= data.path("total").asInt(seen) || list.size() == 0) {
                return false;
            }
            page++;
        }
    }

    @Override
    public void addPerson(String accessLevelRef, String personRef) {
        mutateMembership(ADD_PERSON_PATH, accessLevelRef, personRef);
    }

    @Override
    public void removePerson(String accessLevelRef, String personRef) {
        mutateMembership(DELETE_PERSON_PATH, accessLevelRef, personRef);
    }

    @Override
    public ProviderPage<ProviderPerson> persons(String cursor, int limit) {
        var operation = profile.persons();
        if (operation == null) return ProviderPage.empty(cursor);
        SearchResult result = search(operation, cursor, limit);
        List<ProviderPerson> persons = new java.util.ArrayList<>();
        for (JsonNode item : result.items()) {
            persons.add(new ProviderPerson(
                    requiredField(operation, item, "id"), field(operation, item, "name"),
                    field(operation, item, "email"), field(operation, item, "phone"),
                    field(operation, item, "employeeNumber")));
        }
        return new ProviderPage<>(persons, result.nextCursor(), result.hasMore());
    }

    @Override
    public ProviderPage<ProviderDevice> devices(String cursor, int limit) {
        var operation = profile.devices();
        if (operation == null) return ProviderPage.empty(cursor);
        SearchResult result = search(operation, cursor, limit);
        List<ProviderDevice> devices = new java.util.ArrayList<>();
        for (JsonNode item : result.items()) {
            devices.add(new ProviderDevice(
                    requiredField(operation, item, "id"), field(operation, item, "name"),
                    defaultText(field(operation, item, "type"), "UNKNOWN"), Map.of()));
        }
        return new ProviderPage<>(devices, result.nextCursor(), result.hasMore());
    }

    @Override
    public ProviderPage<ProviderAccessEvent> events(String cursor, int limit) {
        var operation = profile.events();
        if (operation == null) return ProviderPage.empty(cursor);
        SearchResult result = search(operation, cursor, limit);
        List<ProviderAccessEvent> events = new java.util.ArrayList<>();
        for (JsonNode item : result.items()) {
            String occurredAt = requiredField(operation, item, "occurredAt");
            events.add(new ProviderAccessEvent(
                    requiredField(operation, item, "id"), field(operation, item, "personId"),
                    requiredField(operation, item, "deviceId"), parseTimestamp(operation, occurredAt),
                    field(operation, item, "decision"),
                    defaultText(field(operation, item, "eventType"), "ACCESS_ATTEMPT"), Map.of()));
        }
        return new ProviderPage<>(events, result.nextCursor(), result.hasMore());
    }

    @Override
    public ProviderOpenResult remoteOpen(ProviderOpenCommand command) {
        var operation = profile.remoteOpen();
        if (operation == null) {
            throw new HikCentralApiException("El perfil OpenAPI no configura apertura remota.", false);
        }
        JsonNode response = post(operation.path(), render(operation.request(), Map.of(
                "externalDeviceRef", command.externalDeviceRef(),
                "externalEventId", command.externalEventId(),
                "idempotencyKey", command.idempotencyKey())));
        String operationRef = operation.operationRefPointer() == null ? null
                : text(response.at(operation.operationRefPointer()));
        return new ProviderOpenResult(operationRef == null
                ? "hikcentral:open:" + command.authorizationId() : operationRef);
    }

    @Override public boolean supportsPersonSearch() { return profile.persons() != null; }
    @Override public boolean supportsDeviceSearch() { return profile.devices() != null; }
    @Override public boolean supportsEventSearch() { return profile.events() != null; }
    @Override public boolean supportsRemoteOpen() { return profile.remoteOpen() != null; }

    @Override
    public void close() {
        Arrays.fill(appSecret, '\0');
    }

    private void mutateMembership(String path, String accessLevelRef, String personRef) {
        post(path, Map.of(
                "privilegeGroupId", accessLevelRef,
                "type", 1,
                "list", List.of(Map.of("id", personRef))));
    }

    private SearchResult search(
            HikCentralCapabilityProfile.SearchOperation operation, String cursor, int limit
    ) {
        String effectiveCursor = cursor == null || cursor.isBlank() ? "1" : cursor;
        JsonNode response = post(operation.path(), render(operation.request(), Map.of(
                "cursor", effectiveCursor, "limit", Integer.toString(limit),
                "now", OffsetDateTime.now().toString())));
        JsonNode list = response.at(operation.listPointer());
        if (!list.isArray()) {
            throw new HikCentralApiException("El perfil OpenAPI no encontró la lista configurada.", false);
        }
        String paginationMode = operation.paginationMode() == null
                ? "NEXT_CURSOR" : operation.paginationMode().trim().toUpperCase(java.util.Locale.ROOT);
        String next;
        boolean hasMore;
        if ("PAGE_NUMBER".equals(paginationMode)) {
            int page;
            try {
                page = Integer.parseInt(effectiveCursor);
            } catch (NumberFormatException exception) {
                throw new HikCentralApiException("El cursor configurado no es un número de página válido.", false);
            }
            next = list.isEmpty() ? effectiveCursor : Integer.toString(page + 1);
            if (operation.hasMorePointer() != null) {
                hasMore = response.at(operation.hasMorePointer()).asBoolean(false);
            } else if (operation.totalPointer() != null) {
                long total = response.at(operation.totalPointer()).asLong(-1);
                if (total < 0) {
                    throw new HikCentralApiException("El perfil OpenAPI no obtuvo el total configurado.", false);
                }
                hasMore = (long) page * limit < total;
            } else {
                hasMore = list.size() >= limit;
            }
        } else {
            next = operation.nextCursorPointer() == null ? null
                    : text(response.at(operation.nextCursorPointer()));
            if (operation.hasMorePointer() != null) {
                hasMore = response.at(operation.hasMorePointer()).asBoolean(false);
            } else {
                hasMore = next != null && !next.equals(effectiveCursor);
            }
            if (hasMore && (next == null || next.equals(effectiveCursor))) {
                throw new HikCentralApiException(
                        "HikCentral indicó más resultados sin entregar un cursor nuevo.", false);
            }
        }
        return new SearchResult(java.util.stream.StreamSupport.stream(list.spliterator(), false).toList(),
                next == null ? effectiveCursor : next, hasMore);
    }

    private String requiredField(
            HikCentralCapabilityProfile.SearchOperation operation, JsonNode item, String name
    ) {
        String value = field(operation, item, name);
        if (value == null || value.isBlank()) {
            throw new HikCentralApiException("El perfil OpenAPI no obtuvo el campo requerido " + name + ".", false);
        }
        return value;
    }

    private String field(
            HikCentralCapabilityProfile.SearchOperation operation, JsonNode item, String name
    ) {
        String pointer = operation.fields().get(name);
        String value = pointer == null ? null : text(item.at(pointer));
        if (value == null || operation.valueMappings() == null) return value;
        Map<String, String> mappings = operation.valueMappings().get(name);
        return mappings == null ? value : mappings.getOrDefault(value, value);
    }

    private OffsetDateTime parseTimestamp(
            HikCentralCapabilityProfile.SearchOperation operation, String value
    ) {
        String format = operation.timestampFormat() == null
                ? "ISO_OFFSET_DATE_TIME" : operation.timestampFormat().trim().toUpperCase(java.util.Locale.ROOT);
        try {
            return switch (format) {
                case "ISO_INSTANT" -> Instant.parse(value).atOffset(ZoneOffset.UTC);
                case "ISO_LOCAL_DATE_TIME" -> LocalDateTime.parse(value)
                        .atZone(ZoneId.of(operation.timestampZone())).toOffsetDateTime();
                case "ISO_ZONED_DATE_TIME" -> ZonedDateTime.parse(value).toOffsetDateTime();
                case "EPOCH_MILLIS" -> Instant.ofEpochMilli(Long.parseLong(value)).atOffset(ZoneOffset.UTC);
                case "EPOCH_SECONDS" -> Instant.ofEpochSecond(Long.parseLong(value)).atOffset(ZoneOffset.UTC);
                default -> OffsetDateTime.parse(value);
            };
        } catch (RuntimeException exception) {
            throw new HikCentralApiException(
                    "El evento HikCentral contiene una fecha incompatible con timestampFormat=" + format + ".",
                    false, exception);
        }
    }

    private JsonNode render(JsonNode template, Map<String, String> values) {
        if (template.isObject()) {
            ObjectNode rendered = objectMapper.createObjectNode();
            template.properties().forEach(entry -> rendered.set(entry.getKey(), render(entry.getValue(), values)));
            return rendered;
        }
        if (template.isArray()) {
            ArrayNode rendered = objectMapper.createArrayNode();
            template.forEach(value -> rendered.add(render(value, values)));
            return rendered;
        }
        if (!template.isTextual()) return template.deepCopy();
        String text = template.asText();
        for (Map.Entry<String, String> value : values.entrySet()) {
            text = text.replace("${" + value.getKey() + "}", value.getValue());
        }
        if (text.matches("-?[0-9]+")) {
            try { return objectMapper.getNodeFactory().numberNode(Long.parseLong(text)); }
            catch (NumberFormatException ignored) { }
        }
        return TextNode.valueOf(text);
    }

    private static String text(JsonNode value) {
        return value == null || value.isMissingNode() || value.isNull() ? null : value.asText();
    }

    private static String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private record SearchResult(List<JsonNode> items, String nextCursor, boolean hasMore) { }

    private JsonNode post(String path, Object body) {
        byte[] json;
        try {
            json = objectMapper.writeValueAsBytes(body);
        } catch (IOException exception) {
            throw new IllegalStateException("No fue posible serializar una solicitud HikCentral.", exception);
        }
        long timestamp = System.currentTimeMillis();
        String nonce = UUID.randomUUID().toString();
        HikCentralOpenApiSigner.SignedHeaders signed = signer.sign(
                "POST", path, json, appKey, appSecret, timestamp, nonce);
        HttpRequest.Builder request = HttpRequest.newBuilder(baseUrl.resolve(path))
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofByteArray(json));
        signed.headers().forEach(request::header);
        try {
            HttpResponse<String> response = httpClient.send(request.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                boolean retryable = response.statusCode() >= 500 || response.statusCode() == 408
                        || response.statusCode() == 429;
                throw new HikCentralApiException(
                        "HikCentral OpenAPI respondió HTTP " + response.statusCode() + ".", retryable);
            }
            JsonNode parsed = objectMapper.readTree(response.body());
            if (parsed == null || !parsed.isObject()) {
                throw new HikCentralApiException(
                        "HikCentral OpenAPI devolvió una respuesta JSON inválida.", false);
            }
            if (!"0".equals(parsed.path("code").asText())) {
                String code = parsed.path("code").asText("UNKNOWN");
                String message = parsed.path("msg").asText("Solicitud rechazada");
                throw new HikCentralApiException(
                        "HikCentral rechazó la operación (" + safe(code) + "): " + safe(message), false);
            }
            return parsed;
        } catch (HikCentralApiException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new HikCentralApiException("La comunicación con HikCentral fue interrumpida.", true, exception);
        } catch (IOException exception) {
            throw new HikCentralApiException("No fue posible comunicarse con HikCentral OpenAPI.", true, exception);
        }
    }

    private static String safe(String value) {
        if (value == null) {
            return "unknown";
        }
        String normalized = value.replaceAll("[\\r\\n\\t]", " ");
        return normalized.substring(0, Math.min(normalized.length(), 240));
    }
}
