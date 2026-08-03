package com.gymos.payments.api;

import com.gymos.common.security.GymOsPrincipal;
import com.gymos.payments.application.CardPresentPaymentService;
import com.gymos.payments.application.CardPresentPaymentService.StartCommand;
import com.gymos.payments.infrastructure.CardPresentPaymentRepository.Payment;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/card-present-payments")
public class CardPresentPaymentController {
    private final CardPresentPaymentService service;
    public CardPresentPaymentController(CardPresentPaymentService service) { this.service = service; }

    @PostMapping
    Payment start(@AuthenticationPrincipal GymOsPrincipal principal,
            @Valid @RequestBody StartCardPresentPaymentRequest request) {
        return service.start(principal, new StartCommand(request.saleId(), request.mode(), request.amountCents(),
                request.currency(), request.description(), request.idempotencyKey()));
    }

    @PostMapping("/{paymentId}/confirm-manual")
    Payment confirmManual(@AuthenticationPrincipal GymOsPrincipal principal, @PathVariable UUID paymentId,
            @Valid @RequestBody ConfirmManualPaymentRequest request) {
        return service.confirmManual(principal, paymentId, request.reference(), request.authorizationCode());
    }

    @GetMapping("/{paymentId}/status")
    Payment status(@AuthenticationPrincipal GymOsPrincipal principal, @PathVariable UUID paymentId) {
        return service.refresh(principal, paymentId);
    }

    public record StartCardPresentPaymentRequest(@NotNull UUID saleId,@NotBlank String mode,
            @Min(1) long amountCents,@NotBlank @Size(max=3) String currency,
            @NotBlank @Size(max=160) String description,@NotBlank @Size(max=180) String idempotencyKey){}
    public record ConfirmManualPaymentRequest(@NotBlank @Size(max=160) String reference,
            @Size(max=120) String authorizationCode){}
}
