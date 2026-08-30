package com.gymfort.connector.cloud;

import static org.assertj.core.api.Assertions.assertThat;

import com.gymfort.connector.cloud.CloudModels.ClaimedJob;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GymFortCloudClientTest {
    private HttpServer server;
    private final List<RequestSnapshot> requests = new ArrayList<>();
    private final UUID jobId = UUID.randomUUID();
    private final UUID clientId = UUID.randomUUID();
    private final UUID claimToken = UUID.randomUUID();

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::respond);
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void usesTheCloudContractAndAuthenticatesEveryOperationalCall() {
        URI baseUrl = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
        try (GymFortCloudClient client = new GymFortCloudClient(
                baseUrl, "centro-connector", "cloud-secret".toCharArray())) {
            assertThat(client.activate("activation-code").providerKey()).isEqualTo("HIKCENTRAL");
            List<ClaimedJob> jobs = client.claim(20, 90);
            assertThat(jobs).singleElement().satisfies(job -> {
                assertThat(job.id()).isEqualTo(jobId);
                assertThat(job.clientId()).isEqualTo(clientId);
                assertThat(job.claimToken()).isEqualTo(claimToken);
                assertThat(job.desiredAccessLevelRefs())
                        .containsExactly("CENTRO_GENERAL", "CENTRO_SAUNA");
            });
            client.complete(jobId, claimToken, "hikcentral:done");
            client.extendLease(jobId, claimToken, 90);
            client.heartbeat("HEALTHY", "0.1.0", "centro-01", null,
                    java.util.Set.of("EVENT_POLLING", "REMOTE_OPEN"));
        }

        assertThat(requests).hasSize(5);
        assertThat(requests.getFirst().connectorSecret()).isNull();
        assertThat(requests.subList(1, requests.size())).allSatisfy(request -> {
            assertThat(request.connectorKey()).isEqualTo("centro-connector");
            assertThat(request.connectorSecret()).isEqualTo("cloud-secret");
        });
        assertThat(requests.get(2).body())
                .contains("\"claimToken\":\"" + claimToken + "\"")
                .contains("\"externalOperationRef\":\"hikcentral:done\"");
        assertThat(requests.get(4).body())
                .contains("\"EVENT_POLLING\"")
                .contains("\"REMOTE_OPEN\"");
    }

    private void respond(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        requests.add(new RequestSnapshot(
                exchange.getRequestURI().getPath(), body,
                exchange.getRequestHeaders().getFirst("X-GymFort-Connector-Key"),
                exchange.getRequestHeaders().getFirst("X-GymFort-Connector-Secret")));
        String response = switch (exchange.getRequestURI().getPath()) {
            case "/api/v1/physical-connectors/activate" -> """
                    {"connectorId":"00000000-0000-0000-0000-000000000001",
                     "connectorKey":"centro-connector","providerKey":"HIKCENTRAL",
                     "connectorSecret":"issued-secret","activatedAt":"2026-08-29T12:00:00-06:00"}
                    """;
            case "/api/v1/physical-connectors/jobs/claim" -> """
                    [{"id":"%s","idempotencyKey":"membership:1:revision:2","action":"UPSERT",
                      "clientId":"%s","externalPersonRef":"person-42",
                      "desiredAccessLevelRefs":["CENTRO_GENERAL","CENTRO_SAUNA"],
                      "status":"PROCESSING","attempts":1,"retryable":true,
                      "leaseUntil":"2026-08-29T12:02:00-06:00","claimToken":"%s"}]
                    """.formatted(jobId, clientId, claimToken);
            default -> "{}";
        };
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private record RequestSnapshot(
            String path,
            String body,
            String connectorKey,
            String connectorSecret
    ) {
    }
}
