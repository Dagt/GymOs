package com.gymos.automations.api;

import com.gymos.common.security.GymOsPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.LOWEST_PRECEDENCE - 20)
public class AutomationPermissionFilter extends OncePerRequestFilter {
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/v1/automations");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof GymOsPrincipal principal)) {
            filterChain.doFilter(request, response);
            return;
        }
        String required = requiredPermission(request);
        if (allowed(principal.permissions(), required)) {
            filterChain.doFilter(request, response);
            return;
        }
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json");
        response.getWriter().write("{\"code\":\"FORBIDDEN\",\"message\":\"No tienes permiso para esta operación de automatizaciones.\"}");
    }

    private String requiredPermission(HttpServletRequest request) {
        String method = request.getMethod();
        String path = request.getRequestURI();
        if ("GET".equals(method)) return "automation.read";
        if (path.endsWith("/execute")) return "automation.execute";
        if (path.endsWith("/retry")) return "automation.retry";
        if (path.endsWith("/cancel")) return "automation.cancel";
        if (path.endsWith("/domain-events")) return "automation.scheduler.manage";
        return "automation.manage";
    }

    private boolean allowed(Set<String> permissions, String required) {
        return permissions.contains(required)
                || permissions.contains("automation.manage")
                || permissions.contains("platform.admin");
    }
}
