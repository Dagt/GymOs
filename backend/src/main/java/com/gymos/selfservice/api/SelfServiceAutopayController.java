package com.gymos.selfservice.api;

import com.gymos.common.api.Correlation;
import com.gymos.selfservice.application.SelfServiceAutopayService;
import com.gymos.selfservice.application.SelfServiceAutopayService.SetupResponse;
import com.gymos.selfservice.infrastructure.SelfServiceAutopayRepository.MandateRecord;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/self-service/sessions/{sessionId}/autopay")
public class SelfServiceAutopayController {
    private final SelfServiceAutopayService autopayService;

    public SelfServiceAutopayController(SelfServiceAutopayService autopayService) {
        this.autopayService = autopayService;
    }

    @GetMapping("/readiness")
    ReadinessResponse readiness() {
        return new ReadinessResponse(autopayService.providerReady());
    }

    @GetMapping("/mandates")
    List<MandateRecord> mandates(
            @PathVariable UUID sessionId,
            @RequestHeader(name = "X-Self-Service-Session-Token", required = false) String sessionToken
    ) {
        return autopayService.mandates(sessionId, sessionToken);
    }

    @PostMapping("/setup")
    SetupResponse setup(
            @PathVariable UUID sessionId,
            @Valid @RequestBody SetupRequest request,
            @RequestHeader(name = "X-Self-Service-Session-Token", required = false) String sessionToken,
            HttpServletRequest servletRequest
    ) {
        return autopayService.startSetup(
                sessionId, sessionToken, request.membershipId(), request.consentAccepted(),
                request.idempotencyKey(), request.successUrl(), request.cancelUrl(),
                correlationId(servletRequest)
        );
    }

    @DeleteMapping("/mandates/{mandateId}")
    MandateRecord revoke(
            @PathVariable UUID sessionId,
            @PathVariable UUID mandateId,
            @Valid @RequestBody(required = false) RevokeRequest request,
            @RequestHeader(name = "X-Self-Service-Session-Token", required = false) String sessionToken,
            HttpServletRequest servletRequest
    ) {
        return autopayService.revoke(
                sessionId, sessionToken, mandateId,
                request == null ? null : request.reason(), correlationId(servletRequest)
        );
    }

    private String correlationId(HttpServletRequest request) {
        Object value = request.getAttribute(Correlation.ATTRIBUTE);
        return value == null ? "unknown" : value.toString();
    }

    public record ReadinessResponse(boolean ready) {}

    public record SetupRequest(
            @NotNull UUID membershipId,
            boolean consentAccepted,
            @NotBlank @Size(max = 160) String idempotencyKey,
            @NotBlank @Size(max = 500) String successUrl,
            @NotBlank @Size(max = 500) String cancelUrl
    ) {}

    public record RevokeRequest(@Size(max = 500) String reason) {}
}
