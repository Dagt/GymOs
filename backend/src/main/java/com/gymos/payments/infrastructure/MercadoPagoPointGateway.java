package com.gymos.payments.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.gymos.common.api.BusinessException;
import com.gymos.payments.api.CardPresentGateway;
import com.gymos.payments.api.PaymentSecretResolver;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class MercadoPagoPointGateway implements CardPresentGateway {
    private final PaymentChannelConfigurationRepository configurations;
    private final PaymentSecretResolver secrets;
    private final RestClient client;

    public MercadoPagoPointGateway(PaymentChannelConfigurationRepository configurations,
            PaymentSecretResolver secrets, RestClient.Builder builder) {
        this.configurations = configurations;
        this.secrets = secrets;
        this.client = builder.baseUrl("https://api.mercadopago.com").build();
    }

    @Override public String provider() { return "MERCADO_PAGO_POINT"; }

    @Override
    public boolean available(UUID organizationId, UUID branchId) {
        return configurations.find(organizationId, branchId)
                .map(c -> c.mercadoPagoPointEnabled() && c.mercadoPagoTerminalId() != null
                        && c.mercadoPagoAccessTokenRef() != null)
                .orElse(false);
    }

    @Override
    public CreateOrderResult createOrder(CreateOrderRequest request) {
        var configuration = configuration(request.organizationId(), request.branchId());
        String terminalId = request.terminalId() == null || request.terminalId().isBlank()
                ? configuration.mercadoPagoTerminalId() : request.terminalId();
        String amount = BigDecimal.valueOf(request.amountCents(), 2).setScale(2, RoundingMode.UNNECESSARY).toPlainString();
        Map<String, Object> body = Map.of(
                "type", "point",
                "external_reference", externalReference(request.saleId()),
                "expiration_time", "PT16M",
                "description", request.description(),
                "transactions", Map.of("payments", new Object[]{Map.of("amount", amount)}),
                "config", Map.of(
                        "point", Map.of("terminal_id", terminalId, "print_on_terminal", "no_ticket"),
                        "payment_method", Map.of("default_type", "credit_card")
                )
        );
        JsonNode response;
        try {
            response = client.post().uri("/v1/orders")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + secrets.resolve(configuration.mercadoPagoAccessTokenRef()))
                    .header("X-Idempotency-Key", request.idempotencyKey())
                    .body(body).retrieve().body(JsonNode.class);
        } catch (RuntimeException exception) {
            throw new BusinessException("MERCADO_PAGO_POINT_CREATE_FAILED", "No fue posible enviar el cobro a la terminal Point.");
        }
        if (response == null || response.path("id").asText("").isBlank()) {
            throw new BusinessException("MERCADO_PAGO_POINT_INVALID_RESPONSE", "Mercado Pago no devolvió una orden válida.");
        }
        return new CreateOrderResult(response.path("id").asText(), response.path("status").asText("created"), terminalId);
    }

    @Override
    public OrderStatusResult getOrder(UUID organizationId, UUID branchId, String externalOrderId) {
        var configuration = configuration(organizationId, branchId);
        JsonNode response = client.get().uri("/v1/orders/{id}", externalOrderId)
                .header("Authorization", "Bearer " + secrets.resolve(configuration.mercadoPagoAccessTokenRef()))
                .retrieve().body(JsonNode.class);
        JsonNode payment = response == null ? null : response.path("transactions").path("payments").path(0);
        long cents = payment == null ? 0 : payment.path("amount").decimalValue().movePointRight(2).longValue();
        String status = payment == null ? "unknown" : payment.path("status").asText(response.path("status").asText("unknown"));
        return new OrderStatusResult(externalOrderId, status, cents, "MXN");
    }

    @Override
    public void cancel(UUID organizationId, UUID branchId, String externalOrderId) {
        var configuration = configuration(organizationId, branchId);
        client.post().uri("/v1/orders/{id}/cancel", externalOrderId)
                .header("Authorization", "Bearer " + secrets.resolve(configuration.mercadoPagoAccessTokenRef()))
                .retrieve().toBodilessEntity();
    }

    private PaymentChannelConfigurationRepository.Configuration configuration(UUID organizationId, UUID branchId) {
        var result = configurations.find(organizationId, branchId)
                .filter(PaymentChannelConfigurationRepository.Configuration::mercadoPagoPointEnabled)
                .orElseThrow(() -> new BusinessException("MERCADO_PAGO_POINT_DISABLED", "Mercado Pago Point no está habilitado para esta sucursal."));
        return result;
    }

    private String externalReference(UUID saleId) {
        String value = "gymos-sale-" + saleId;
        return value.length() <= 64 ? value : value.substring(0, 64);
    }
}
