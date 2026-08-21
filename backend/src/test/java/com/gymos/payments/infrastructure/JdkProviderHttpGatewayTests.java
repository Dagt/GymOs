package com.gymos.payments.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymos.common.api.BusinessException;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JdkProviderHttpGatewayTests {
	@Test
	void includesSafeProviderMessageForRejectedRequests() throws Exception {
		HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/rejected", exchange -> {
			byte[] response = "{\"error\":{\"message\":\"Received unknown parameter: setup_intent_data[usage]\"}}".getBytes();
			exchange.getResponseHeaders().add("Content-Type", "application/json");
			exchange.sendResponseHeaders(400, response.length);
			exchange.getResponseBody().write(response);
			exchange.close();
		});
		server.start();
		try {
			var gateway = new JdkProviderHttpGateway(
					new ObjectMapper(), Duration.ofSeconds(1), Duration.ofSeconds(1)
			);
			String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/rejected";
			assertThatThrownBy(() -> gateway.exchange("POST", url, Map.of(), "mode=setup", "application/x-www-form-urlencoded"))
					.isInstanceOfSatisfying(BusinessException.class, exception -> {
						assertThat(exception.code()).isEqualTo("PAYMENT_PROVIDER_HTTP_ERROR");
						assertThat(exception.getMessage()).contains("Received unknown parameter: setup_intent_data[usage]");
					});
		} finally {
			server.stop(0);
		}
	}

	@Test
	void reportsTimeoutWithRecoverableProviderCode() throws Exception {
		HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/slow", exchange -> {
			try {
				Thread.sleep(250);
				exchange.sendResponseHeaders(200, 2);
				exchange.getResponseBody().write("{}".getBytes());
			} catch (InterruptedException exception) {
				Thread.currentThread().interrupt();
			} finally {
				exchange.close();
			}
		});
		server.start();
		try {
			var gateway = new JdkProviderHttpGateway(
					new ObjectMapper(), Duration.ofSeconds(1), Duration.ofMillis(50)
			);
			String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/slow";
			assertThatThrownBy(() -> gateway.exchange("GET", url, Map.of(), null, null))
					.isInstanceOfSatisfying(BusinessException.class, exception ->
							assertThat(exception.code()).isEqualTo("PAYMENT_PROVIDER_TIMEOUT"));
		} finally {
			server.stop(0);
		}
	}
}
