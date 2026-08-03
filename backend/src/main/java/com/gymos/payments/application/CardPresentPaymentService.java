package com.gymos.payments.application;

import com.gymos.common.api.BusinessException;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.payments.api.CardPresentGateway;
import com.gymos.payments.infrastructure.CardPresentPaymentRepository;
import com.gymos.payments.infrastructure.CardPresentPaymentRepository.Payment;
import com.gymos.payments.infrastructure.PaymentChannelConfigurationRepository;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CardPresentPaymentService {
    private final PaymentChannelConfigurationRepository configurations;
    private final CardPresentPaymentRepository payments;
    private final CardPresentGateway point;

    public CardPresentPaymentService(PaymentChannelConfigurationRepository configurations,
            CardPresentPaymentRepository payments, CardPresentGateway point) {
        this.configurations = configurations;
        this.payments = payments;
        this.point = point;
    }

    @Transactional
    public Payment start(GymOsPrincipal principal, StartCommand command) {
        if (command.amountCents() <= 0) throw new BusinessException("PAYMENT_AMOUNT_INVALID", "El importe debe ser mayor a cero.");
        var config = configurations.find(principal.organizationId(), principal.branchId())
                .orElseThrow(() -> new BusinessException("PAYMENT_CHANNELS_NOT_CONFIGURED", "La sucursal no tiene medios de pago configurados."));
        String mode = command.mode().toUpperCase(Locale.ROOT);
        if (mode.equals("EXTERNAL_TERMINAL_MANUAL")) {
            if (!config.externalTerminalManualEnabled()) throw new BusinessException("MANUAL_TERMINAL_DISABLED", "El cobro manual con terminal está deshabilitado.");
            return payments.create(principal.organizationId(), principal.branchId(), command.saleId(), mode,
                    command.amountCents(), command.currency(), command.idempotencyKey(), null, null, "PENDING");
        }
        if (!mode.equals("MERCADO_PAGO_POINT")) throw new BusinessException("CARD_PRESENT_MODE_INVALID", "Modalidad de terminal no soportada.");
        if (!config.mercadoPagoPointEnabled()) throw new BusinessException("MERCADO_PAGO_POINT_DISABLED", "Mercado Pago Point está deshabilitado.");
        var created = point.createOrder(new CardPresentGateway.CreateOrderRequest(
                principal.organizationId(), principal.branchId(), command.saleId(), command.amountCents(),
                command.currency(), command.description(), command.idempotencyKey(), config.mercadoPagoTerminalId()));
        return payments.create(principal.organizationId(), principal.branchId(), command.saleId(), mode,
                command.amountCents(), command.currency(), command.idempotencyKey(), created.externalOrderId(),
                created.terminalId(), "PENDING");
    }

    @Transactional
    public Payment confirmManual(GymOsPrincipal principal, UUID paymentId, String reference, String authorizationCode) {
        if (reference == null || reference.isBlank()) throw new BusinessException("MANUAL_PAYMENT_REFERENCE_REQUIRED", "Captura la referencia de la terminal.");
        return payments.confirmManual(principal.organizationId(), principal.branchId(), paymentId,
                principal.userId(), reference.trim(), authorizationCode == null ? null : authorizationCode.trim());
    }

    @Transactional
    public Payment refresh(GymOsPrincipal principal, UUID paymentId) {
        Payment payment = payments.find(principal.organizationId(), principal.branchId(), paymentId)
                .orElseThrow(() -> new BusinessException("CARD_PRESENT_PAYMENT_NOT_FOUND", "El cobro no existe."));
        if (!payment.mode().equals("MERCADO_PAGO_POINT") || payment.externalOrderId() == null) return payment;
        var status = point.getOrder(principal.organizationId(), principal.branchId(), payment.externalOrderId());
        if (status.amountCents() > 0 && status.amountCents() != payment.amountCents()) {
            payments.updateStatus(principal.organizationId(), principal.branchId(), paymentId, "FAILED", "El importe reportado por Mercado Pago no coincide.");
        } else {
            payments.updateStatus(principal.organizationId(), principal.branchId(), paymentId, normalize(status.status()), null);
        }
        return payments.find(principal.organizationId(), principal.branchId(), paymentId).orElseThrow();
    }

    private String normalize(String status) {
        String value = status == null ? "" : status.toLowerCase(Locale.ROOT);
        if (value.contains("approved") || value.contains("processed")) return "APPROVED";
        if (value.contains("reject") || value.contains("failed")) return "REJECTED";
        if (value.contains("cancel")) return "CANCELLED";
        return "PENDING";
    }

    public record StartCommand(UUID saleId,String mode,long amountCents,String currency,String description,String idempotencyKey){}
}
