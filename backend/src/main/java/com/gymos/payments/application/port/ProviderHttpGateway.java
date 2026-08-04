package com.gymos.payments.application.port;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;

public interface ProviderHttpGateway {
	JsonNode exchange(String method, String url, Map<String, String> headers, String body, String contentType);
}
