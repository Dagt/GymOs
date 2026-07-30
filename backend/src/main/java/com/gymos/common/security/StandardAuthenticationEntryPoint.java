package com.gymos.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymos.common.api.ApiError;
import com.gymos.common.api.Correlation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

public class StandardAuthenticationEntryPoint implements AuthenticationEntryPoint {
	private final ObjectMapper objectMapper;

	public StandardAuthenticationEntryPoint(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException) {
		try {
			Object correlationId = request.getAttribute(Correlation.ATTRIBUTE);
			response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
			response.setContentType(MediaType.APPLICATION_JSON_VALUE);
			objectMapper.writeValue(response.getOutputStream(), ApiError.of(
					"AUTHENTICATION_REQUIRED",
					"Debes iniciar sesión para continuar.",
					Map.of(),
					correlationId == null ? "unknown" : correlationId.toString()
			));
		} catch (Exception ignored) {
		}
	}
}
