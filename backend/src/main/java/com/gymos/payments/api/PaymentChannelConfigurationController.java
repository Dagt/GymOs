package com.gymos.payments.api;

import com.gymos.common.api.BusinessException;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.payments.infrastructure.PaymentChannelConfigurationRepository;
import com.gymos.payments.infrastructure.PaymentChannelConfigurationRepository.Configuration;
import com.gymos.payments.infrastructure.PaymentChannelConfigurationRepository.ConfigurationInput;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Locale;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payment-channels")
public class PaymentChannelConfigurationController {
    private final PaymentChannelConfigurationRepository repository;

    public PaymentChannelConfigurationController(PaymentChannelConfigurationRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    Configuration get(@AuthenticationPrincipal GymOsPrincipal principal) {
        requireManager(principal);
        return repository.find(principal.organizationId(), principal.branchId())
                .orElse(new Configuration(principal.organizationId(), principal.branchId(), false,
                        null, null, true, false, null, null, false, "SANDBOX", null));
    }

    @PutMapping
    Configuration update(@AuthenticationPrincipal GymOsPrincipal principal,
            @Valid @RequestBody UpdatePaymentChannelsRequest request) {
        requireOwnerOrAdmin(principal);
        String environment = request.environment().toUpperCase(Locale.ROOT);
        if (!environment.equals("SANDBOX") && !environment.equals("PRODUCTION")) {
            throw new BusinessException("PAYMENT_ENVIRONMENT_INVALID", "El entorno de pagos no es válido.");
        }
        if (request.mercadoPagoPointEnabled()
                && (blank(request.mercadoPagoAccessTokenRef()) || blank(request.mercadoPagoTerminalId()))) {
            throw new BusinessException("MERCADO_PAGO_POINT_CONFIGURATION_REQUIRED",
                    "Point requiere referencia de credencial e identificador de terminal.");
        }
        if (request.stripeKioskEnabled()
                && (blank(request.stripeSecretKeyRef()) || blank(request.stripeWebhookSecretRef()))) {
            throw new BusinessException("STRIPE_CONFIGURATION_REQUIRED",
                    "Stripe requiere referencias de Secret Key y Webhook Secret.");
        }
        if (request.stripeAutopayEnabled() && !request.stripeKioskEnabled()) {
            throw new BusinessException("STRIPE_AUTOPAY_REQUIRES_KIOSK",
                    "La domiciliación requiere Stripe habilitado en el kiosco.");
        }
        return repository.save(principal.organizationId(), principal.branchId(), new ConfigurationInput(
                request.mercadoPagoPointEnabled(), request.mercadoPagoAccessTokenRef(),
                request.mercadoPagoTerminalId(), request.externalTerminalManualEnabled(),
                request.stripeKioskEnabled(), request.stripeSecretKeyRef(),
                request.stripeWebhookSecretRef(), request.stripeAutopayEnabled(), environment
        ), principal.userId());
    }

    private void requireManager(GymOsPrincipal principal) {
        if (principal == null || !(principal.roles().contains("OWNER") || principal.roles().contains("ADMIN")
                || principal.permissions().contains("payments.configure") || principal.platformAdmin())) {
            throw new BusinessException("PAYMENT_CONFIGURATION_FORBIDDEN", "No tienes permiso para consultar esta configuración.");
        }
    }

    private void requireOwnerOrAdmin(GymOsPrincipal principal) { requireManager(principal); }
    private boolean blank(String value) { return value == null || value.isBlank(); }

    public record UpdatePaymentChannelsRequest(
            boolean mercadoPagoPointEnabled,
            @Size(max=240) String mercadoPagoAccessTokenRef,
            @Size(max=160) String mercadoPagoTerminalId,
            boolean externalTerminalManualEnabled,
            boolean stripeKioskEnabled,
            @Size(max=240) String stripeSecretKeyRef,
            @Size(max=240) String stripeWebhookSecretRef,
            boolean stripeAutopayEnabled,
            @NotBlank @Size(max=16) String environment
    ) {}
}
