package com.gymos.payments.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymos.common.api.BusinessException;
import com.gymos.payments.api.PaymentConfigurationView;
import com.gymos.payments.api.events.ProviderPaymentCompletedV1;
import com.gymos.payments.application.port.ProviderHttpGateway;
import com.gymos.payments.infrastructure.PaymentConfigurationRepository;
import com.gymos.payments.infrastructure.PaymentProviderRepository;
import com.gymos.payments.infrastructure.PaymentProviderRepository.OperationRecord;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ApplicationEventPublisher;

class PaymentWebhookServiceTests {
	private static final String WEBHOOK_SECRET = "whsec_test_secret";
	private final ObjectMapper objectMapper = new ObjectMapper();
	private final ProviderHttpGateway http = mock(ProviderHttpGateway.class);
	private final PaymentProviderRepository repository = mock(PaymentProviderRepository.class);
	private final PaymentConfigurationRepository configurationRepository = mock(PaymentConfigurationRepository.class);
	private final MercadoPagoProviderService mercadoPagoProviderService = mock(MercadoPagoProviderService.class);
	private final StripeRecurringBillingService stripeRecurringBillingService = mock(StripeRecurringBillingService.class);
	private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
	private PaymentWebhookService service;

	@BeforeEach
	void setUp() {
		var properties = new PaymentProviderProperties(
				"credential-key", "http://localhost:3000",
				new PaymentProviderProperties.Stripe("sk_test", WEBHOOK_SECRET, null, null, "https://stripe.test"),
				new PaymentProviderProperties.MercadoPago("client", "secret", "http://redirect", "mp_secret", "https://mp.test", "https://auth.test")
		);
		when(repository.markOperationStatus(any(UUID.class), eq("COMPLETED"), anyString(), eq(true))).thenReturn(true);
		when(configurationRepository.providerConfiguration(any(UUID.class))).thenReturn(new PaymentConfigurationView(
				true, false, null, null, false, true, false, "acct_test", true
		));
		service = new PaymentWebhookService(
				properties, objectMapper, http, repository, configurationRepository,
				mercadoPagoProviderService, stripeRecurringBillingService,
				new ProviderPaymentCompletionService(repository, stripeRecurringBillingService, eventPublisher),
				com.gymos.common.observability.TenantTelemetry.noOp()
		);
	}

	@Test
	void stripeReconcilesAuthoritativeSessionBeforePublishingCompletion() throws Exception {
		UUID organizationId = UUID.randomUUID();
		UUID branchId = UUID.randomUUID();
		UUID saleId = UUID.randomUUID();
		UUID paymentId = UUID.randomUUID();
		UUID operationId = UUID.randomUUID();
		OperationRecord operation = new OperationRecord(
				operationId, organizationId, branchId, "STRIPE", "CHECKOUT_SESSION", saleId, paymentId,
				UUID.randomUUID(), "cs_test_123", "stripe:test", "OPEN", 12_345, "MXN", "https://checkout", "{}", null
		);
		String payload = "{\"id\":\"evt_test_123\",\"type\":\"checkout.session.completed\",\"account\":\"acct_test\",\"data\":{\"object\":{\"id\":\"cs_test_123\"}}}";
		long timestamp = Instant.now().getEpochSecond();
		String signature = "t=" + timestamp + ",v1=" + hmac(WEBHOOK_SECRET, timestamp + "." + payload);

		when(repository.operationByExternalId("STRIPE", "cs_test_123")).thenReturn(Optional.of(operation));
		when(repository.registerWebhook(eq("STRIPE"), eq("evt_test_123"), eq("checkout.session.completed"), anyString(), eq(organizationId), any(UUID.class))).thenReturn(true);
		when(configurationRepository.providerConfiguration(organizationId)).thenReturn(new PaymentConfigurationView(
				true, false, null, null, false, true, false, "acct_test", true
		));
		when(http.exchange(eq("GET"), eq("https://stripe.test/v1/checkout/sessions/cs_test_123?expand[]=payment_intent.latest_charge"), any(), isNull(), isNull()))
				.thenReturn(objectMapper.readTree("{\"id\":\"cs_test_123\",\"status\":\"complete\",\"payment_status\":\"paid\",\"amount_total\":12345,\"currency\":\"mxn\",\"payment_intent\":{\"latest_charge\":{\"receipt_url\":\"https://pay.stripe.com/receipts/test\"}}}"));

		service.stripe(payload, signature);

		ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
		verify(eventPublisher).publishEvent(eventCaptor.capture());
		ProviderPaymentCompletedV1 event = (ProviderPaymentCompletedV1) eventCaptor.getValue();
		assertThat(event.organizationId()).isEqualTo(organizationId);
		assertThat(event.paymentId()).isEqualTo(paymentId);
		assertThat(event.amountCents()).isEqualTo(12_345);
		verify(repository).markOperationStatus(eq(operationId), eq("COMPLETED"), anyString(), eq(true));
		verify(repository).saveReceiptUrl(operationId, "https://pay.stripe.com/receipts/test");
		verify(repository).completeWebhook(eq("STRIPE"), eq("evt_test_123"), eq("PROCESSED"), isNull(), any(UUID.class));
	}

	@Test
	void stripeRejectsInvalidSignatureBeforeReadingAnyOperation() {
		String payload = "{\"id\":\"evt_bad\"}";
		long timestamp = Instant.now().getEpochSecond();

		assertThatThrownBy(() -> service.stripe(payload, "t=" + timestamp + ",v1=bad"))
				.isInstanceOfSatisfying(BusinessException.class, exception ->
						assertThat(exception.code()).isEqualTo("STRIPE_WEBHOOK_SIGNATURE_INVALID"));
	}

	@Test
	void stripeIgnoresPaymentIntentEmittedForCheckoutSession() throws Exception {
		String payload = "{\"id\":\"evt_checkout_pi\",\"type\":\"payment_intent.succeeded\",\"account\":\"acct_test\",\"data\":{\"object\":{\"id\":\"pi_checkout\"}}}";
		long timestamp = Instant.now().getEpochSecond();
		String signature = "t=" + timestamp + ",v1=" + hmac(WEBHOOK_SECRET, timestamp + "." + payload);
		when(repository.operationByExternalId("STRIPE", "pi_checkout")).thenReturn(Optional.empty());

		service.stripe(payload, signature);

		verify(repository).operationByExternalId("STRIPE", "pi_checkout");
		verifyNoInteractions(eventPublisher);
	}

	@Test
	@ExtendWith(OutputCaptureExtension.class)
	void stripeReportsUnmatchedCheckoutSession(CapturedOutput output) throws Exception {
		String payload = "{\"id\":\"evt_unmatched_checkout\",\"type\":\"checkout.session.completed\",\"account\":\"acct_test\",\"data\":{\"object\":{\"id\":\"cs_unmatched\"}}}";
		long timestamp = Instant.now().getEpochSecond();
		String signature = "t=" + timestamp + ",v1=" + hmac(WEBHOOK_SECRET, timestamp + "." + payload);
		when(repository.operationByExternalId("STRIPE", "cs_unmatched")).thenReturn(Optional.empty());

		service.stripe(payload, signature);

		assertThat(output)
				.contains("Unmatched Stripe Checkout webhook")
				.contains("evt_unmatched_checkout")
				.contains("cs_unmatched")
				.contains("acct_test");
		verifyNoInteractions(eventPublisher);
	}

	@Test
	void recurringPaymentIntentSuccessPublishesAuthoritativeCompletion() throws Exception {
		UUID organizationId = UUID.randomUUID();
		UUID operationId = UUID.randomUUID();
		UUID paymentId = UUID.randomUUID();
		OperationRecord operation = recurringOperation(operationId, organizationId, paymentId, "pi_recurring_ok");
		String payload = "{\"id\":\"evt_recurring_ok\",\"type\":\"payment_intent.succeeded\",\"account\":\"acct_test\",\"data\":{\"object\":{\"id\":\"pi_recurring_ok\"}}}";
		long timestamp = Instant.now().getEpochSecond();
		String signature = "t=" + timestamp + ",v1=" + hmac(WEBHOOK_SECRET, timestamp + "." + payload);

		when(repository.operationByExternalId("STRIPE", "pi_recurring_ok")).thenReturn(Optional.of(operation));
		when(repository.registerWebhook(eq("STRIPE"), eq("evt_recurring_ok"), eq("payment_intent.succeeded"), anyString(), eq(organizationId), any(UUID.class))).thenReturn(true);
		when(stripeRecurringBillingService.fetchPaymentIntent(operation)).thenReturn(objectMapper.readTree(
				"{\"id\":\"pi_recurring_ok\",\"status\":\"succeeded\",\"amount\":12345,\"amount_received\":12345,\"currency\":\"mxn\"}"
		));

		service.stripe(payload, signature);

		verify(stripeRecurringBillingService).completeRecurringCharge(eq(operation), anyString());
		ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
		verify(eventPublisher).publishEvent(eventCaptor.capture());
		ProviderPaymentCompletedV1 event = (ProviderPaymentCompletedV1) eventCaptor.getValue();
		assertThat(event.paymentId()).isEqualTo(paymentId);
		verify(repository).completeWebhook(eq("STRIPE"), eq("evt_recurring_ok"), eq("PROCESSED"), isNull(), any(UUID.class));
	}

	@Test
	void recurringPaymentIntentFailureSchedulesRecoveryWithoutCompletingSale() throws Exception {
		UUID organizationId = UUID.randomUUID();
		OperationRecord operation = recurringOperation(
				UUID.randomUUID(), organizationId, UUID.randomUUID(), "pi_recurring_failed"
		);
		String payload = "{\"id\":\"evt_recurring_failed\",\"type\":\"payment_intent.payment_failed\",\"account\":\"acct_test\",\"data\":{\"object\":{\"id\":\"pi_recurring_failed\"}}}";
		long timestamp = Instant.now().getEpochSecond();
		String signature = "t=" + timestamp + ",v1=" + hmac(WEBHOOK_SECRET, timestamp + "." + payload);

		when(repository.operationByExternalId("STRIPE", "pi_recurring_failed")).thenReturn(Optional.of(operation));
		when(repository.registerWebhook(eq("STRIPE"), eq("evt_recurring_failed"), eq("payment_intent.payment_failed"), anyString(), eq(organizationId), any(UUID.class))).thenReturn(true);
		when(stripeRecurringBillingService.fetchPaymentIntent(operation)).thenReturn(objectMapper.readTree(
				"{\"id\":\"pi_recurring_failed\",\"status\":\"requires_payment_method\",\"last_payment_error\":{\"decline_code\":\"insufficient_funds\"}}"
		));

		service.stripe(payload, signature);

		verify(stripeRecurringBillingService).failRecurringCharge(operation, "insufficient_funds");
		verifyNoInteractions(eventPublisher);
		verify(repository).completeWebhook(eq("STRIPE"), eq("evt_recurring_failed"), eq("PROCESSED"), isNull(), any(UUID.class));
	}

	@Test
	void duplicateSignedWebhookDoesNotCompleteRecurringPaymentTwice() throws Exception {
		UUID organizationId = UUID.randomUUID();
		OperationRecord operation = recurringOperation(
				UUID.randomUUID(), organizationId, UUID.randomUUID(), "pi_recurring_duplicate"
		);
		String payload = "{\"id\":\"evt_recurring_duplicate\",\"type\":\"payment_intent.succeeded\",\"account\":\"acct_test\",\"data\":{\"object\":{\"id\":\"pi_recurring_duplicate\"}}}";
		long timestamp = Instant.now().getEpochSecond();
		String signature = "t=" + timestamp + ",v1=" + hmac(WEBHOOK_SECRET, timestamp + "." + payload);
		when(repository.operationByExternalId("STRIPE", "pi_recurring_duplicate"))
				.thenReturn(Optional.of(operation));
		when(repository.registerWebhook(
				eq("STRIPE"), eq("evt_recurring_duplicate"), eq("payment_intent.succeeded"),
				anyString(), eq(organizationId), any(UUID.class)
		)).thenReturn(true, false);
		when(stripeRecurringBillingService.fetchPaymentIntent(operation)).thenReturn(objectMapper.readTree(
				"{\"id\":\"pi_recurring_duplicate\",\"status\":\"succeeded\",\"amount\":12345,\"amount_received\":12345,\"currency\":\"mxn\"}"
		));

		service.stripe(payload, signature);
		service.stripe(payload, signature);

		verify(stripeRecurringBillingService, times(1)).completeRecurringCharge(eq(operation), anyString());
		verify(eventPublisher, times(1)).publishEvent(any(Object.class));
		verify(repository, times(1)).completeWebhook(eq("STRIPE"), eq("evt_recurring_duplicate"), eq("PROCESSED"), isNull(), any(UUID.class));
	}

	@Test
	void signedStripeDeliveryFromAnotherConnectedAccountCannotConfirmPayment() throws Exception {
		UUID organizationId = UUID.randomUUID();
		OperationRecord operation = recurringOperation(UUID.randomUUID(), organizationId, UUID.randomUUID(), "pi_scoped");
		String payload = """
				{"id":"evt_other_account","type":"payment_intent.succeeded","account":"acct_other","data":{"object":{"id":"pi_scoped"}}}
				""";
		long timestamp = Instant.now().getEpochSecond();
		when(repository.operationByExternalId("STRIPE", "pi_scoped")).thenReturn(Optional.of(operation));

		String signature = "t=" + timestamp + ",v1=" + hmac(WEBHOOK_SECRET, timestamp + "." + payload);
		assertThatThrownBy(() -> service.stripe(payload, signature)).isInstanceOfSatisfying(BusinessException.class,
				error -> assertThat(error.code()).isEqualTo("STRIPE_WEBHOOK_ACCOUNT_MISMATCH"));
		verifyNoInteractions(http, eventPublisher);
	}

	@Test
	void pointParsesExactTextualAmountAndRejectsMismatchedCurrency() throws Exception {
		UUID organizationId = UUID.randomUUID();
		UUID saleId = UUID.randomUUID();
		OperationRecord operation = new OperationRecord(UUID.randomUUID(), organizationId, UUID.randomUUID(),
				"MERCADO_PAGO", "POINT_ORDER", saleId, UUID.randomUUID(), null, "ORD-test",
				"point-test", "AWAITING_PAYMENT", 12_345, "MXN", null, "{}", null);
		when(mercadoPagoProviderService.accessToken(organizationId)).thenReturn("test");
		String order = """
				{"id":"ORD-test","external_reference":"gymos-%s","status":"processed",
				 "transactions":{"payments":[{"status":"processed","amount":"123.45","currency_id":"%s"}]}}
				""";
		when(http.exchange("GET", "https://mp.test/v1/orders/ORD-test", Map.of("Authorization", "Bearer test"), null, null))
				.thenReturn(objectMapper.readTree(order.formatted(saleId, "USD")),
						objectMapper.readTree(order.formatted(saleId, "MXN")));
		assertThatThrownBy(() -> service.reconcile(operation)).isInstanceOfSatisfying(BusinessException.class,
				error -> assertThat(error.code()).isEqualTo("PROVIDER_PAYMENT_AMOUNT_MISMATCH"));
		verifyNoInteractions(eventPublisher);
		service.reconcile(operation);
		verify(eventPublisher).publishEvent(any(Object.class));
	}

	private OperationRecord recurringOperation(
			UUID operationId,
			UUID organizationId,
			UUID paymentId,
			String paymentIntentId
	) {
		return new OperationRecord(
				operationId, organizationId, UUID.randomUUID(), "STRIPE", "RECURRING_PAYMENT_INTENT",
				UUID.randomUUID(), paymentId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
				paymentIntentId, "stripe-recurring:test", "PROCESSING", 12_345, "MXN", null, "{}", null
		);
	}

	private String hmac(String secret, String value) throws Exception {
		Mac mac = Mac.getInstance("HmacSHA256");
		mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
		return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
	}
}
