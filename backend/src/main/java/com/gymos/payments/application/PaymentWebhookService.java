package com.gymos.payments.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymos.common.api.BusinessException;
import com.gymos.common.observability.TenantTelemetry;
import com.gymos.payments.application.port.ProviderHttpGateway;
import com.gymos.payments.infrastructure.PaymentConfigurationRepository;
import com.gymos.payments.infrastructure.PaymentProviderRepository;
import com.gymos.payments.infrastructure.PaymentProviderRepository.OperationRecord;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class PaymentWebhookService {
	private static final Logger LOGGER = LoggerFactory.getLogger(PaymentWebhookService.class);
	private static final long SIGNATURE_TOLERANCE_SECONDS = 300;
	private final PaymentProviderProperties properties;
	private final ObjectMapper objectMapper;
	private final ProviderHttpGateway http;
	private final PaymentProviderRepository repository;
	private final PaymentConfigurationRepository configurationRepository;
	private final MercadoPagoProviderService mercadoPagoProviderService;
	private final StripeRecurringBillingService stripeRecurringBillingService;
	private final ProviderPaymentCompletionService completion;
	private final TenantTelemetry telemetry;

	public PaymentWebhookService(
			PaymentProviderProperties properties,
			ObjectMapper objectMapper,
			ProviderHttpGateway http,
			PaymentProviderRepository repository,
			PaymentConfigurationRepository configurationRepository,
			MercadoPagoProviderService mercadoPagoProviderService,
			StripeRecurringBillingService stripeRecurringBillingService,
			ProviderPaymentCompletionService completion,
			TenantTelemetry telemetry
	) {
		this.properties = properties;
		this.objectMapper = objectMapper;
		this.http = http;
		this.repository = repository;
		this.configurationRepository = configurationRepository;
		this.mercadoPagoProviderService = mercadoPagoProviderService;
		this.stripeRecurringBillingService = stripeRecurringBillingService;
		this.completion = completion;
		this.telemetry = telemetry;
	}

	public void stripe(String rawBody, String signatureHeader) {
		verifyStripeSignature(rawBody, signatureHeader);
		JsonNode event = parse(rawBody, "STRIPE_WEBHOOK_INVALID");
		String eventId = requiredText(event, "id", "STRIPE_WEBHOOK_INVALID");
		String eventType = event.path("type").asText("unknown");
		if (!java.util.Set.of(
				"checkout.session.completed", "checkout.session.async_payment_succeeded",
				"payment_intent.succeeded", "payment_intent.payment_failed"
		).contains(eventType)) {
			return;
		}
		String externalId = event.path("data").path("object").path("id").asText();
		OperationRecord operation = repository.operationByExternalId("STRIPE", externalId).orElse(null);
		// Stripe also emits payment_intent events for Checkout payments whose GymOS
		// operation is keyed by the Checkout Session. They are valid but not the
		// authoritative event used by that flow.
		if (operation == null) {
			String connectedAccount = event.path("account").asText("platform");
			if (eventType.startsWith("checkout.session.")) {
				LOGGER.warn(
						"Unmatched Stripe Checkout webhook. eventId={} eventType={} externalOperationId={} connectedAccount={}",
						eventId, eventType, externalId, connectedAccount
				);
			} else {
				LOGGER.debug(
						"Ignoring Stripe webhook without a direct operation match. eventId={} eventType={} externalOperationId={} connectedAccount={}",
						eventId, eventType, externalId, connectedAccount
				);
			}
			return;
		}
		if (!stripeAccount(operation).equals(event.path("account").asText())) {
			throw new BusinessException("STRIPE_WEBHOOK_ACCOUNT_MISMATCH", "La cuenta de Stripe no corresponde a la operación.");
		}
		UUID attemptId = UUID.randomUUID();
		if (!repository.registerWebhook("STRIPE", eventId, eventType, PaymentProviderEncoding.sha256Hex(rawBody), operation.organizationId(), attemptId)) {
			webhookMetric(operation, "STRIPE", eventType, "duplicate");
			return;
		}
		webhookMetric(operation, "STRIPE", eventType, "accepted");
		try {
			if ("SETUP_SESSION".equals(operation.operationType())) {
				stripeRecurringBillingService.completeSetup(operation);
				repository.completeWebhook("STRIPE", eventId, "PROCESSED", null, attemptId);
				webhookMetric(operation, "STRIPE", eventType, "processed");
				return;
			}
			if ("RECURRING_PAYMENT_INTENT".equals(operation.operationType())) {
				processRecurringPaymentIntent(operation, eventType, eventId, attemptId);
				return;
			}
			JsonNode session = fetchStripeSession(operation);
			if (!"paid".equalsIgnoreCase(session.path("payment_status").asText())) {
				repository.markOperationStatus(operation.id(), pendingStatus(session, "OPEN"), json(session), false);
				repository.completeWebhook("STRIPE", eventId, "IGNORED", null, attemptId);
				webhookMetric(operation, "STRIPE", eventType, "ignored");
				return;
			}
			long amount = session.path("amount_total").asLong(-1);
			String currency = session.path("currency").asText("").toUpperCase();
			ensureAmount(operation, amount, currency);
			completion.complete(operation, json(session), receiptUrl(session));
			repository.completeWebhook("STRIPE", eventId, "PROCESSED", null, attemptId);
			webhookMetric(operation, "STRIPE", eventType, "processed");
		} catch (RuntimeException exception) {
			repository.completeWebhook("STRIPE", eventId, "FAILED", safeMessage(exception), attemptId);
			webhookMetric(operation, "STRIPE", eventType, "failed");
			throw exception;
		}
	}

	private void processRecurringPaymentIntent(
			OperationRecord operation,
			String eventType,
			String eventId,
			UUID attemptId
	) {
		JsonNode paymentIntent = stripeRecurringBillingService.fetchPaymentIntent(operation);
		ensureIdentity(operation, paymentIntent);
		String status = paymentIntent.path("status").asText("processing");
		if ("succeeded".equalsIgnoreCase(status)) {
			long amount = paymentIntent.path("amount_received").asLong(-1);
			String currency = paymentIntent.path("currency").asText("").toUpperCase();
			ensureAmount(operation, amount, currency);
			completion.complete(operation, json(paymentIntent), receiptUrl(paymentIntent));
			repository.completeWebhook("STRIPE", eventId, "PROCESSED", null, attemptId);
			webhookMetric(operation, "STRIPE", eventType, "processed");
			return;
		}
		if (java.util.Set.of("requires_payment_method", "requires_action", "canceled").contains(status)) {
			String failureCode = paymentIntent.path("last_payment_error").path("decline_code").asText();
			if (failureCode.isBlank()) failureCode = paymentIntent.path("last_payment_error").path("code").asText(status.toUpperCase());
			stripeRecurringBillingService.failRecurringCharge(operation, failureCode);
			repository.completeWebhook("STRIPE", eventId, "PROCESSED", null, attemptId);
			webhookMetric(operation, "STRIPE", eventType, "processed_failure");
			return;
		}
		repository.markOperationStatus(operation.id(), status.toUpperCase(), json(paymentIntent), false);
		repository.completeWebhook("STRIPE", eventId, "IGNORED", null, attemptId);
		webhookMetric(operation, "STRIPE", eventType, "ignored");
	}

	public void mercadoPago(
			String rawBody,
			String signatureHeader,
			String requestId,
			String dataId,
			String notificationId,
			String eventType
	) {
		verifyMercadoPagoSignature(signatureHeader, requestId, dataId);
		if (dataId == null || dataId.isBlank()) {
			throw new BusinessException("MERCADO_PAGO_WEBHOOK_INVALID", "La notificacion de Mercado Pago no incluye data.id.");
		}
		OperationRecord operation = repository.operationByExternalId("MERCADO_PAGO", dataId)
				.orElseThrow(() -> new BusinessException("MERCADO_PAGO_OPERATION_NOT_FOUND", "La orden Point no corresponde a una operacion de GymOS."));
		String eventId = notificationId == null || notificationId.isBlank()
				? (eventType == null ? "order" : eventType) + ":" + dataId + ":" + requestId
				: notificationId;
		UUID attemptId = UUID.randomUUID();
		if (!repository.registerWebhook("MERCADO_PAGO", eventId, eventType, PaymentProviderEncoding.sha256Hex(rawBody), operation.organizationId(), attemptId)) {
			webhookMetric(operation, "MERCADO_PAGO", eventType, "duplicate");
			return;
		}
		webhookMetric(operation, "MERCADO_PAGO", eventType, "accepted");
		try {
			JsonNode order = fetchPointOrder(operation);
			if (!mercadoPagoPaid(order)) {
				updateUnpaidPointOrder(operation, order);
				repository.completeWebhook("MERCADO_PAGO", eventId, "IGNORED", null, attemptId);
				webhookMetric(operation, "MERCADO_PAGO", eventType, "ignored");
				return;
			}
			long amount = mercadoPagoAmount(order);
			ensureAmount(operation, amount, mercadoPagoCurrency(order));
			completion.complete(operation, json(order), receiptUrl(order));
			repository.completeWebhook("MERCADO_PAGO", eventId, "PROCESSED", null, attemptId);
			webhookMetric(operation, "MERCADO_PAGO", eventType, "processed");
		} catch (RuntimeException exception) {
			repository.completeWebhook("MERCADO_PAGO", eventId, "FAILED", safeMessage(exception), attemptId);
			webhookMetric(operation, "MERCADO_PAGO", eventType, "failed");
			throw exception;
		}
	}

	private String stripeAccount(OperationRecord operation) {
		String accountId = configurationRepository.providerConfiguration(operation.organizationId()).stripeAccountId();
		if (accountId == null || accountId.isBlank()) {
			throw new BusinessException("STRIPE_ACCOUNT_MISSING", "La cuenta conectada de Stripe ya no esta disponible.");
		}
		return accountId;
	}

	private JsonNode fetchStripeSession(OperationRecord operation) {
		JsonNode session = http.exchange(
				"GET", stripeUrl("/v1/checkout/sessions/" + operation.externalOperationId()
						+ "?expand[]=payment_intent.latest_charge"),
				Map.of("Authorization", "Bearer " + properties.stripe().secretKey(), "Stripe-Account", stripeAccount(operation)),
				null, null
		);
		ensureIdentity(operation, session);
		return session;
	}

	private JsonNode fetchPointOrder(OperationRecord operation) {
		String token = mercadoPagoProviderService.accessToken(operation.organizationId());
		JsonNode order = http.exchange("GET", mercadoPagoUrl("/v1/orders/" + operation.externalOperationId()),
				Map.of("Authorization", "Bearer " + token), null, null);
		ensureIdentity(operation, order);
		String reference = order.path("external_reference").asText();
		if (!("gymos-" + operation.saleId()).equals(reference)) {
			throw new BusinessException("PROVIDER_PAYMENT_IDENTITY_MISMATCH", "La referencia del proveedor no corresponde a la venta.");
		}
		return order;
	}

	private void ensureIdentity(OperationRecord operation, JsonNode response) {
		if (!operation.externalOperationId().equals(response.path("id").asText())) {
			throw new BusinessException("PROVIDER_PAYMENT_IDENTITY_MISMATCH", "El recurso del proveedor no corresponde a la operación.");
		}
	}

	private String receiptUrl(JsonNode providerObject) {
		JsonNode charge = providerObject.path("latest_charge");
		if (charge.isMissingNode()) charge = providerObject.path("payment_intent").path("latest_charge");
		return charge.path("receipt_url").asText();
	}

	private String pendingStatus(JsonNode providerObject, String fallback) {
		String status = providerObject.path("status").asText(fallback).toUpperCase(java.util.Locale.ROOT);
		return "COMPLETED".equals(status) ? "AWAITING_PAYMENT" : status;
	}

	public void reconcile(OperationRecord operation) {
		if (operation == null || operation.externalOperationId() == null || "COMPLETED".equals(operation.status())) return;
		if ("STRIPE".equals(operation.provider())) {
			if ("RECURRING_PAYMENT_INTENT".equals(operation.operationType())) {
				JsonNode paymentIntent = stripeRecurringBillingService.fetchPaymentIntent(operation);
				ensureIdentity(operation, paymentIntent);
				String status = paymentIntent.path("status").asText("PROCESSING");
				if ("succeeded".equalsIgnoreCase(status)) {
					ensureAmount(
							operation,
							paymentIntent.path("amount_received").asLong(-1),
							paymentIntent.path("currency").asText("").toUpperCase()
					);
					completion.complete(operation, json(paymentIntent), receiptUrl(paymentIntent));
				} else if ("requires_confirmation".equals(status)) {
					stripeRecurringBillingService.confirmRecurringPayment(operation);
				} else if (java.util.Set.of("requires_payment_method", "requires_action", "canceled").contains(status)) {
					stripeRecurringBillingService.failRecurringCharge(operation, status.toUpperCase());
				} else {
					repository.markOperationStatus(operation.id(), status.toUpperCase(), json(paymentIntent), false);
				}
				return;
			}
			if ("SETUP_SESSION".equals(operation.operationType())) {
				stripeRecurringBillingService.reconcileSetup(operation);
				return;
			}
			JsonNode session = fetchStripeSession(operation);
			if ("paid".equalsIgnoreCase(session.path("payment_status").asText())) {
				ensureAmount(operation, session.path("amount_total").asLong(-1), session.path("currency").asText("").toUpperCase());
				completion.complete(operation, json(session), receiptUrl(session));
			} else {
				repository.markOperationStatus(operation.id(), pendingStatus(session, "OPEN"), json(session), false);
			}
			return;
		}
		if ("MERCADO_PAGO".equals(operation.provider())) {
			JsonNode order = fetchPointOrder(operation);
			if (mercadoPagoPaid(order)) {
				ensureAmount(operation, mercadoPagoAmount(order), mercadoPagoCurrency(order));
				completion.complete(operation, json(order), receiptUrl(order));
			} else {
				updateUnpaidPointOrder(operation, order);
			}
		}
	}

	private void ensureAmount(OperationRecord operation, long amount, String currency) {
		if (amount != operation.amountCents() || !operation.currency().equalsIgnoreCase(currency)) {
			repository.markOperationStatus(operation.id(), "AMOUNT_MISMATCH", "{}", false);
			throw new BusinessException("PROVIDER_PAYMENT_AMOUNT_MISMATCH", "El monto confirmado por el proveedor no coincide con la venta.");
		}
	}

	private void updateUnpaidPointOrder(OperationRecord operation, JsonNode order) {
		String status = pendingStatus(order, "PENDING");
		if (java.util.Set.of("EXPIRED", "CANCELED", "CANCELLED", "REJECTED").contains(status)) {
			completion.closePoint(operation, status, json(order));
		} else {
			repository.markOperationStatus(operation.id(), status, json(order), false);
		}
	}

	private boolean mercadoPagoPaid(JsonNode order) {
		JsonNode payments = order.path("transactions").path("payments");
		return java.util.Set.of("processed", "completed").contains(order.path("status").asText())
				&& payments.isArray() && payments.size() == 1
				&& java.util.Set.of("processed", "approved", "accredited").contains(payments.get(0).path("status").asText());
	}

	private long mercadoPagoAmount(JsonNode order) {
		try {
			return new BigDecimal(order.path("transactions").path("payments").path(0).path("amount").asText())
					.movePointRight(2).longValueExact();
		} catch (ArithmeticException | NumberFormatException exception) {
			return -1;
		}
	}

	private String mercadoPagoCurrency(JsonNode order) {
		return order.path("transactions").path("payments").path(0).path("currency_id")
				.asText(order.path("currency_id").asText("MXN"));
	}

	private void verifyStripeSignature(String payload, String header) {
		String secret = properties.stripe().webhookSecret();
		if (secret == null || secret.isBlank()) throw new BusinessException("STRIPE_WEBHOOK_NOT_CONFIGURED", "Configura el secreto de webhook de Stripe.");
		Map<String, String> parts = signatureParts(header);
		long timestamp = parseTimestamp(parts.get("t"), "STRIPE_WEBHOOK_SIGNATURE_INVALID");
		ensureFresh(timestamp, "STRIPE_WEBHOOK_SIGNATURE_EXPIRED");
		String expected = hmacHex(secret, timestamp + "." + payload);
		if (header == null || java.util.Arrays.stream(header.split(","))
				.map(String::trim).filter(part -> part.startsWith("v1="))
				.noneMatch(part -> constantTimeHex(expected, part.substring(3)))) {
			throw new BusinessException("STRIPE_WEBHOOK_SIGNATURE_INVALID", "La firma del webhook de Stripe no es valida.");
		}
	}

	private void verifyMercadoPagoSignature(String header, String requestId, String dataId) {
		String secret = properties.mercadoPago().webhookSecret();
		if (secret == null || secret.isBlank()) throw new BusinessException("MERCADO_PAGO_WEBHOOK_NOT_CONFIGURED", "Configura el secreto de webhook de Mercado Pago.");
		if (requestId == null || requestId.isBlank() || dataId == null || dataId.isBlank()) {
			throw new BusinessException("MERCADO_PAGO_WEBHOOK_SIGNATURE_INVALID", "La notificacion no contiene los identificadores firmados.");
		}
		Map<String, String> parts = signatureParts(header);
		long timestamp = parseTimestamp(parts.get("ts"), "MERCADO_PAGO_WEBHOOK_SIGNATURE_INVALID");
		ensureFresh(timestamp, "MERCADO_PAGO_WEBHOOK_SIGNATURE_EXPIRED");
		String normalizedDataId = dataId == null ? "" : dataId.toLowerCase();
		String manifest = "id:" + normalizedDataId + ";request-id:" + requestId + ";ts:" + timestamp + ";";
		String expected = hmacHex(secret, manifest);
		if (!constantTimeHex(expected, parts.get("v1"))) {
			throw new BusinessException("MERCADO_PAGO_WEBHOOK_SIGNATURE_INVALID", "La firma del webhook de Mercado Pago no es valida.");
		}
	}

	private Map<String, String> signatureParts(String header) {
		Map<String, String> result = new LinkedHashMap<>();
		if (header != null) {
			for (String part : header.split(",")) {
				String[] pair = part.trim().split("=", 2);
				if (pair.length == 2) result.putIfAbsent(pair[0], pair[1]);
			}
		}
		return result;
	}

	private long parseTimestamp(String value, String code) {
		try {
			return Long.parseLong(value);
		} catch (RuntimeException exception) {
			throw new BusinessException(code, "La firma del webhook no contiene una fecha valida.");
		}
	}

	private void ensureFresh(long timestamp, String code) {
		long now = Instant.now().getEpochSecond();
		if (timestamp < now - SIGNATURE_TOLERANCE_SECONDS || timestamp > now + SIGNATURE_TOLERANCE_SECONDS) {
			throw new BusinessException(code, "La firma del webhook esta fuera de la ventana permitida.");
		}
	}

	private String hmacHex(String secret, String value) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
			return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
		} catch (Exception exception) {
			throw new IllegalStateException("HmacSHA256 no esta disponible.", exception);
		}
	}

	private boolean constantTimeHex(String expected, String provided) {
		if (provided == null) return false;
		return MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII), provided.getBytes(StandardCharsets.US_ASCII));
	}

	private JsonNode parse(String raw, String code) {
		try {
			return objectMapper.readTree(raw == null ? "{}" : raw);
		} catch (Exception exception) {
			throw new BusinessException(code, "El cuerpo del webhook no es JSON valido.");
		}
	}

	private String requiredText(JsonNode node, String field, String code) {
		String value = node.path(field).asText();
		if (value.isBlank()) throw new BusinessException(code, "El webhook no contiene un identificador.");
		return value;
	}

	private String json(JsonNode node) {
		try {
			return objectMapper.writeValueAsString(node);
		} catch (Exception exception) {
			throw new IllegalStateException("No fue posible serializar la respuesta del proveedor.", exception);
		}
	}

	private String stripeUrl(String path) {
		return properties.stripe().apiBaseUrl().replaceAll("/+$", "") + path;
	}

	private String mercadoPagoUrl(String path) {
		return properties.mercadoPago().apiBaseUrl().replaceAll("/+$", "") + path;
	}

	private String safeMessage(RuntimeException exception) {
		return exception instanceof BusinessException business ? business.code() : exception.getClass().getSimpleName();
	}

	private void webhookMetric(OperationRecord operation, String provider, String eventType, String outcome) {
		telemetry.increment(
				"gymos.payments.webhooks", operation.organizationId(),
				"provider", provider, "event_type", eventType, "outcome", outcome
		);
	}
}
