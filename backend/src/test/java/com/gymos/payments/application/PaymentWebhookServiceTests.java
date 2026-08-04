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
import org.mockito.ArgumentCaptor;
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
		service = new PaymentWebhookService(
				properties, objectMapper, http, repository, configurationRepository,
				mercadoPagoProviderService, stripeRecurringBillingService, eventPublisher
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
		String payload = "{\"id\":\"evt_test_123\",\"type\":\"checkout.session.completed\",\"data\":{\"object\":{\"id\":\"cs_test_123\"}}}";
		long timestamp = Instant.now().getEpochSecond();
		String signature = "t=" + timestamp + ",v1=" + hmac(WEBHOOK_SECRET, timestamp + "." + payload);

		when(repository.operationByExternalId("STRIPE", "cs_test_123")).thenReturn(Optional.of(operation));
		when(repository.registerWebhook(eq("STRIPE"), eq("evt_test_123"), eq("checkout.session.completed"), anyString(), eq(organizationId))).thenReturn(true);
		when(configurationRepository.configuration(organizationId)).thenReturn(new PaymentConfigurationView(
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
		verify(repository).completeWebhook("STRIPE", "evt_test_123", "PROCESSED", null);
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
		String payload = "{\"id\":\"evt_checkout_pi\",\"type\":\"payment_intent.succeeded\",\"data\":{\"object\":{\"id\":\"pi_checkout\"}}}";
		long timestamp = Instant.now().getEpochSecond();
		String signature = "t=" + timestamp + ",v1=" + hmac(WEBHOOK_SECRET, timestamp + "." + payload);
		when(repository.operationByExternalId("STRIPE", "pi_checkout")).thenReturn(Optional.empty());

		service.stripe(payload, signature);

		verify(repository).operationByExternalId("STRIPE", "pi_checkout");
		verifyNoInteractions(eventPublisher);
	}

	@Test
	void recurringPaymentIntentSuccessPublishesAuthoritativeCompletion() throws Exception {
		UUID organizationId = UUID.randomUUID();
		UUID operationId = UUID.randomUUID();
		UUID paymentId = UUID.randomUUID();
		OperationRecord operation = recurringOperation(operationId, organizationId, paymentId, "pi_recurring_ok");
		String payload = "{\"id\":\"evt_recurring_ok\",\"type\":\"payment_intent.succeeded\",\"data\":{\"object\":{\"id\":\"pi_recurring_ok\"}}}";
		long timestamp = Instant.now().getEpochSecond();
		String signature = "t=" + timestamp + ",v1=" + hmac(WEBHOOK_SECRET, timestamp + "." + payload);

		when(repository.operationByExternalId("STRIPE", "pi_recurring_ok")).thenReturn(Optional.of(operation));
		when(repository.registerWebhook(eq("STRIPE"), eq("evt_recurring_ok"), eq("payment_intent.succeeded"), anyString(), eq(organizationId))).thenReturn(true);
		when(stripeRecurringBillingService.fetchPaymentIntent(operation)).thenReturn(objectMapper.readTree(
				"{\"id\":\"pi_recurring_ok\",\"status\":\"succeeded\",\"amount\":12345,\"amount_received\":12345,\"currency\":\"mxn\"}"
		));

		service.stripe(payload, signature);

		verify(stripeRecurringBillingService).completeRecurringCharge(eq(operation), anyString());
		ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
		verify(eventPublisher).publishEvent(eventCaptor.capture());
		ProviderPaymentCompletedV1 event = (ProviderPaymentCompletedV1) eventCaptor.getValue();
		assertThat(event.paymentId()).isEqualTo(paymentId);
		verify(repository).completeWebhook("STRIPE", "evt_recurring_ok", "PROCESSED", null);
	}

	@Test
	void recurringPaymentIntentFailureSchedulesRecoveryWithoutCompletingSale() throws Exception {
		UUID organizationId = UUID.randomUUID();
		OperationRecord operation = recurringOperation(
				UUID.randomUUID(), organizationId, UUID.randomUUID(), "pi_recurring_failed"
		);
		String payload = "{\"id\":\"evt_recurring_failed\",\"type\":\"payment_intent.payment_failed\",\"data\":{\"object\":{\"id\":\"pi_recurring_failed\"}}}";
		long timestamp = Instant.now().getEpochSecond();
		String signature = "t=" + timestamp + ",v1=" + hmac(WEBHOOK_SECRET, timestamp + "." + payload);

		when(repository.operationByExternalId("STRIPE", "pi_recurring_failed")).thenReturn(Optional.of(operation));
		when(repository.registerWebhook(eq("STRIPE"), eq("evt_recurring_failed"), eq("payment_intent.payment_failed"), anyString(), eq(organizationId))).thenReturn(true);
		when(stripeRecurringBillingService.fetchPaymentIntent(operation)).thenReturn(objectMapper.readTree(
				"{\"id\":\"pi_recurring_failed\",\"status\":\"requires_payment_method\",\"last_payment_error\":{\"decline_code\":\"insufficient_funds\"}}"
		));

		service.stripe(payload, signature);

		verify(stripeRecurringBillingService).failRecurringCharge(operation, "insufficient_funds");
		verifyNoInteractions(eventPublisher);
		verify(repository).completeWebhook("STRIPE", "evt_recurring_failed", "PROCESSED", null);
	}

	@Test
	void duplicateSignedWebhookDoesNotCompleteRecurringPaymentTwice() throws Exception {
		UUID organizationId = UUID.randomUUID();
		OperationRecord operation = recurringOperation(
				UUID.randomUUID(), organizationId, UUID.randomUUID(), "pi_recurring_duplicate"
		);
		String payload = "{\"id\":\"evt_recurring_duplicate\",\"type\":\"payment_intent.succeeded\",\"data\":{\"object\":{\"id\":\"pi_recurring_duplicate\"}}}";
		long timestamp = Instant.now().getEpochSecond();
		String signature = "t=" + timestamp + ",v1=" + hmac(WEBHOOK_SECRET, timestamp + "." + payload);
		when(repository.operationByExternalId("STRIPE", "pi_recurring_duplicate"))
				.thenReturn(Optional.of(operation));
		when(repository.registerWebhook(
				eq("STRIPE"), eq("evt_recurring_duplicate"), eq("payment_intent.succeeded"),
				anyString(), eq(organizationId)
		)).thenReturn(true, false);
		when(stripeRecurringBillingService.fetchPaymentIntent(operation)).thenReturn(objectMapper.readTree(
				"{\"id\":\"pi_recurring_duplicate\",\"status\":\"succeeded\",\"amount\":12345,\"amount_received\":12345,\"currency\":\"mxn\"}"
		));

		service.stripe(payload, signature);
		service.stripe(payload, signature);

		verify(stripeRecurringBillingService, times(1)).completeRecurringCharge(eq(operation), anyString());
		verify(eventPublisher, times(1)).publishEvent(any(Object.class));
		verify(repository, times(1)).completeWebhook("STRIPE", "evt_recurring_duplicate", "PROCESSED", null);
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
