package com.gymos.communications.api;

import com.gymos.common.api.Correlation;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.communications.application.OutboundDeliveryAdministrationService;
import com.gymos.communications.infrastructure.OutboundDeliveryAdministrationRepository.OutboundDeliveryRecord;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/communications/deliveries")
public class OutboundDeliveryAdministrationController {
    private final OutboundDeliveryAdministrationService service;

    public OutboundDeliveryAdministrationController(OutboundDeliveryAdministrationService service) {
        this.service = service;
    }

    @GetMapping
    List<OutboundDeliveryRecord> search(
            @AuthenticationPrincipal GymOsPrincipal principal,
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID clientId,
            @RequestParam(required = false) UUID ruleId,
            @RequestParam(defaultValue = "100") @Min(1) @Max(250) int limit
    ) {
        return service.search(principal, channel, status, clientId, ruleId, limit);
    }

    @PostMapping("/{messageId}/retry")
    ResponseEntity<Void> retry(
            @AuthenticationPrincipal GymOsPrincipal principal,
            @PathVariable UUID messageId,
            HttpServletRequest request
    ) {
        service.retry(principal, messageId, correlationId(request));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{messageId}/cancel")
    ResponseEntity<Void> cancel(
            @AuthenticationPrincipal GymOsPrincipal principal,
            @PathVariable UUID messageId,
            HttpServletRequest request
    ) {
        service.cancel(principal, messageId, correlationId(request));
        return ResponseEntity.noContent().build();
    }

    private String correlationId(HttpServletRequest request) {
        Object value = request.getAttribute(Correlation.ATTRIBUTE);
        return value == null ? "unknown" : value.toString();
    }
}
