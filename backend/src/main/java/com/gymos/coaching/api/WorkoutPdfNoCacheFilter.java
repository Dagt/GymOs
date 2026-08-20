package com.gymos.coaching.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Prevents browsers and intermediary caches from reusing an older generated workout PDF.
 * The renderer marker also makes it possible to verify which backend version answered.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class WorkoutPdfNoCacheFilter extends OncePerRequestFilter {

    static final String RENDERER_VERSION = "compact-dark-v3";

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return !"GET".equalsIgnoreCase(request.getMethod())
                || uri == null
                || !uri.matches("^/api/v1/workout-assignments/[^/]+/pdf$");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, must-revalidate, max-age=0");
        response.setHeader(HttpHeaders.PRAGMA, "no-cache");
        response.setDateHeader(HttpHeaders.EXPIRES, 0L);
        response.setHeader("X-GymOS-PDF-Renderer", RENDERER_VERSION);
        filterChain.doFilter(request, response);
    }
}
