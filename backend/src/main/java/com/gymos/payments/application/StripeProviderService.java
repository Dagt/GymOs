package com.gymos.payments.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymos.audit.api.AuditService;
import com.gymos.common.api.BusinessException;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.payments.api.PaymentConfigurationView;
import com.gymos.payments.api.StripeCheckoutOperations;
import com.gymos.payments.application.port.ProviderHttpGateway;
import com.gymos.payments.infrastructure.PaymentConfigurationRepository;
import com.gymos.payments.infrastructure.PaymentProviderRepository;
import com.gymos.payments.infrastructure.PaymentProviderRepository.CreateOperationData;
import com.gymos.payments.infrastructure.PaymentProviderRepository.OperationRecord;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class StripeProviderService implements StripeCheckoutOperations {
	private static final String PROVIDER = "STRIPE";
	private final PaymentProviderProperties properties;
	private final ProviderHttpGateway http;
	private final PaymentProviderRepository providerRepository;
	private final PaymentConfigurationRepository configurationRepository;
	private final ObjectMapper objectMapper;
	private final AuditService auditService;

	public StripeProviderService(
			PaymentProviderProperties properties,
			ProviderHttpGateway http,
			PaymentProviderRepository providerRepository,
			PaymentConfigurationRepository configurationRepository,
			ObjectMapper objectMapper,
			AuditService auditService
	) {
		this.properties = properties;
		this.http = http;
		this.providerRepository = providerRepository;
		this.configurationRepository = configurationRepository;
		this.objectMapper = objectMapper;
		this.auditService = auditService;
	}

	public ProviderLink beginOnboarding(GymOsPrincipal principal, String correlationId) {
		requireTenant(principal);
		requireConfigured();
		configurationRepository.providerConfiguration(principal.organizationId());
		String accountId = providerRepository.connection(principal.organizationId(), PROVIDER)
				.map(PaymentProviderRepository.ConnectionRecord::externalAccountId)
				.filter(value -> value != null && !value.isBlank())
				.orElseGet(() -> createConnectedAccount(principal));

		Map<String, Object> body = PaymentProviderEncoding.values();
		body.put("account", accountId);
		body.put("refresh_url", defaultUrl(properties.stripe().refreshUrl(), "/settings/organization?payment=stripe-refresh"));
		body.put("return_url", defaultUrl(properties.stripe().returnUrl(), "/settings/organization?payment=stripe-return"));
		body.put("type", "account_onboarding");
		JsonNode response = http.exchange(
				"POST",
				stripeUrl("/v1/account_links"),
				stripeHeaders(null),
				PaymentProviderEncoding.form(body),
				"application/x-www-form-urlencoded"
		);
		String url = requiredText(response, "url", "STRIPE_ACCOUNT_LINK_INVALID");
		auditService.record(principal, "STRIPE_ONBOARDING_STARTED", "PAYMENT_PROVIDER_CONNECTION",
				principal.organizationId(), "BACKOFFICE", null, correlationId);
		return new ProviderLink(url, accountId, text(response, "expires_at"));
	}

	public PaymentConfigurationView refreshConnection(GymOsPrincipal principal, String correlationId) {
		requireTenant(principal);
		requireConfigured();
		String accountId = providerRepository.connection(principal.organizationId(), PROVIDER)
				.map(PaymentProviderRepository.ConnectionRecord::externalAccountId)
				.filter(value -> value != null && !value.isBlank())
				.orElseThrow(() -> new BusinessException("STRIPE_NOT_LINKED", "Inicia la vinculacion de Stripe antes de verificarla."));
		JsonNode response = http.exchange("GET", stripeUrl("/v1/accounts/" + accountId), stripeHeaders(null), null, null);
		if (!accountId.equals(requiredText(response, "id", "STRIPE_ACCOUNT_INVALID"))) {
			throw new BusinessException("STRIPE_ACCOUNT_INVALID", "Stripe devolvió una cuenta distinta de la vinculada.");
		}
		boolean connected = response.path("details_submitted").asBoolean(false)
				&& response.path("charges_enabled").asBoolean(false);
		providerRepository.saveStripeAccount(
				principal.organizationId(), principal.userId(), accountId, connected, json(response)
		);
		auditService.record(principal, connected ? "STRIPE_CONNECTED" : "STRIPE_CONNECTION_CHECKED",
				"PAYMENT_PROVIDER_CONNECTION", principal.organizationId(), "BACKOFFICE", null, correlationId);
		return configurationRepository.configuration(principal.organizationId(), principal.branchId());
	}

	@Override
	public CheckoutSessionResult createCheckout(CreateCheckoutSessionCommand command) {
		validateCheckout(command);
		PaymentConfigurationView configuration = configurationRepository.configuration(command.organizationId(), command.branchId());
		if (!configuration.stripeCheckoutReady() || configuration.stripeAccountId() == null) {
			throw new BusinessException("STRIPE_CHECKOUT_DISABLED", "Stripe self checkout no esta habilitado para este gimnasio.");
		}
		if (command.recurring()) {
			throw new BusinessException(
					"STRIPE_RECURRING_REQUIRES_SCHEDULE",
					"El cobro recurrente requiere una periodicidad explicita del plan. No se creara un cargo unico por error."
			);
		}

		for (int attempt = 0; attempt < 3; attempt++) {
			CreateOperationData data = checkoutData(command, command.idempotencyKey());
			String paymentState = providerRepository.checkoutPaymentState(data);
			if (!"PENDING_VALIDATION".equals(paymentState)) return withoutCheckout(null, paymentState);
			OperationRecord latest = providerRepository.latestOperationForPayment(
					command.organizationId(), PROVIDER, "CHECKOUT_SESSION", command.paymentId()).orElse(null);
			if (latest != null) {
				requireMatchingCheckout(latest, command);
				if ("COMPLETED".equals(latest.status())) return withoutCheckout(latest.id(), "AWAITING_PAYMENT");
				if (latest.externalOperationId() == null) {
					return submitCheckout(command, latest, configuration.stripeAccountId());
				}
				var observation = observeCheckout(latest, configuration.stripeAccountId());
				if (!observation.retryAllowed()) return checkoutResult(latest, observation, data);
			}
			// The first reservation uses the original key. A replacement is linked to its predecessor.
			String key = latest == null ? command.idempotencyKey()
					: "stripe-retry:" + command.paymentId() + ":" + latest.id();
			var reservation = providerRepository.reserveCheckout(checkoutData(command, key), latest == null ? null : latest.id());
			if (!"PENDING_VALIDATION".equals(reservation.paymentState())) return withoutCheckout(null, reservation.paymentState());
			if (reservation.operation() != null) {
				requireMatchingCheckout(reservation.operation(), command);
				return submitCheckout(command, reservation.operation(), configuration.stripeAccountId());
			}
			// Another request reserved a new attempt while we were consulting Stripe.
		}
		return withoutCheckout(null, "AWAITING_PAYMENT");
	}

	private CreateOperationData checkoutData(CreateCheckoutSessionCommand command, String key) {
		return new CreateOperationData(command.organizationId(), command.branchId(), PROVIDER, "CHECKOUT_SESSION",
				command.saleId(), command.paymentId(), command.clientId(), key, command.amountCents(), normalizeCurrency(command.currency()));
	}

	private CheckoutSessionResult submitCheckout(CreateCheckoutSessionCommand command, OperationRecord operation, String accountId) {
		CreateOperationData data = checkoutData(command, operation.idempotencyKey());
		String paymentState = providerRepository.checkoutPaymentState(data);
		if (!"PENDING_VALIDATION".equals(paymentState)) return withoutCheckout(operation.id(), paymentState);
		if (!"CREATING".equals(operation.status()) || !providerRepository.checkoutCreationReplayAllowed(operation.id())) {
			return withoutCheckout(operation.id(), "AWAITING_PAYMENT");
		}
		UUID operationId = operation.id();

		Map<String, Object> body = PaymentProviderEncoding.values();
		body.put("mode", "payment");
		body.put("success_url", properties.frontendBaseUrl() + "/kiosk/checkout/result?status=success&session_id={CHECKOUT_SESSION_ID}");
		body.put("cancel_url", properties.frontendBaseUrl() + "/kiosk/checkout/result?status=cancelled");
		body.put("client_reference_id", command.saleId().toString());
		body.put("line_items[0][quantity]", 1);
		body.put("line_items[0][price_data][currency]", normalizeCurrency(command.currency()).toLowerCase());
		body.put("line_items[0][price_data][unit_amount]", command.amountCents());
		body.put("line_items[0][price_data][product_data][name]", safeDescription(command.description()));
		body.put("metadata[gymos_operation_id]", operationId);
		body.put("metadata[gymos_sale_id]", command.saleId());
		body.put("metadata[gymos_payment_id]", command.paymentId());
		body.put("payment_intent_data[metadata][gymos_operation_id]", operationId);
		JsonNode response = http.exchange(
				"POST", stripeUrl("/v1/checkout/sessions"),
				stripeHeaders(accountId, operation.idempotencyKey()),
				PaymentProviderEncoding.form(body), "application/x-www-form-urlencoded"
		);
		String sessionId = requiredText(response, "id", "STRIPE_SESSION_INVALID");
		String checkoutUrl = text(response, "url");
		String status = response.path("status").asText("AWAITING_PAYMENT").toUpperCase(java.util.Locale.ROOT);
		OffsetDateTime expiresAt = response.hasNonNull("expires_at")
				? OffsetDateTime.ofInstant(java.time.Instant.ofEpochSecond(response.path("expires_at").asLong()), java.time.ZoneOffset.UTC)
				: null;
		providerRepository.markOperationCreated(operationId, sessionId, status, checkoutUrl, json(response), expiresAt);
		OperationRecord stored = providerRepository.operation(operationId).orElseThrow();
		// Stripe can return the original POST response on replay, even if the session has since closed.
		return checkoutResult(stored, observeCheckout(stored, accountId), data);
	}

	private CheckoutObservation observeCheckout(OperationRecord operation, String accountId) {
		JsonNode session;
		try {
			session = http.exchange("GET", stripeUrl("/v1/checkout/sessions/" + operation.externalOperationId()
					+ "?expand[]=payment_intent.latest_charge"), stripeHeaders(accountId), null, null);
		} catch (BusinessException exception) {
			return new CheckoutObservation(null, false);
		}
		if (session == null || !operation.externalOperationId().equals(session.path("id").asText())
				|| !"payment".equals(session.path("mode").asText())
				|| !session.path("amount_total").isIntegralNumber() || !session.path("amount_total").canConvertToLong()
				|| session.path("amount_total").longValue() != operation.amountCents()
				|| !operation.currency().equalsIgnoreCase(session.path("currency").asText())
				|| !operation.saleId().toString().equals(session.path("client_reference_id").asText())) {
			return new CheckoutObservation(null, false);
		}
		String status = session.path("status").asText();
		if (!"unpaid".equals(session.path("payment_status").asText())) return new CheckoutObservation(null, false);
		JsonNode intent = session.path("payment_intent");
		boolean noIntent = intent.isNull();
		String intentStatus = intent.path("status").asText();
		boolean noFunds = isZero(intent.path("amount_received")) && isZero(intent.path("amount_capturable"));
		boolean canceled = "canceled".equals(intentStatus) && noFunds;
		boolean needsMethod = "requires_payment_method".equals(intentStatus) && noFunds;
		if (("expired".equals(status) && (noIntent || canceled || needsMethod)) || ("complete".equals(status) && canceled)) {
			return new CheckoutObservation(null, true);
		}
		String url = text(session, "url");
		boolean open = "open".equals(status) && url != null && !url.isBlank()
				&& (noIntent || needsMethod);
		return new CheckoutObservation(open ? url : null, false);
	}

	private boolean isZero(JsonNode amount) {
		return amount.isIntegralNumber() && amount.canConvertToLong() && amount.longValue() == 0;
	}

	private CheckoutSessionResult checkoutResult(OperationRecord operation, CheckoutObservation observation, CreateOperationData data) {
		String paymentState = providerRepository.checkoutPaymentState(data);
		if (!"PENDING_VALIDATION".equals(paymentState)) return withoutCheckout(operation.id(), paymentState);
		if (observation.url() != null) {
			return new CheckoutSessionResult(operation.id(), operation.externalOperationId(), observation.url(), "OPEN");
		}
		providerRepository.requestCheckoutReconciliation(operation.id());
		return withoutCheckout(operation.id(), "AWAITING_PAYMENT");
	}

	private CheckoutSessionResult withoutCheckout(UUID operationId, String status) {
		return new CheckoutSessionResult(operationId, null, null, status);
	}

	private record CheckoutObservation(String url, boolean retryAllowed) {}

	private String createConnectedAccount(GymOsPrincipal principal) {
		Map<String, Object> body = PaymentProviderEncoding.values();
		body.put("type", "express");
		body.put("country", "MX");
		body.put("capabilities[card_payments][requested]", true);
		body.put("capabilities[transfers][requested]", true);
		body.put("metadata[gymos_organization_id]", principal.organizationId());
		JsonNode response = http.exchange("POST", stripeUrl("/v1/accounts"), stripeHeaders(null, "gymfort-connect:" + principal.organizationId()),
				PaymentProviderEncoding.form(body), "application/x-www-form-urlencoded");
		String accountId = requiredText(response, "id", "STRIPE_ACCOUNT_INVALID");
		providerRepository.saveStripeAccount(principal.organizationId(), principal.userId(), accountId, false, json(response));
		return accountId;
	}

	private void requireMatchingCheckout(OperationRecord operation, CreateCheckoutSessionCommand command) {
		boolean matches = PROVIDER.equals(operation.provider()) && "CHECKOUT_SESSION".equals(operation.operationType())
				&& Objects.equals(operation.organizationId(), command.organizationId())
				&& Objects.equals(operation.branchId(), command.branchId())
				&& Objects.equals(operation.clientId(), command.clientId())
				&& Objects.equals(operation.saleId(), command.saleId())
				&& Objects.equals(operation.paymentId(), command.paymentId())
				&& operation.amountCents() == command.amountCents()
				&& operation.currency().equals(normalizeCurrency(command.currency()));
		if (!matches) {
			throw new BusinessException("STRIPE_CHECKOUT_CONTEXT_MISMATCH", "La operación de Stripe no corresponde a este pago.");
		}
	}

	private void validateCheckout(CreateCheckoutSessionCommand command) {
		requireConfigured();
		if (command == null || command.organizationId() == null || command.saleId() == null || command.paymentId() == null) {
			throw new BusinessException("STRIPE_CHECKOUT_INVALID", "La venta y el pago son obligatorios para iniciar Stripe Checkout.");
		}
		if (command.amountCents() <= 0 || command.idempotencyKey() == null || command.idempotencyKey().isBlank()) {
			throw new BusinessException("STRIPE_CHECKOUT_INVALID", "El monto y la llave de idempotencia son obligatorios.");
		}
	}

	private Map<String, String> stripeHeaders(String accountId) {
		return stripeHeaders(accountId, null);
	}

	private Map<String, String> stripeHeaders(String accountId, String idempotencyKey) {
		Map<String, String> headers = new java.util.LinkedHashMap<>();
		headers.put("Authorization", "Bearer " + properties.stripe().secretKey().trim());
		if (accountId != null) headers.put("Stripe-Account", accountId);
		if (idempotencyKey != null) headers.put("Idempotency-Key", idempotencyKey);
		return headers;
	}

	private String stripeUrl(String path) {
		return properties.stripe().apiBaseUrl().replaceAll("/+$", "") + path;
	}

	private String defaultUrl(String configured, String path) {
		return configured == null || configured.isBlank() ? properties.frontendBaseUrl().replaceAll("/+$", "") + path : configured.trim();
	}

	private String requiredText(JsonNode node, String field, String code) {
		String value = text(node, field);
		if (value == null || value.isBlank()) throw new BusinessException(code, "Stripe devolvio una respuesta incompleta.");
		return value;
	}

	private String text(JsonNode node, String field) {
		return node != null && node.hasNonNull(field) ? node.path(field).asText() : null;
	}

	private String json(JsonNode node) {
		try {
			return objectMapper.writeValueAsString(node);
		} catch (Exception exception) {
			throw new IllegalStateException("No fue posible serializar la respuesta de Stripe.", exception);
		}
	}

	private String normalizeCurrency(String currency) {
		return currency == null || currency.isBlank() ? "MXN" : currency.trim().toUpperCase();
	}

	private String safeDescription(String description) {
		return description == null || description.isBlank() ? "Membresia GymOS" : description.trim();
	}

	private void requireTenant(GymOsPrincipal principal) {
		if (principal == null || principal.organizationId() == null || principal.userId() == null) {
			throw new BusinessException("ORGANIZATION_REQUIRED", "Selecciona un gimnasio para continuar.");
		}
	}

	private void requireConfigured() {
		if (properties.stripe().secretKey() == null || properties.stripe().secretKey().isBlank()) {
			throw new BusinessException("STRIPE_NOT_CONFIGURED", "Configura la credencial de plataforma de Stripe en el servidor.");
		}
	}

	public record ProviderLink(String url, String accountId, String expiresAt) {
	}
}
