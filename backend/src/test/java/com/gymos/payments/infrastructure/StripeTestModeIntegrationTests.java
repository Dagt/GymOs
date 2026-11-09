package com.gymos.payments.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymos.common.api.BusinessException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@Tag("stripe-test")
@EnabledIfEnvironmentVariable(named = "GYMOS_STRIPE_TEST_SECRET_KEY", matches = "sk_test_.+")
class StripeTestModeIntegrationTests {
	private static final String API = "https://api.stripe.com";
	private JdkProviderHttpGateway gateway;
	private Map<String, String> headers;

	@BeforeEach
	void setUp() {
		gateway = new JdkProviderHttpGateway(new ObjectMapper());
		headers = new LinkedHashMap<>();
		headers.put("Authorization", "Bearer " + System.getenv("GYMOS_STRIPE_TEST_SECRET_KEY"));
		String accountId = System.getenv("GYMOS_STRIPE_TEST_ACCOUNT_ID");
		if (accountId != null && !accountId.isBlank()) headers.put("Stripe-Account", accountId.trim());
	}

	@Test
	void idempotentRetryReturnsSamePaymentIntentAndCrashRecoveryConfirmsIt() {
		String idempotencyKey = "gymos-stripe-test-" + UUID.randomUUID();
		Map<String, String> createHeaders = new LinkedHashMap<>(headers);
		createHeaders.put("Idempotency-Key", idempotencyKey);
		String body = form(Map.of(
				"amount", "5000",
				"currency", "mxn",
				"payment_method", "pm_card_visa",
				"payment_method_types[]", "card",
				"metadata[gymos_test]", "recurring_recovery"
		));

		JsonNode first = gateway.exchange(
				"POST", API + "/v1/payment_intents", createHeaders, body, "application/x-www-form-urlencoded"
		);
		JsonNode retriedAfterUncertainResponse = gateway.exchange(
				"POST", API + "/v1/payment_intents", createHeaders, body, "application/x-www-form-urlencoded"
		);
		assertThat(retriedAfterUncertainResponse.path("id").asText()).isEqualTo(first.path("id").asText());
		assertThat(first.path("status").asText()).isEqualTo("requires_confirmation");

		JsonNode recovered = retrieve(first.path("id").asText());
		assertThat(recovered.path("status").asText()).isEqualTo("requires_confirmation");
		JsonNode confirmed = gateway.exchange(
				"POST", API + "/v1/payment_intents/" + first.path("id").asText() + "/confirm",
				headers, "off_session=true", "application/x-www-form-urlencoded"
		);
		assertThat(confirmed.path("status").asText()).isEqualTo("succeeded");
		assertThat(confirmed.path("livemode").asBoolean()).isFalse();
	}

	@Test
	void offSessionCardThatRequiresAuthenticationEndsInRequiresAction() {
		JsonNode intent = createIntent("pm_card_authenticationRequired", "requires_action");
		try {
			gateway.exchange(
					"POST", API + "/v1/payment_intents/" + intent.path("id").asText() + "/confirm",
					headers, "off_session=true", "application/x-www-form-urlencoded"
			);
		} catch (BusinessException expectedForStripeCardErrorResponse) {
			assertThat(expectedForStripeCardErrorResponse.code()).isEqualTo("PAYMENT_PROVIDER_HTTP_ERROR");
		}
		JsonNode recovered = retrieve(intent.path("id").asText());
		assertThat(recovered.path("status").asText()).isEqualTo("requires_action");
		assertThat(recovered.path("livemode").asBoolean()).isFalse();
	}

	@Test
	void failedOffSessionCardExposesRecoverableDeclineState() {
		JsonNode intent = createIntent("pm_card_chargeDeclinedInsufficientFunds", "declined");
		try {
			gateway.exchange(
					"POST", API + "/v1/payment_intents/" + intent.path("id").asText() + "/confirm",
					headers, "off_session=true", "application/x-www-form-urlencoded"
			);
		} catch (BusinessException expectedForStripeCardErrorResponse) {
			assertThat(expectedForStripeCardErrorResponse.code()).isEqualTo("PAYMENT_PROVIDER_HTTP_ERROR");
		}
		JsonNode recovered = retrieve(intent.path("id").asText());
		assertThat(recovered.path("status").asText()).isEqualTo("requires_payment_method");
		assertThat(recovered.path("last_payment_error").path("decline_code").asText())
				.isEqualTo("insufficient_funds");
	}

	private JsonNode createIntent(String paymentMethod, String scenario) {
		Map<String, String> requestHeaders = new LinkedHashMap<>(headers);
		requestHeaders.put("Idempotency-Key", "gymos-stripe-test-" + UUID.randomUUID());
		return gateway.exchange(
				"POST", API + "/v1/payment_intents", requestHeaders,
				form(Map.of(
						"amount", "5000", "currency", "mxn", "payment_method", paymentMethod,
						"payment_method_types[]", "card", "metadata[gymos_test]", scenario
				)),
				"application/x-www-form-urlencoded"
		);
	}

	@Test
	@EnabledIfEnvironmentVariable(named = "GYMOS_STRIPE_TEST_ACCOUNT_ID", matches = "acct_.+")
	void connectedCheckoutIsIdempotentAndCanExpireUnpaid() {
		String url = API + "/v1/checkout/sessions";
		Map<String, String> createHeaders = new LinkedHashMap<>(headers);
		createHeaders.put("Idempotency-Key", "gymos-stripe-test-checkout-" + UUID.randomUUID());
		String body = form(Map.of(
				"mode", "payment", "payment_method_types[0]", "card",
				"success_url", "https://example.invalid/test/success?session_id={CHECKOUT_SESSION_ID}",
				"cancel_url", "https://example.invalid/test/cancel",
				"line_items[0][quantity]", "1", "line_items[0][price_data][currency]", "mxn",
				"line_items[0][price_data][unit_amount]", "5000",
				"line_items[0][price_data][product_data][name]", "GymFort Checkout test"
		));
		JsonNode opened = gateway.exchange("POST", url, createHeaders, body, "application/x-www-form-urlencoded");
		JsonNode repeated = gateway.exchange("POST", url, createHeaders, body, "application/x-www-form-urlencoded");
		String id = opened.path("id").asText();
		assertThat(id).startsWith("cs_test_");
		assertThat(repeated.path("id").asText()).isEqualTo(id);
		assertThat(opened.path("livemode").asBoolean(true)).isFalse();
		assertThat(opened.path("status").asText()).isEqualTo("open");
		JsonNode expired = gateway.exchange("POST", url + "/" + id + "/expire", headers, "", "application/x-www-form-urlencoded");
		assertThat(expired.path("status").asText()).isEqualTo("expired");
		assertThat(expired.path("payment_status").asText()).isEqualTo("unpaid");
	}

	private JsonNode retrieve(String paymentIntentId) {
		return gateway.exchange("GET", API + "/v1/payment_intents/" + paymentIntentId, headers, null, null);
	}

	private String form(Map<String, String> values) {
		return values.entrySet().stream()
				.map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
				.collect(Collectors.joining("&"));
	}

	private String encode(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8);
	}
}
