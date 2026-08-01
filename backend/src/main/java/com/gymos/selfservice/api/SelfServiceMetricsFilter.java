package com.gymos.selfservice.api;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.LOWEST_PRECEDENCE - 10)
public class SelfServiceMetricsFilter extends OncePerRequestFilter {
    private final MeterRegistry meterRegistry;

    public SelfServiceMetricsFilter(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/v1/self-service/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            filterChain.doFilter(request, response);
        } finally {
            String operation = operation(request);
            String outcome = outcome(response.getStatus());
            meterRegistry.counter(
                    "gymos.selfservice.requests",
                    "operation", operation,
                    "method", request.getMethod(),
                    "outcome", outcome
            ).increment();
            sample.stop(meterRegistry.timer(
                    "gymos.selfservice.request.duration",
                    "operation", operation,
                    "method", request.getMethod(),
                    "outcome", outcome
            ));
        }
    }

    private String operation(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path.contains("/verify")) return "verify";
        if (path.contains("/summary")) return "summary";
        if (path.contains("/contact")) return "contact_update";
        if (path.contains("/resources") && path.contains("/bookings")) return "booking_create";
        if (path.contains("/resources")) return "resources";
        if (path.contains("/renew")) return "membership_renew";
        if (path.contains("/change-plan")) return "membership_change_plan";
        if (path.endsWith("/end")) return "session_end";
        if (path.contains("/sessions")) return "session";
        return "other";
    }

    private String outcome(int status) {
        if (status >= 500) return "server_error";
        if (status >= 400) return "client_error";
        if (status >= 300) return "redirect";
        return "success";
    }
}
