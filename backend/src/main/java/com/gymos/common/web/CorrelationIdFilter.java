package com.gymos.common.web;

import com.gymos.common.api.Correlation;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class CorrelationIdFilter extends OncePerRequestFilter {
	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		String correlationId = request.getHeader(Correlation.HEADER);
		if (correlationId == null || correlationId.isBlank()) {
			correlationId = UUID.randomUUID().toString();
		}

		request.setAttribute(Correlation.ATTRIBUTE, correlationId);
		response.setHeader(Correlation.HEADER, correlationId);
		MDC.put(Correlation.ATTRIBUTE, correlationId);
		try {
			filterChain.doFilter(request, response);
		} finally {
			MDC.remove(Correlation.ATTRIBUTE);
		}
	}
}
