package com.gymos.payments.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.gymos.common.api.BusinessException;
import com.gymos.payments.api.OnlinePaymentGateway;
import com.gymos.payments.api.PaymentSecretResolver;
import com.gymos.payments.api.RecurringPaymentProvider;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

@Primary
@Component
public class StripePaymentGateway implements OnlinePaymentGateway, RecurringPaymentProvider {
    private final PaymentChannelConfigurationRepository configurations;
    private final PaymentSecretResolver secrets;
    private final RestClient client;

    public StripePaymentGateway(PaymentChannelConfigurationRepository configurations,
            PaymentSecretResolver secrets, RestClient.Builder builder) {
        this.configurations = configurations;
        this.secrets = secrets;
        this.client = builder.baseUrl("https://api.stripe.com").build();
    }

    @Override public String provider() { return "STRIPE"; }
    @Override public boolean available() { return true; }

    @Override
    public CreateIntentResult createIntent(CreateIntentRequest request) {
        Context context = context(request.metadata());
        var configuration = configuration(context.organizationId(), context.branchId(), false);
        MultiValueMap<String,String> form = new LinkedMultiValueMap<>();
        form.add("mode", "payment");
        form.add("success_url", request.successUrl());
        form.add("cancel_url", request.cancelUrl());
        form.add("line_items[0][price_data][currency]", request.currency().toLowerCase());
        form.add("line_items[0][price_data][product_data][name]", request.description());
        form.add("line_items[0][price_data][unit_amount]", Long.toString(request.amountCents()));
        form.add("line_items[0][quantity]", "1");
        form.add("payment_intent_data[metadata][gymos_intent]", request.idempotencyKey());
        request.metadata().forEach((key,value) -> form.add("metadata["+key+"]", value));
        JsonNode response = post(configuration, "/v1/checkout/sessions", request.idempotencyKey(), form);
        return new CreateIntentResult(response.path("id").asText(), response.path("payment_status").asText("unpaid"),
                response.path("url").asText(null), null, OffsetDateTime.now().plusMinutes(30));
    }

    @Override
    public SetupResult createSetup(SetupRequest request) {
        Context context = context(request.metadata());
        var configuration = configuration(context.organizationId(), context.branchId(), true);
        MultiValueMap<String,String> form = new LinkedMultiValueMap<>();
        form.add("mode", "setup");
        form.add("currency", "mxn");
        form.add("success_url", request.successUrl());
        form.add("cancel_url", request.cancelUrl());
        form.add("client_reference_id", request.customerReference());
        form.add("setup_intent_data[metadata][gymos_mandate]", request.idempotencyKey());
        request.metadata().forEach((key,value) -> form.add("metadata["+key+"]", value));
        JsonNode response = post(configuration, "/v1/checkout/sessions", request.idempotencyKey(), form);
        return new SetupResult(response.path("customer").asText(null), response.path("setup_intent").asText(null),
                response.path("url").asText(null), null, OffsetDateTime.now().plusMinutes(30));
    }

    @Override
    public void revoke(String providerPaymentMethodId) {
        if (providerPaymentMethodId == null || providerPaymentMethodId.isBlank()) return;
        // La revocación local impide nuevos cargos. El desacoplamiento remoto se ejecutará
        // con el contexto del mandato desde el servicio de kiosco cuando exista la referencia del Customer.
    }

    @Override
    public WebhookResult verifyWebhook(String payload, String signature) {
        JsonNode event;
        try { event = new com.fasterxml.jackson.databind.ObjectMapper().readTree(payload); }
        catch (Exception exception) { throw new BusinessException("STRIPE_WEBHOOK_INVALID", "El webhook Stripe no contiene JSON válido."); }
        JsonNode object = event.path("data").path("object");
        String organization = object.path("metadata").path("organizationId").asText(null);
        String branch = object.path("metadata").path("branchId").asText(null);
        if (organization == null || branch == null) throw new BusinessException("STRIPE_WEBHOOK_CONTEXT_REQUIRED", "El webhook no contiene contexto GymOS.");
        var configuration = configuration(UUID.fromString(organization), UUID.fromString(branch), false);
        verifySignature(payload, signature, secrets.resolve(configuration.stripeWebhookSecretRef()));
        long amount = object.path("amount_total").asLong(object.path("amount").asLong(0));
        String currency = object.path("currency").asText("mxn").toUpperCase();
        String status = switch (event.path("type").asText()) {
            case "checkout.session.completed", "payment_intent.succeeded" -> "APPROVED";
            case "payment_intent.payment_failed", "checkout.session.expired" -> "FAILED";
            default -> object.path("payment_status").asText(object.path("status").asText("PENDING")).toUpperCase();
        };
        String intentId = object.path("payment_intent").asText(object.path("id").asText());
        return new WebhookResult(intentId,status,amount,currency,event.path("id").asText(),OffsetDateTime.now());
    }

    private JsonNode post(PaymentChannelConfigurationRepository.Configuration configuration, String path,
            String idempotencyKey, MultiValueMap<String,String> form) {
        try {
            JsonNode response = client.post().uri(path)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .header("Authorization", "Bearer " + secrets.resolve(configuration.stripeSecretKeyRef()))
                    .header("Idempotency-Key", idempotencyKey).body(form).retrieve().body(JsonNode.class);
            if (response == null || response.path("id").asText("").isBlank()) throw new IllegalStateException("Empty Stripe response");
            return response;
        } catch (RuntimeException exception) {
            throw new BusinessException("STRIPE_REQUEST_FAILED", "No fue posible iniciar la operación con Stripe.");
        }
    }

    private PaymentChannelConfigurationRepository.Configuration configuration(UUID organizationId, UUID branchId, boolean autopay) {
        return configurations.find(organizationId, branchId).filter(c -> c.stripeKioskEnabled() && (!autopay || c.stripeAutopayEnabled()))
                .orElseThrow(() -> new BusinessException(autopay ? "STRIPE_AUTOPAY_DISABLED" : "STRIPE_KIOSK_DISABLED",
                        autopay ? "La domiciliación Stripe está deshabilitada para este kiosco." : "Stripe está deshabilitado para este kiosco."));
    }

    private Context context(Map<String,String> metadata) {
        try { return new Context(UUID.fromString(metadata.get("organizationId")), UUID.fromString(metadata.get("branchId"))); }
        catch (RuntimeException exception) { throw new BusinessException("STRIPE_CONTEXT_REQUIRED", "La operación Stripe requiere organización y sucursal."); }
    }

    private void verifySignature(String payload, String header, String secret) {
        if (header == null) throw new BusinessException("STRIPE_SIGNATURE_REQUIRED", "Falta la firma Stripe.");
        String timestamp=null, expected=null;
        for(String part:header.split(",")){String[] pair=part.split("=",2);if(pair.length==2&&pair[0].equals("t"))timestamp=pair[1];if(pair.length==2&&pair[0].equals("v1"))expected=pair[1];}
        if(timestamp==null||expected==null) throw new BusinessException("STRIPE_SIGNATURE_INVALID", "La firma Stripe no es válida.");
        try {
            Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
            String actual=java.util.HexFormat.of().formatHex(mac.doFinal((timestamp+"."+payload).getBytes(StandardCharsets.UTF_8)));
            if(!java.security.MessageDigest.isEqual(actual.getBytes(StandardCharsets.US_ASCII),expected.getBytes(StandardCharsets.US_ASCII)))
                throw new BusinessException("STRIPE_SIGNATURE_INVALID", "La firma Stripe no es válida.");
        } catch (BusinessException exception) { throw exception; }
        catch (Exception exception) { throw new BusinessException("STRIPE_SIGNATURE_INVALID", "No fue posible validar la firma Stripe."); }
    }

    private record Context(UUID organizationId, UUID branchId) {}
}
