package com.gymos.selfservice.api;

import com.gymos.common.api.Correlation;
import com.gymos.selfservice.application.SelfServiceCheckoutService;
import com.gymos.selfservice.infrastructure.SelfServiceCheckoutRepository.PaymentIntentRecord;
import com.gymos.selfservice.infrastructure.SelfServiceCheckoutRepository.QuoteRecord;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/self-service")
public class SelfServiceCheckoutController {
    private final SelfServiceCheckoutService checkoutService;

    public SelfServiceCheckoutController(SelfServiceCheckoutService checkoutService) {
        this.checkoutService = checkoutService;
    }

    @PostMapping("/sessions/{sessionId}/quotes")
    QuoteRecord createQuote(
            @PathVariable UUID sessionId,
            @RequestHeader(name = "X-Self-Service-Session-Token", required = false) String sessionToken,
            @Valid @RequestBody CreateQuoteRequest request,
            HttpServletRequest servletRequest
    ) {
        return checkoutService.createQuote(
                sessionId,
                sessionToken,
                request.operationType(),
                request.membershipId(),
                request.planId(),
                correlationId(servletRequest)
        );
    }

    @PostMapping("/sessions/{sessionId}/payment-intents")
    PaymentIntentRecord createPaymentIntent(
            @PathVariable UUID sessionId,
            @RequestHeader(name = "X-Self-Service-Session-Token", required = false) String sessionToken,
            @Valid @RequestBody CreatePaymentIntentRequest request,
            HttpServletRequest servletRequest
    ) {
        return checkoutService.createPaymentIntent(
                sessionId,
                sessionToken,
                request.quoteId(),
                request.idempotencyKey(),
                request.successUrl(),
                request.cancelUrl(),
                correlationId(servletRequest)
        );
    }

    @PostMapping("/payments/webhook")
    PaymentIntentRecord webhook(
            @RequestHeader(name = "X-Payment-Signature", required = false) String signature,
            @RequestBody String payload,
            HttpServletRequest servletRequest
    ) {
        return checkoutService.handleWebhook(payload, signature, correlationId(servletRequest));
    }

    private String correlationId(HttpServletRequest request) {
        Object value = request.getAttribute(Correlation.ATTRIBUTE);
        return value == null ? "unknown" : value.toString();
    }

    public record CreateQuoteRequest(
            @NotBlank @Size(max = 40) String operationType,
            UUID membershipId,
            UUID planId
    ) {}

    public record CreatePaymentIntentRequest(
            @NotNull UUID quoteId,
            @NotBlank @Size(max = 160) String idempotencyKey,
            @NotBlank @Size(max = 1000) String successUrl,
            @NotBlank @Size(max = 1000) String cancelUrl
    ) {}
}
