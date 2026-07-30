package com.gymos.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymos.common.api.ApiError;
import com.gymos.common.api.Correlation;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

public class StandardAccessDeniedHandler implements AccessDeniedHandler {
	private final ObjectMapper objectMapper;

	public StandardAccessDeniedHandler(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	@Override
	public void handle(
			HttpServletRequest request,
			HttpServletResponse response,
			AccessDeniedException accessDeniedException
	) throws IOException, ServletException {
		Object correlationId = request.getAttribute(Correlation.ATTRIBUTE);
		response.setStatus(HttpServletResponse.SC_FORBIDDEN);
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		objectMapper.writeValue(response.getOutputStream(), ApiError.of(
				"PERMISSION_DENIED",
				"No tienes permiso para realizar esta accion.",
				Map.of(),
				correlationId == null ? "unknown" : correlationId.toString()
		));
	}
}
