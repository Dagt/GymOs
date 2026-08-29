package com.gymfort.connector.provider.hikcentral;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HikCentralOpenApiClientTest {
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
