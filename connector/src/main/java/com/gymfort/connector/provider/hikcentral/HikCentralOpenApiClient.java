package com.gymfort.connector.provider.hikcentral;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
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

    public HikCentralOpenApiClient(URI baseUrl, String appKey, char[] appSecret) {
        this(baseUrl, appKey, appSecret,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(),
                new ObjectMapper(), new HikCentralOpenApiSigner());
    }

    HikCentralOpenApiClient(
            URI baseUrl,
            String appKey,
            char[] appSecret,
            HttpClient httpClient,
            ObjectMapper objectMapper,
            HikCentralOpenApiSigner signer
    ) {
        this.baseUrl = baseUrl;
        this.appKey = appKey;
        this.appSecret = appSecret.clone();
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.signer = signer;
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
    public void close() {
        Arrays.fill(appSecret, '\0');
    }

    private void mutateMembership(String path, String accessLevelRef, String personRef) {
        post(path, Map.of(
                "privilegeGroupId", accessLevelRef,
                "type", 1,
                "list", List.of(Map.of("id", personRef))));
    }

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
