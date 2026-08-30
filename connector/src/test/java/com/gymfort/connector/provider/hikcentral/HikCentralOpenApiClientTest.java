package com.gymfort.connector.provider.hikcentral;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HikCentralOpenApiClientTest {
    @TempDir Path tempDirectory;
    private HttpServer server;
    private final List<RequestSnapshot> requests = new ArrayList<>();

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
    void callsTheOfficialVersionPrivilegeGroupAndMembershipContracts() {
        URI baseUrl = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
        try (HikCentralOpenApiClient client = new HikCentralOpenApiClient(
                baseUrl, "app-key", "app-secret".toCharArray())) {
            assertThat(client.version()).isEqualTo("2.6.1");
            assertThat(client.accessLevelIds())
                    .containsExactlyInAnyOrder("CENTRO_GENERAL", "CENTRO_SAUNA");
            assertThat(client.accessLevelContains("CENTRO_SAUNA", "person-42")).isTrue();
            client.addPerson("CENTRO_GENERAL", "person-42");
            client.removePerson("CENTRO_SAUNA", "person-42");
        }

        assertThat(requests).hasSize(5);
        assertThat(requests).allSatisfy(request -> {
            assertThat(request.method()).isEqualTo("POST");
            assertThat(request.signature()).isNotBlank();
            assertThat(request.signatureHeaders())
                    .isEqualTo("x-ca-key,x-ca-nonce,x-ca-timestamp");
            assertThat(request.contentMd5()).isNotBlank();
        });
        assertThat(requests.get(3).body())
                .contains("\"privilegeGroupId\":\"CENTRO_GENERAL\"")
                .contains("\"id\":\"person-42\"");
        assertThat(requests.get(4).path())
                .isEqualTo("/artemis/api/acs/v1/privilege/group/single/deletePersons");
    }

    @Test
    void capabilityProfileAdaptsDiscoveryEventsAndRemoteOpenWithoutRecompiling() throws Exception {
        Path profile = tempDirectory.resolve("hikcentral-profile.json");
        Files.writeString(profile, """
                {
                  "persons": {
                    "path": "/artemis/site/persons", "request": {"page": "${cursor}", "size": "${limit}"},
                    "listPointer": "/data/rows", "nextCursorPointer": "/data/next", "hasMorePointer": "/data/more",
                    "fields": {"id": "/id", "name": "/display", "email": "/mail"}
                  },
                  "devices": {
                    "path": "/artemis/site/devices", "request": {"page": "${cursor}", "size": "${limit}"},
                    "listPointer": "/data/rows", "nextCursorPointer": "/data/next", "hasMorePointer": "/data/more",
                    "fields": {"id": "/id", "name": "/display", "type": "/kind"}
                  },
                  "events": {
                    "path": "/artemis/site/events", "request": {"cursor": "${cursor}", "size": "${limit}"},
                    "listPointer": "/data/rows", "nextCursorPointer": "/data/next", "hasMorePointer": "/data/more",
                    "timestampFormat": "ISO_LOCAL_DATE_TIME", "timestampZone": "America/Mexico_City",
                    "valueMappings": {"decision": {"1": "GRANTED", "0": "DENIED"}},
                    "fields": {"id": "/id", "personId": "/person", "deviceId": "/device",
                               "occurredAt": "/when", "decision": "/decision", "eventType": "/kind"}
                  },
                  "remoteOpen": {
                    "path": "/artemis/site/open",
                    "request": {"door": "${externalDeviceRef}", "requestKey": "${idempotencyKey}"},
                    "operationRefPointer": "/data/operation"
                  }
                }
                """);
        URI baseUrl = URI.create("http://127.0.0.1:" + server.getAddress().getPort());

        try (HikCentralOpenApiClient client = new HikCentralOpenApiClient(
                baseUrl, "app-key", "app-secret".toCharArray(), null, profile)) {
            assertThat(client.persons(null, 25).items()).singleElement().satisfies(person -> {
                assertThat(person.externalPersonRef()).isEqualTo("person-42");
                assertThat(person.email()).isEqualTo("ana@example.test");
            });
            assertThat(client.devices(null, 25).items()).singleElement().satisfies(device ->
                    assertThat(device.externalDeviceRef()).isEqualTo("door-1"));
            assertThat(client.events(null, 25).items()).singleElement().satisfies(event -> {
                assertThat(event.externalEventId()).isEqualTo("event-1");
                assertThat(event.externalDeviceRef()).isEqualTo("door-1");
                assertThat(event.providerDecision()).isEqualTo("GRANTED");
                assertThat(event.occurredAt())
                        .isEqualTo(java.time.OffsetDateTime.parse("2026-08-29T18:00:00-06:00"));
            });
            var opened = client.remoteOpen(new com.gymfort.connector.provider.ProviderOpenCommand(
                    java.util.UUID.randomUUID(), "event-1", "door-1", "open-once"));
            assertThat(opened.externalOperationRef()).isEqualTo("operation-99");
        }

        assertThat(requests).extracting(RequestSnapshot::path).contains(
                "/artemis/site/persons", "/artemis/site/devices", "/artemis/site/events", "/artemis/site/open");
        assertThat(requests).filteredOn(request -> request.path().equals("/artemis/site/open"))
                .singleElement().satisfies(request -> assertThat(request.body())
                        .contains("\"door\":\"door-1\"")
                        .contains("\"requestKey\":\"open-once\""));
    }

    @Test
    void capabilityProfileRejectsPathsOutsideTheSignedArtemisSurface() throws Exception {
        Path profile = tempDirectory.resolve("unsafe-profile.json");
        Files.writeString(profile, """
                {"persons":{"path":"https://attacker.invalid/export","request":{},
                 "listPointer":"/data","fields":{"id":"/id"}}}
                """);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new HikCentralOpenApiClient(
                URI.create("http://127.0.0.1:" + server.getAddress().getPort()),
                "app-key", "app-secret".toCharArray(), null, profile))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("debe iniciar con /artemis/");
    }

    @Test
    void capabilityProfileSupportsPageNumberPaginationWithTotal() throws Exception {
        Path profile = tempDirectory.resolve("page-profile.json");
        Files.writeString(profile, """
                {"persons":{"path":"/artemis/site/paged-persons","request":{"page":"${cursor}","size":"${limit}"},
                 "listPointer":"/data/rows","paginationMode":"PAGE_NUMBER","totalPointer":"/data/total",
                 "fields":{"id":"/id","name":"/name"}}}
                """);
        URI baseUrl = URI.create("http://127.0.0.1:" + server.getAddress().getPort());

        try (HikCentralOpenApiClient client = new HikCentralOpenApiClient(
                baseUrl, "app-key", "app-secret".toCharArray(), null, profile)) {
            var first = client.persons(null, 1);
            assertThat(first.hasMore()).isTrue();
            assertThat(first.nextCursor()).isEqualTo("2");

            var second = client.persons(first.nextCursor(), 1);
            assertThat(second.hasMore()).isFalse();
            assertThat(second.nextCursor()).isEqualTo("3");
        }
        assertThat(requests).filteredOn(request -> request.path().equals("/artemis/site/paged-persons"))
                .extracting(RequestSnapshot::body)
                .containsExactly("{\"page\":1,\"size\":1}", "{\"page\":2,\"size\":1}");
    }

    @Test
    void capabilityProfileRejectsAStalledCursorInsteadOfLoopingForever() throws Exception {
        Path profile = tempDirectory.resolve("stalled-profile.json");
        Files.writeString(profile, """
                {"persons":{"path":"/artemis/site/stalled-persons","request":{"cursor":"${cursor}"},
                 "listPointer":"/data/rows","nextCursorPointer":"/data/next","hasMorePointer":"/data/more",
                 "fields":{"id":"/id"}}}
                """);
        URI baseUrl = URI.create("http://127.0.0.1:" + server.getAddress().getPort());

        try (HikCentralOpenApiClient client = new HikCentralOpenApiClient(
                baseUrl, "app-key", "app-secret".toCharArray(), null, profile)) {
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> client.persons("1", 25))
                    .isInstanceOf(HikCentralApiException.class)
                    .hasMessageContaining("sin entregar un cursor nuevo");
        }
    }

    private void respond(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        requests.add(new RequestSnapshot(
                exchange.getRequestMethod(), exchange.getRequestURI().getPath(), body,
                exchange.getRequestHeaders().getFirst("X-Ca-Signature"),
                exchange.getRequestHeaders().getFirst("X-Ca-Signature-Headers"),
                exchange.getRequestHeaders().getFirst("Content-MD5")));

        String response = switch (exchange.getRequestURI().getPath()) {
            case "/artemis/api/common/v1/version" ->
                    "{\"code\":\"0\",\"data\":{\"version\":\"2.6.1\"}}";
            case "/artemis/api/acs/v1/privilege/group" ->
                    "{\"code\":\"0\",\"data\":{\"total\":2,\"list\":["
                            + "{\"privilegeGroupId\":\"CENTRO_GENERAL\"},"
                            + "{\"privilegeGroupId\":\"CENTRO_SAUNA\"}]}}";
            case "/artemis/api/acs/v1/privilege/group/single/personList" ->
                    "{\"code\":\"0\",\"data\":{\"total\":1,\"list\":[{\"id\":\"person-42\"}]}}";
            case "/artemis/site/persons" ->
                    "{\"code\":\"0\",\"data\":{\"rows\":[{\"id\":\"person-42\",\"display\":\"Ana\",\"mail\":\"ana@example.test\"}],\"next\":\"2\",\"more\":false}}";
            case "/artemis/site/paged-persons" ->
                    "{\"code\":\"0\",\"data\":{\"rows\":[{\"id\":\"person-42\",\"name\":\"Ana\"}],\"total\":2}}";
            case "/artemis/site/stalled-persons" ->
                    "{\"code\":\"0\",\"data\":{\"rows\":[{\"id\":\"person-42\"}],\"next\":\"1\",\"more\":true}}";
            case "/artemis/site/devices" ->
                    "{\"code\":\"0\",\"data\":{\"rows\":[{\"id\":\"door-1\",\"display\":\"Torniquete\",\"kind\":\"TURNSTILE\"}],\"next\":\"2\",\"more\":false}}";
            case "/artemis/site/events" ->
                    "{\"code\":\"0\",\"data\":{\"rows\":[{\"id\":\"event-1\",\"person\":\"person-42\",\"device\":\"door-1\",\"when\":\"2026-08-29T18:00:00\",\"decision\":\"1\",\"kind\":\"FACE_MATCH\"}],\"next\":\"2\",\"more\":false}}";
            case "/artemis/site/open" ->
                    "{\"code\":\"0\",\"data\":{\"operation\":\"operation-99\"}}";
            default -> "{\"code\":\"0\",\"data\":{}}";
        };
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private record RequestSnapshot(
            String method,
            String path,
            String body,
            String signature,
            String signatureHeaders,
            String contentMd5
    ) {
    }
}
