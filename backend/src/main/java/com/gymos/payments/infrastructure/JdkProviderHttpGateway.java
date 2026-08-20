package com.gymos.payments.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymos.common.api.BusinessException;
import com.gymos.payments.application.port.ProviderHttpGateway;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

@Component
class JdkProviderHttpGateway implements ProviderHttpGateway {
	private final HttpClient httpClient;
	private final ObjectMapper objectMapper;
	private final Duration requestTimeout;

	@Autowired
	JdkProviderHttpGateway(ObjectMapper objectMapper) {
		this(objectMapper, Duration.ofSeconds(8), Duration.ofSeconds(20));
	}

	JdkProviderHttpGateway(ObjectMapper objectMapper, Duration connectTimeout, Duration requestTimeout) {
		this.objectMapper = objectMapper;
		this.requestTimeout = requestTimeout;
		this.httpClient = HttpClient.newBuilder()
				.connectTimeout(connectTimeout)
				.followRedirects(HttpClient.Redirect.NEVER)
				.build();
	}

	@Override
	public JsonNode exchange(String method, String url, Map<String, String> headers, String body, String contentType) {
		try {
			HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
					.timeout(requestTimeout)
					.header("Accept", "application/json");
			headers.forEach(builder::header);
			if (contentType != null) builder.header("Content-Type", contentType);
			String resolvedBody = body == null ? "" : body;
			builder.method(method, resolvedBody.isEmpty()
					? HttpRequest.BodyPublishers.noBody()
					: HttpRequest.BodyPublishers.ofString(resolvedBody));
			HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() < 200 || response.statusCode() >= 300) {
				throw new BusinessException(
						"PAYMENT_PROVIDER_HTTP_ERROR",
						"El proveedor de pagos rechazo la operacion (HTTP " + response.statusCode() + ")."
				);
			}
			return response.body() == null || response.body().isBlank()
					? objectMapper.createObjectNode()
					: objectMapper.readTree(response.body());
		} catch (BusinessException exception) {
			throw exception;
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new BusinessException("PAYMENT_PROVIDER_INTERRUPTED", "La comunicacion con el proveedor fue interrumpida.");
		} catch (HttpTimeoutException exception) {
			throw new BusinessException("PAYMENT_PROVIDER_TIMEOUT", "El proveedor de pagos no respondio a tiempo.");
		} catch (Exception exception) {
			throw new BusinessException("PAYMENT_PROVIDER_UNAVAILABLE", "No fue posible comunicarse con el proveedor de pagos.");
		}
	}
}
