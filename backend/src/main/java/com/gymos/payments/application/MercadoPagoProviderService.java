package com.gymos.payments.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gymos.audit.api.AuditService;
import com.gymos.common.api.BusinessException;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.payments.api.MercadoPagoPointOperations;
import com.gymos.payments.api.PaymentConfigurationView;
import com.gymos.payments.application.port.CredentialCipher;
import com.gymos.payments.application.port.ProviderHttpGateway;
import com.gymos.payments.infrastructure.PaymentConfigurationRepository;
import com.gymos.payments.infrastructure.PaymentProviderRepository;
import com.gymos.payments.infrastructure.PaymentProviderRepository.ConnectionRecord;
import com.gymos.payments.infrastructure.PaymentProviderRepository.CreateOperationData;
import com.gymos.payments.infrastructure.PaymentProviderRepository.OperationRecord;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class MercadoPagoProviderService implements MercadoPagoPointOperations {
	private static final String PROVIDER = "MERCADO_PAGO";
	private final PaymentProviderProperties properties;
	private final ProviderHttpGateway http;
	private final CredentialCipher cipher;
	private final PaymentProviderRepository providerRepository;
	private final PaymentConfigurationRepository configurationRepository;
	private final ObjectMapper objectMapper;
	private final AuditService auditService;

	public MercadoPagoProviderService(
			PaymentProviderProperties properties,
			ProviderHttpGateway http,
			CredentialCipher cipher,
			PaymentProviderRepository providerRepository,
			PaymentConfigurationRepository configurationRepository,
			ObjectMapper objectMapper,
			AuditService auditService
	) {
		this.properties = properties;
		this.http = http;
		this.cipher = cipher;
		this.providerRepository = providerRepository;
		this.configurationRepository = configurationRepository;
		this.objectMapper = objectMapper;
		this.auditService = auditService;
	}

	public ProviderLink beginOauth(GymOsPrincipal principal, String correlationId) {
		requireTenant(principal);
		requireConfigured();
		configurationRepository.providerConfiguration(principal.organizationId());
		String state = PaymentProviderEncoding.randomUrlSafe(32);
		String verifier = PaymentProviderEncoding.randomUrlSafe(48);
		providerRepository.createOauthRequest(
				PaymentProviderEncoding.sha256Hex(state), principal.organizationId(), principal.userId(), PROVIDER,
				cipher.encrypt(verifier), OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(10)
		);
		Map<String, Object> query = PaymentProviderEncoding.values();
		query.put("client_id", properties.mercadoPago().clientId().trim());
		query.put("response_type", "code");
		query.put("platform_id", "mp");
		query.put("redirect_uri", redirectUri());
		query.put("state", state);
		query.put("code_challenge", PaymentProviderEncoding.sha256UrlSafe(verifier));
		query.put("code_challenge_method", "S256");
		auditService.record(principal, "MERCADO_PAGO_OAUTH_STARTED", "PAYMENT_PROVIDER_CONNECTION",
				principal.organizationId(), "BACKOFFICE", null, correlationId);
		return new ProviderLink(PaymentProviderEncoding.query(properties.mercadoPago().authorizationBaseUrl(), query));
	}

	public String completeOauth(String code, String state, String correlationId) {
		requireConfigured();
		if (blank(code) || blank(state)) {
			throw new BusinessException("MERCADO_PAGO_OAUTH_INVALID", "Mercado Pago no devolvio el codigo y estado requeridos.");
		}
		PaymentProviderRepository.OauthRequestRecord request = providerRepository.consumeOauthRequest(
				PaymentProviderEncoding.sha256Hex(state), PROVIDER, OffsetDateTime.now(ZoneOffset.UTC)
		).orElseThrow(() -> new BusinessException(
				"MERCADO_PAGO_OAUTH_STATE_INVALID", "La vinculacion vencio o ya fue utilizada. Inicia el proceso nuevamente."
		));

		ObjectNode body = objectMapper.createObjectNode();
		body.put("client_id", properties.mercadoPago().clientId().trim());
		body.put("client_secret", properties.mercadoPago().clientSecret().trim());
		body.put("grant_type", "authorization_code");
		body.put("code", code.trim());
		body.put("redirect_uri", redirectUri());
		body.put("code_verifier", cipher.decrypt(request.codeVerifierEncrypted()));
		JsonNode response = http.exchange("POST", mercadoPagoUrl("/oauth/token"), Map.of(), json(body), "application/json");
		String accessToken = requiredText(response, "access_token", "MERCADO_PAGO_TOKEN_INVALID");
		String refreshToken = requiredText(response, "refresh_token", "MERCADO_PAGO_TOKEN_INVALID");
		String accountId = requiredText(response, "user_id", "MERCADO_PAGO_TOKEN_INVALID");
		OffsetDateTime expiresAt = OffsetDateTime.now(ZoneOffset.UTC).plusSeconds(Math.max(60, response.path("expires_in").asLong(15_552_000)));
		providerRepository.saveMercadoPagoConnection(
				request.organizationId(), request.actorUserId(), accountId,
				cipher.encrypt(accessToken), cipher.encrypt(refreshToken), expiresAt, tokenMetadata(response)
		);
		auditService.recordSystem(request.organizationId(), null, request.actorUserId(), "MERCADO_PAGO_CONNECTED",
				"PAYMENT_PROVIDER_CONNECTION", request.organizationId(), "BACKOFFICE", null, correlationId);
		return properties.frontendBaseUrl().replaceAll("/+$", "") + "/settings/organization?payment=mercado-pago-connected";
	}

	public List<TerminalView> terminals(GymOsPrincipal principal) {
		requireTenant(principal);
		String accessToken = accessToken(principal.organizationId());
		JsonNode response = http.exchange(
				"GET", mercadoPagoUrl("/terminals/v1/list?limit=50&offset=0"), bearer(accessToken), null, null
		);
		JsonNode terminals = response.path("terminals");
		if (!terminals.isArray()) terminals = response.path("data").path("terminals");
		List<TerminalView> result = new ArrayList<>();
		if (terminals.isArray()) {
			for (JsonNode terminal : terminals) {
				result.add(new TerminalView(
						terminal.path("id").asText(),
						firstText(terminal, "external_id", "name", "id"),
						firstText(terminal, "status", "operating_mode"),
						firstText(terminal, "store_id", "store", "pos_id")
				));
			}
		}
		return List.copyOf(result);
	}

	public PaymentConfigurationView selectTerminal(GymOsPrincipal principal, String terminalId, String correlationId) {
		requireTenant(principal);
		if (blank(terminalId) || terminalId.length() > 160) {
			throw new BusinessException("MERCADO_PAGO_TERMINAL_INVALID", "Selecciona una terminal Point valida.");
		}
		String accessToken = accessToken(principal.organizationId());
		ObjectNode body = objectMapper.createObjectNode();
		ArrayNode terminals = body.putArray("terminals");
		ObjectNode terminal = terminals.addObject();
		terminal.put("id", terminalId.trim());
		terminal.put("operating_mode", "PDV");
		http.exchange("PATCH", mercadoPagoUrl("/terminals/v1/setup"), bearer(accessToken), json(body), "application/json");
		configurationRepository.selectMercadoPagoTerminal(principal.organizationId(), principal.branchId(), principal.userId(), terminalId.trim());
		auditService.record(principal, "MERCADO_PAGO_TERMINAL_SELECTED", "ORGANIZATION_PAYMENT_CONFIGURATION",
				principal.organizationId(), "BACKOFFICE", terminalId.trim(), correlationId);
		return configurationRepository.configuration(principal.organizationId(), principal.branchId());
	}

	@Override
	public PointOrderResult createOrder(CreatePointOrderCommand command) {
		validateOrder(command);
		PaymentConfigurationView configuration = configurationRepository.configuration(command.organizationId(), command.branchId());
		if (!configuration.mercadoPagoReady()) {
			throw new BusinessException("MERCADO_PAGO_POINT_DISABLED", "El cobro integrado con Mercado Pago Point no esta habilitado.");
		}
		OperationRecord latest = providerRepository.latestOperationForPayment(
				command.organizationId(), PROVIDER, "POINT_ORDER", command.paymentId()
		).orElse(null);
		UUID operationId = latest != null
				? latest.id()
				: providerRepository.createOperation(new CreateOperationData(
						command.organizationId(), command.branchId(), PROVIDER, "POINT_ORDER",
						command.saleId(), command.paymentId(), command.clientId(), command.idempotencyKey(),
						command.amountCents(), normalizeCurrency(command.currency())
				));
		OperationRecord existing = providerRepository.operation(operationId).orElseThrow();
		if (existing.externalOperationId() != null) {
			return new PointOrderResult(operationId, existing.externalOperationId(), existing.status());
		}

		ObjectNode body = objectMapper.createObjectNode();
		body.put("type", "point");
		body.put("external_reference", compactReference(command.saleId()));
		body.put("description", "GymOS venta " + command.saleId());
		body.put("expiration_time", "PT10M");
		ObjectNode payment = body.putObject("transactions").putArray("payments").addObject();
		payment.put("amount", BigDecimal.valueOf(command.amountCents(), 2));
		ObjectNode point = body.putObject("config").putObject("point");
		point.put("terminal_id", configuration.mercadoPagoTerminalId());
		point.put("print_on_terminal", "no_ticket");

		Map<String, String> headers = new LinkedHashMap<>(bearer(accessToken(command.organizationId())));
		headers.put("X-Idempotency-Key", existing.idempotencyKey());
		JsonNode response = http.exchange("POST", mercadoPagoUrl("/v1/orders"), headers, json(body), "application/json");
		String orderId = requiredText(response, "id", "MERCADO_PAGO_ORDER_INVALID");
		String status = response.path("status").asText("created").toUpperCase();
		providerRepository.markOperationCreated(operationId, orderId, "AWAITING_PAYMENT", null, json(response), OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(10));
		return new PointOrderResult(operationId, orderId, status);
	}

	String accessToken(UUID organizationId) {
		requireConfigured();
		ConnectionRecord connection = providerRepository.connection(organizationId, PROVIDER)
				.orElseThrow(() -> new BusinessException("MERCADO_PAGO_NOT_LINKED", "Vincula una cuenta de Mercado Pago para continuar."));
		if (blank(connection.accessTokenEncrypted())) {
			throw new BusinessException("MERCADO_PAGO_TOKEN_MISSING", "La vinculacion de Mercado Pago no contiene una credencial valida.");
		}
		if (connection.tokenExpiresAt() != null && connection.tokenExpiresAt().isBefore(OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(2))) {
			return refreshToken(connection);
		}
		return cipher.decrypt(connection.accessTokenEncrypted());
	}

	private String refreshToken(ConnectionRecord connection) {
		if (blank(connection.refreshTokenEncrypted())) {
			throw new BusinessException("MERCADO_PAGO_REAUTH_REQUIRED", "La sesion de Mercado Pago vencio. Vuelve a vincular la cuenta.");
		}
		ObjectNode body = objectMapper.createObjectNode();
		body.put("client_id", properties.mercadoPago().clientId().trim());
		body.put("client_secret", properties.mercadoPago().clientSecret().trim());
		body.put("grant_type", "refresh_token");
		body.put("refresh_token", cipher.decrypt(connection.refreshTokenEncrypted()));
		JsonNode response = http.exchange("POST", mercadoPagoUrl("/oauth/token"), Map.of(), json(body), "application/json");
		String accessToken = requiredText(response, "access_token", "MERCADO_PAGO_TOKEN_INVALID");
		String refreshToken = response.path("refresh_token").asText(cipher.decrypt(connection.refreshTokenEncrypted()));
		OffsetDateTime expiresAt = OffsetDateTime.now(ZoneOffset.UTC).plusSeconds(Math.max(60, response.path("expires_in").asLong(15_552_000)));
		providerRepository.updateMercadoPagoTokens(connection.organizationId(), cipher.encrypt(accessToken), cipher.encrypt(refreshToken), expiresAt, tokenMetadata(response));
		return accessToken;
	}

	private void validateOrder(CreatePointOrderCommand command) {
		requireConfigured();
		if (command == null || command.organizationId() == null || command.saleId() == null || command.paymentId() == null) {
			throw new BusinessException("MERCADO_PAGO_ORDER_INVALID", "La venta y el pago son obligatorios para iniciar Point.");
		}
		if (command.amountCents() <= 0 || blank(command.idempotencyKey())) {
			throw new BusinessException("MERCADO_PAGO_ORDER_INVALID", "El monto y la llave de idempotencia son obligatorios.");
		}
		if (!"MXN".equals(normalizeCurrency(command.currency()))) {
			throw new BusinessException("MERCADO_PAGO_CURRENCY_INVALID", "Mercado Pago Point solo esta habilitado para cobros en MXN.");
		}
	}

	private Map<String, String> bearer(String accessToken) {
		return Map.of("Authorization", "Bearer " + accessToken);
	}

	private String mercadoPagoUrl(String path) {
		return properties.mercadoPago().apiBaseUrl().replaceAll("/+$", "") + path;
	}

	private String redirectUri() {
		if (blank(properties.mercadoPago().redirectUri())) {
			throw new BusinessException("MERCADO_PAGO_REDIRECT_NOT_CONFIGURED", "Configura la URL de retorno OAuth de Mercado Pago.");
		}
		return properties.mercadoPago().redirectUri().trim();
	}

	private String compactReference(UUID saleId) {
		return "gymos-" + saleId;
	}

	private String requiredText(JsonNode node, String field, String code) {
		String value = node != null && node.hasNonNull(field) ? node.path(field).asText() : null;
		if (blank(value)) throw new BusinessException(code, "Mercado Pago devolvio una respuesta incompleta.");
		return value;
	}

	private String firstText(JsonNode node, String... fields) {
		for (String field : fields) {
			if (node.hasNonNull(field) && !node.path(field).asText().isBlank()) return node.path(field).asText();
		}
		return null;
	}

	private String json(JsonNode node) {
		try {
			return objectMapper.writeValueAsString(node);
		} catch (Exception exception) {
			throw new IllegalStateException("No fue posible serializar la respuesta de Mercado Pago.", exception);
		}
	}

	private String tokenMetadata(JsonNode response) {
		ObjectNode metadata = objectMapper.createObjectNode();
		copyIfPresent(response, metadata, "user_id");
		copyIfPresent(response, metadata, "public_key");
		copyIfPresent(response, metadata, "scope");
		copyIfPresent(response, metadata, "token_type");
		copyIfPresent(response, metadata, "expires_in");
		copyIfPresent(response, metadata, "live_mode");
		return json(metadata);
	}

	private void copyIfPresent(JsonNode source, ObjectNode target, String field) {
		if (source.has(field) && !source.path(field).isNull()) target.set(field, source.path(field));
	}

	private String normalizeCurrency(String currency) {
		return blank(currency) ? "MXN" : currency.trim().toUpperCase();
	}

	private boolean blank(String value) {
		return value == null || value.isBlank();
	}

	private void requireTenant(GymOsPrincipal principal) {
		if (principal == null || principal.organizationId() == null || principal.userId() == null) {
			throw new BusinessException("ORGANIZATION_REQUIRED", "Selecciona un gimnasio para continuar.");
		}
	}

	private void requireConfigured() {
		if (blank(properties.mercadoPago().clientId()) || blank(properties.mercadoPago().clientSecret())) {
			throw new BusinessException("MERCADO_PAGO_NOT_CONFIGURED", "Configura las credenciales OAuth de Mercado Pago en el servidor.");
		}
	}

	public record ProviderLink(String url) {
	}

	public record TerminalView(String id, String name, String status, String storeId) {
	}
}
