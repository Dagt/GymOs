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
		configurationRepository.configuration(principal.organizationId());
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
		boolean connected = response.path("details_submitted").asBoolean(false)
				&& response.path("charges_enabled").asBoolean(false);
		providerRepository.saveStripeAccount(
				principal.organizationId(), principal.userId(), accountId, connected, json(response)
		);
		auditService.record(principal, connected ? "STRIPE_CONNECTED" : "STRIPE_CONNECTION_CHECKED",
				"PAYMENT_PROVIDER_CONNECTION", principal.organizationId(), "BACKOFFICE", null, correlationId);
		return configurationRepository.configuration(principal.organizationId());
	}

	@Override
	public CheckoutSessionResult createCheckout(CreateCheckoutSessionCommand command) {
		validateCheckout(command);
		PaymentConfigurationView configuration = configurationRepository.configuration(command.organizationId());
		if (!configuration.stripeCheckoutReady() || configuration.stripeAccountId() == null) {
			throw new BusinessException("STRIPE_CHECKOUT_DISABLED", "Stripe self checkout no esta habilitado para este gimnasio.");
		}
		if (command.recurring()) {
			throw new BusinessException(
					"STRIPE_RECURRING_REQUIRES_SCHEDULE",
					"El cobro recurrente requiere una periodicidad explicita del plan. No se creara un cargo unico por error."
			);
		}

		OperationRecord latest = providerRepository.latestOperationForPayment(
				command.organizationId(), PROVIDER, "CHECKOUT_SESSION", command.paymentId()
		).orElse(null);
		String operationKey = latest != null && retryableTerminal(latest.status())
				? "stripe-retry:" + command.paymentId() + ":" + latest.id()
				: command.idempotencyKey();
		UUID operationId = latest != null && !retryableTerminal(latest.status())
				? latest.id()
				: providerRepository.createOperation(new CreateOperationData(
						command.organizationId(), command.branchId(), PROVIDER, "CHECKOUT_SESSION",
						command.saleId(), command.paymentId(), command.clientId(), operationKey,
						command.amountCents(), normalizeCurrency(command.currency())
				));
		OperationRecord existing = providerRepository.operation(operationId).orElseThrow();
		if (existing.externalOperationId() != null && existing.checkoutUrl() != null) {
			return new CheckoutSessionResult(operationId, existing.externalOperationId(), existing.checkoutUrl(), existing.status());
		}

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
				stripeHeaders(configuration.stripeAccountId(), existing.idempotencyKey()),
				PaymentProviderEncoding.form(body), "application/x-www-form-urlencoded"
		);
		String sessionId = requiredText(response, "id", "STRIPE_SESSION_INVALID");
		String checkoutUrl = requiredText(response, "url", "STRIPE_SESSION_INVALID");
		String status = response.path("status").asText("open").toUpperCase();
		OffsetDateTime expiresAt = response.hasNonNull("expires_at")
				? OffsetDateTime.ofInstant(java.time.Instant.ofEpochSecond(response.path("expires_at").asLong()), java.time.ZoneOffset.UTC)
				: null;
		providerRepository.markOperationCreated(operationId, sessionId, status, checkoutUrl, json(response), expiresAt);
		return new CheckoutSessionResult(operationId, sessionId, checkoutUrl, status);
	}

	private String createConnectedAccount(GymOsPrincipal principal) {
		Map<String, Object> body = PaymentProviderEncoding.values();
		body.put("type", "express");
		body.put("country", "MX");
		body.put("capabilities[card_payments][requested]", true);
		body.put("capabilities[transfers][requested]", true);
		body.put("metadata[gymos_organization_id]", principal.organizationId());
		JsonNode response = http.exchange("POST", stripeUrl("/v1/accounts"), stripeHeaders(null),
				PaymentProviderEncoding.form(body), "application/x-www-form-urlencoded");
		String accountId = requiredText(response, "id", "STRIPE_ACCOUNT_INVALID");
		providerRepository.saveStripeAccount(principal.organizationId(), principal.userId(), accountId, false, json(response));
		return accountId;
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

	private boolean retryableTerminal(String status) {
		return status != null && java.util.Set.of("EXPIRED", "CANCELED", "CANCELLED", "REJECTED", "FAILED").contains(status.toUpperCase());
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
