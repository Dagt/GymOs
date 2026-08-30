package com.gymfort.connector.cloud;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.gymfort.connector.cloud.CloudModels.ActivationResponse;
import com.gymfort.connector.cloud.CloudModels.ClaimedJob;
import com.gymfort.connector.cloud.CloudModels.PersonCandidate;
import com.gymfort.connector.cloud.CloudModels.DeviceCandidate;
import com.gymfort.connector.cloud.CloudModels.AuthorizationRequest;
import com.gymfort.connector.cloud.CloudModels.AuthorizationResponse;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class GymFortCloudClient implements CloudGateway {
    private static final String CONNECTOR_KEY_HEADER = "X-GymFort-Connector-Key";
    private static final String CONNECTOR_SECRET_HEADER = "X-GymFort-Connector-Secret";

    private final URI baseUrl;
    private final String connectorKey;
    private final char[] connectorSecret;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public GymFortCloudClient(URI baseUrl, String connectorKey, char[] connectorSecret) {
        this(baseUrl, connectorKey, connectorSecret,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(), mapper());
    }

    GymFortCloudClient(
            URI baseUrl,
            String connectorKey,
            char[] connectorSecret,
            HttpClient httpClient,
            ObjectMapper objectMapper
    ) {
        this.baseUrl = baseUrl;
        this.connectorKey = connectorKey;
        this.connectorSecret = connectorSecret == null ? null : connectorSecret.clone();
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    public ActivationResponse activate(String activationCode) {
        return post("/api/v1/physical-connectors/activate",
                Map.of("connectorKey", connectorKey, "activationCode", activationCode),
                ActivationResponse.class, false);
    }

    @Override
    public List<ClaimedJob> claim(int limit, int leaseSeconds) {
        return post("/api/v1/physical-connectors/jobs/claim",
                Map.of("limit", limit, "leaseSeconds", leaseSeconds),
                new TypeReference<>() {}, true);
    }

    @Override
    public void complete(UUID jobId, UUID claimToken, String externalOperationRef) {
        post("/api/v1/physical-connectors/jobs/" + jobId + "/complete",
                Map.of("claimToken", claimToken, "externalOperationRef",
                        externalOperationRef == null ? "" : externalOperationRef),
                Void.class, true);
    }

    @Override
    public void fail(
            UUID jobId,
            UUID claimToken,
            String error,
            boolean retryable,
            int retryAfterSeconds
    ) {
        post("/api/v1/physical-connectors/jobs/" + jobId + "/fail",
                Map.of("claimToken", claimToken, "error", safeError(error),
                        "retryable", retryable, "retryAfterSeconds", retryAfterSeconds),
                Void.class, true);
    }

    @Override
    public void extendLease(UUID jobId, UUID claimToken, int leaseSeconds) {
        post("/api/v1/physical-connectors/jobs/" + jobId + "/lease",
                Map.of("claimToken", claimToken, "leaseSeconds", leaseSeconds), Void.class, true);
    }

    @Override
    public void heartbeat(String status, String connectorVersion, String instanceId, String lastError) {
        heartbeat(status, connectorVersion, instanceId, lastError, Set.of());
    }

    @Override
    public void heartbeat(
            String status, String connectorVersion, String instanceId,
            String lastError, Set<String> capabilities
    ) {
        post("/api/v1/physical-connectors/heartbeat",
                Map.of("status", status,
                        "connectorVersion", connectorVersion,
                        "instanceId", instanceId,
                        "capabilities", capabilities == null ? Set.of() : capabilities,
                        "lastError", lastError == null ? "" : safeError(lastError)),
                Void.class, true);
    }

    @Override
    public void publishPerson(PersonCandidate candidate) {
        post("/api/v1/physical-connectors/person-candidates", candidate, Void.class, true);
    }

    @Override
    public void publishDevice(DeviceCandidate candidate) {
        post("/api/v1/physical-connectors/device-candidates", candidate, Void.class, true);
    }

    @Override
    public AuthorizationResponse authorize(AuthorizationRequest request) {
        return post("/api/v1/physical-connectors/authorizations", request,
                AuthorizationResponse.class, true);
    }

    @Override
    public void reportActuation(
            UUID authorizationId, String status, String externalOperationRef, String error
    ) {
        java.util.LinkedHashMap<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("status", status);
        body.put("externalOperationRef", externalOperationRef);
        body.put("error", error);
        post("/api/v1/physical-connectors/authorizations/" + authorizationId + "/actuation",
                body, Void.class, true);
    }

    @Override
    public void close() {
        if (connectorSecret != null) {
            Arrays.fill(connectorSecret, '\0');
        }
    }

    private <T> T post(String path, Object body, Class<T> responseType, boolean authenticated) {
        HttpResponse<String> response = send(path, body, authenticated);
        if (responseType == Void.class || response.body() == null || response.body().isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(response.body(), responseType);
        } catch (JsonProcessingException exception) {
            throw new CloudApiException(response.statusCode(), "GymFort devolvió una respuesta JSON inválida.");
        }
    }

    private <T> T post(String path, Object body, TypeReference<T> responseType, boolean authenticated) {
        HttpResponse<String> response = send(path, body, authenticated);
        try {
            return objectMapper.readValue(response.body(), responseType);
        } catch (JsonProcessingException exception) {
            throw new CloudApiException(response.statusCode(), "GymFort devolvió una respuesta JSON inválida.");
        }
    }

    private HttpResponse<String> send(String path, Object body, boolean authenticated) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(baseUrl.resolve(path))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)));
            if (authenticated) {
                if (connectorSecret == null || connectorSecret.length == 0) {
                    throw new IllegalStateException("Falta activar el Connector o configurar GYMFORT_CONNECTOR_SECRET.");
                }
                builder.header(CONNECTOR_KEY_HEADER, connectorKey)
                        .header(CONNECTOR_SECRET_HEADER, new String(connectorSecret));
            }
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new CloudApiException(response.statusCode(),
                        "GymFort rechazó la solicitud con HTTP " + response.statusCode() + ".");
            }
            return response;
        } catch (CloudApiException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new CloudApiException(0, "La comunicación con GymFort fue interrumpida.");
        } catch (IOException exception) {
            throw new CloudApiException(0, "No fue posible comunicarse con GymFort.");
        }
    }

    private static ObjectMapper mapper() {
        return new ObjectMapper().registerModule(new JavaTimeModule());
    }

    private static String safeError(String error) {
        String safe = error == null || error.isBlank() ? "Fallo no especificado" : error;
        safe = safe.replaceAll("(?i)(secret|password|token|authorization)\\s*[:=]\\s*\\S+", "$1=<redacted>");
        return safe.substring(0, Math.min(safe.length(), 900));
    }
}
