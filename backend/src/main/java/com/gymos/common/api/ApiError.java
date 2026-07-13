package com.gymos.common.api;

import java.time.Instant;
import java.util.Map;

public record ApiError(
		String code,
		String message,
		Map<String, Object> details,
		String correlationId,
		Instant timestamp
) {
	public static ApiError of(String code, String message, Map<String, Object> details, String correlationId) {
		return new ApiError(code, message, details == null ? Map.of() : details, correlationId, Instant.now());
	}
}
