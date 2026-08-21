package com.gymos.payments.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymos.common.api.BusinessException;
import com.gymos.common.observability.TenantTelemetry;
import com.gymos.payments.api.events.ProviderPaymentCompletedV1;
import com.gymos.payments.application.port.ProviderHttpGateway;
import com.gymos.payments.infrastructure.PaymentConfigurationRepository;
import com.gymos.payments.infrastructure.PaymentProviderRepository;
import com.gymos.payments.infrastructure.PaymentProviderRepository.OperationRecord;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
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
	private final ApplicationEventPublisher eventPublisher;
	private final TenantTelemetry telemetry;

	@Autowired
	public PaymentWebhookService(
			PaymentProviderProperties properties,
			ObjectMapper objectMapper,
			ProviderHttpGateway http,
			PaymentProviderRepository repository,
			PaymentConfigurationRepository configurationRepository,
			MercadoPagoProviderService mercadoPagoProviderService,
			StripeRecurringBillingService stripeRecurringBillingService,
			ApplicationEventPublisher eventPublisher,
			TenantTelemetry telemetry
	) {
		this.properties = properties;
		this.objectMapper = objectMapper;
		this.http = http;
		this.repository = repository;
		this.configurationRepository = configurationRepository;
		this.mercadoPagoProviderService = mercadoPagoProviderService;
		this.stripeRecurringBillingService = stripeRecurringBillingService;
		this.eventPublisher = eventPublisher;
		this.telemetry = telemetry;
	}

	PaymentWebhookService(
			PaymentProviderProperties properties,
			ObjectMapper objectMapper,
			ProviderHttpGateway http,
			PaymentProviderRepository repository,
			PaymentConfigurationRepository configurationRepository,
			MercadoPagoProviderService mercadoPagoProviderService,
			StripeRecurringBillingService stripeRecurringBillingService,
			ApplicationEventPublisher eventPublisher
	) {
		this(
				properties, objectMapper, http, repository, configurationRepository,
				mercadoPagoProviderService, stripeRecurringBillingService, eventPublisher,
				TenantTelemetry.noOp()
		);
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
		if (!repository.registerWebhook("STRIPE", eventId, eventType, PaymentProviderEncoding.sha256Hex(rawBody), operation.organizationId())) {
			webhookMetric(operation, "STRIPE", eventType, "duplicate");
			return;
		}
		webhookMetric(operation, "STRIPE", eventType, "accepted");
		try {
			if ("SETUP_SESSION".equals(operation.operationType())) {
				stripeRecurringBillingService.completeSetup(operation);
				repository.completeWebhook("STRIPE", eventId, "PROCESSED", null);
				webhookMetric(operation, "STRIPE", eventType, "processed");
				return;
			}
			if ("RECURRING_PAYMENT_INTENT".equals(operation.operationType())) {
				processRecurringPaymentIntent(operation, eventType, eventId);
				return;
			}
			JsonNode session = fetchStripeSession(operation);
			if (!"paid".equalsIgnoreCase(session.path("payment_status").asText())) {
				repository.markOperationStatus(operation.id(), session.path("status").asText("OPEN").toUpperCase(), json(session), false);
				repository.completeWebhook("STRIPE", eventId, "IGNORED", null);
				webhookMetric(operation, "STRIPE", eventType, "ignored");
				return;
			}
			long amount = session.path("amount_total").asLong(-1);
			String currency = session.path("currency").asText("").toUpperCase();
			ensureAmount(operation, amount, currency);
			storeReceipt(operation, session);
			publishCompletion(operation, "STRIPE", eventId, json(session));
			repository.completeWebhook("STRIPE", eventId, "PROCESSED", null);
			webhookMetric(operation, "STRIPE", eventType, "processed");
		} catch (RuntimeException exception) {
			repository.completeWebhook("STRIPE", eventId, "FAILED", safeMessage(exception));
			webhookMetric(operation, "STRIPE", eventType, "failed");
			throw exception;
		}
	}

	private void processRecurringPaymentIntent(
			OperationRecord operation,
			String eventType,
			String eventId
	) {
		JsonNode paymentIntent = stripeRecurringBillingService.fetchPaymentIntent(operation);
		String status = paymentIntent.path("status").asText("processing");
		if ("succeeded".equalsIgnoreCase(status)) {
			long amount = paymentIntent.path("amount_received").asLong(paymentIntent.path("amount").asLong(-1));
			String currency = paymentIntent.path("currency").asText("").toUpperCase();
			ensureAmount(operation, amount, currency);
			storeReceipt(operation, paymentIntent);
			stripeRecurringBillingService.completeRecurringCharge(operation, json(paymentIntent));
			publishCompletion(operation, "STRIPE", eventId, json(paymentIntent));
			repository.completeWebhook("STRIPE", eventId, "PROCESSED", null);
			webhookMetric(operation, "STRIPE", eventType, "processed");
			return;
		}
		if ("payment_intent.payment_failed".equals(eventType)
				|| java.util.Set.of("requires_payment_method", "requires_action", "canceled").contains(status)) {
			String failureCode = paymentIntent.path("last_payment_error").path("decline_code").asText();
			if (failureCode.isBlank()) failureCode = paymentIntent.path("last_payment_error").path("code").asText(status.toUpperCase());
			stripeRecurringBillingService.failRecurringCharge(operation, failureCode);
			repository.completeWebhook("STRIPE", eventId, "PROCESSED", null);
			webhookMetric(operation, "STRIPE", eventType, "processed_failure");
			return;
		}
		repository.markOperationStatus(operation.id(), status.toUpperCase(), json(paymentIntent), false);
		repository.completeWebhook("STRIPE", eventId, "IGNORED", null);
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
		if (!repository.registerWebhook("MERCADO_PAGO", eventId, eventType, PaymentProviderEncoding.sha256Hex(rawBody), operation.organizationId())) {
			webhookMetric(operation, "MERCADO_PAGO", eventType, "duplicate");
			return;
		}
		webhookMetric(operation, "MERCADO_PAGO", eventType, "accepted");
		try {
			String token = mercadoPagoProviderService.accessToken(operation.organizationId());
			JsonNode order = http.exchange("GET", mercadoPagoUrl("/v1/orders/" + dataId), Map.of("Authorization", "Bearer " + token), null, null);
			if (!mercadoPagoPaid(order)) {
				repository.markOperationStatus(operation.id(), order.path("status").asText("PENDING").toUpperCase(), json(order), false);
				repository.completeWebhook("MERCADO_PAGO", eventId, "IGNORED", null);
				webhookMetric(operation, "MERCADO_PAGO", eventType, "ignored");
				return;
			}
			long amount = mercadoPagoAmount(order);
			ensureAmount(operation, amount, "MXN");
			publishCompletion(operation, "MERCADO_PAGO", eventId, json(order));
			repository.completeWebhook("MERCADO_PAGO", eventId, "PROCESSED", null);
			webhookMetric(operation, "MERCADO_PAGO", eventType, "processed");
		} catch (RuntimeException exception) {
			repository.completeWebhook("MERCADO_PAGO", eventId, "FAILED", safeMessage(exception));
			webhookMetric(operation, "MERCADO_PAGO", eventType, "failed");
			throw exception;
		}
	}

	private JsonNode fetchStripeSession(OperationRecord operation) {
		String accountId = configurationRepository.configuration(operation.organizationId()).stripeAccountId();
		if (accountId == null || accountId.isBlank()) {
			throw new BusinessException("STRIPE_ACCOUNT_MISSING", "La cuenta conectada de Stripe ya no esta disponible.");
		}
		Map<String, String> headers = new LinkedHashMap<>();
		headers.put("Authorization", "Bearer " + properties.stripe().secretKey());
		headers.put("Stripe-Account", accountId);
		return http.exchange(
				"GET", stripeUrl("/v1/checkout/sessions/" + operation.externalOperationId()
						+ "?expand[]=payment_intent.latest_charge"), headers, null, null
		);
	}

	private void storeReceipt(OperationRecord operation, JsonNode providerObject) {
		JsonNode charge = providerObject.path("latest_charge");
		if (charge.isMissingNode()) charge = providerObject.path("payment_intent").path("latest_charge");
		String receiptUrl = charge.path("receipt_url").asText();
		if (!receiptUrl.isBlank()) repository.saveReceiptUrl(operation.id(), receiptUrl);
	}

	private void publishCompletion(OperationRecord operation, String provider, String eventId, String payload) {
		eventPublisher.publishEvent(new ProviderPaymentCompletedV1(
				"provider-payment-completed:" + provider + ":" + operation.id(),
				operation.organizationId(), operation.branchId(), provider, operation.id(), operation.externalOperationId(),
				operation.saleId(), operation.paymentId(), operation.amountCents(), operation.currency(), OffsetDateTime.now(ZoneOffset.UTC)
		));
		repository.markOperationStatus(operation.id(), "COMPLETED", payload, true);
	}

	public void reconcile(OperationRecord operation) {
		if (operation == null || operation.externalOperationId() == null || "COMPLETED".equals(operation.status())) return;
		if ("STRIPE".equals(operation.provider())) {
			if ("RECURRING_PAYMENT_INTENT".equals(operation.operationType())) {
				JsonNode paymentIntent = stripeRecurringBillingService.fetchPaymentIntent(operation);
				String status = paymentIntent.path("status").asText("PROCESSING");
				if ("succeeded".equalsIgnoreCase(status)) {
					ensureAmount(
							operation,
							paymentIntent.path("amount_received").asLong(paymentIntent.path("amount").asLong(-1)),
							paymentIntent.path("currency").asText("").toUpperCase()
					);
					stripeRecurringBillingService.completeRecurringCharge(operation, json(paymentIntent));
					publishCompletion(operation, "STRIPE", "reconciliation:" + operation.id(), json(paymentIntent));
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
				storeReceipt(operation, session);
				publishCompletion(operation, "STRIPE", "reconciliation:" + operation.id(), json(session));
			} else {
				repository.markOperationStatus(operation.id(), session.path("status").asText("OPEN").toUpperCase(), json(session), false);
			}
			return;
		}
		if ("MERCADO_PAGO".equals(operation.provider())) {
			String token = mercadoPagoProviderService.accessToken(operation.organizationId());
			JsonNode order = http.exchange("GET", mercadoPagoUrl("/v1/orders/" + operation.externalOperationId()), Map.of("Authorization", "Bearer " + token), null, null);
			if (mercadoPagoPaid(order)) {
				ensureAmount(operation, mercadoPagoAmount(order), "MXN");
				publishCompletion(operation, "MERCADO_PAGO", "reconciliation:" + operation.id(), json(order));
			} else {
				repository.markOperationStatus(operation.id(), order.path("status").asText("PENDING").toUpperCase(), json(order), false);
			}
		}
	}

	private void ensureAmount(OperationRecord operation, long amount, String currency) {
		if (amount != operation.amountCents() || !operation.currency().equalsIgnoreCase(currency)) {
			repository.markOperationStatus(operation.id(), "AMOUNT_MISMATCH", "{}", false);
			throw new BusinessException("PROVIDER_PAYMENT_AMOUNT_MISMATCH", "El monto confirmado por el proveedor no coincide con la venta.");
		}
	}

	private boolean mercadoPagoPaid(JsonNode order) {
		String orderStatus = order.path("status").asText();
		if ("processed".equalsIgnoreCase(orderStatus) || "completed".equalsIgnoreCase(orderStatus)) return true;
		JsonNode payments = order.path("transactions").path("payments");
		if (!payments.isArray()) return false;
		for (JsonNode payment : payments) {
			String status = payment.path("status").asText();
			if ("processed".equalsIgnoreCase(status) || "approved".equalsIgnoreCase(status) || "accredited".equalsIgnoreCase(status)) return true;
		}
		return false;
	}

	private long mercadoPagoAmount(JsonNode order) {
		JsonNode payments = order.path("transactions").path("payments");
		if (!payments.isArray() || payments.isEmpty()) return -1;
		try {
			return payments.get(0).path("amount").decimalValue().movePointRight(2).longValueExact();
		} catch (ArithmeticException exception) {
			return -1;
		}
	}

	private void verifyStripeSignature(String payload, String header) {
		String secret = properties.stripe().webhookSecret();
		if (secret == null || secret.isBlank()) throw new BusinessException("STRIPE_WEBHOOK_NOT_CONFIGURED", "Configura el secreto de webhook de Stripe.");
		Map<String, String> parts = signatureParts(header);
		long timestamp = parseTimestamp(parts.get("t"), "STRIPE_WEBHOOK_SIGNATURE_INVALID");
		ensureFresh(timestamp, "STRIPE_WEBHOOK_SIGNATURE_EXPIRED");
		String expected = hmacHex(secret, timestamp + "." + payload);
		if (!constantTimeHex(expected, parts.get("v1"))) {
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
		if (Math.abs(Instant.now().getEpochSecond() - timestamp) > SIGNATURE_TOLERANCE_SECONDS) {
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
		String message = exception.getMessage();
		return message == null ? exception.getClass().getSimpleName() : message.substring(0, Math.min(1000, message.length()));
	}

	private void webhookMetric(OperationRecord operation, String provider, String eventType, String outcome) {
		telemetry.increment(
				"gymos.payments.webhooks", operation.organizationId(),
				"provider", provider, "event_type", eventType, "outcome", outcome
		);
	}
}
