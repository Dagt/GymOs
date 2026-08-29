package com.gymos.payments.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymos.audit.api.AuditService;
import com.gymos.common.api.BusinessException;
import com.gymos.payments.api.PaymentConfigurationView;
import com.gymos.payments.api.StripeRecurringOperations.BeginMandateSetupCommand;
import com.gymos.payments.api.StripeRecurringOperations.RecurringMembershipContext;
import com.gymos.payments.api.events.RecurringMandateActivatedV1;
import com.gymos.payments.application.port.ProviderHttpGateway;
import com.gymos.payments.infrastructure.PaymentConfigurationRepository;
import com.gymos.payments.infrastructure.PaymentProviderRepository;
import com.gymos.payments.infrastructure.PaymentProviderRepository.OperationRecord;
import com.gymos.payments.infrastructure.RecurringBillingRepository;
import com.gymos.payments.infrastructure.RecurringBillingRepository.ChargeRecord;
import com.gymos.payments.infrastructure.RecurringBillingRepository.CustomerRecord;
import com.gymos.payments.infrastructure.RecurringBillingRepository.MandateRecord;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

class StripeRecurringBillingServiceTests {
	private final ProviderHttpGateway http = mock(ProviderHttpGateway.class);
	private final RecurringBillingRepository recurringRepository = mock(RecurringBillingRepository.class);
	private final PaymentProviderRepository providerRepository = mock(PaymentProviderRepository.class);
	private final PaymentConfigurationRepository configurationRepository = mock(PaymentConfigurationRepository.class);
	private final AuditService auditService = mock(AuditService.class);
	private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
	private final ObjectMapper objectMapper = new ObjectMapper();
	private StripeRecurringBillingService service;

	@BeforeEach
	void setUp() {
		var properties = new PaymentProviderProperties(
				"credential-key", "http://localhost:3000",
				new PaymentProviderProperties.Stripe("sk_test", "whsec_test", null, null, "https://stripe.test"),
				new PaymentProviderProperties.MercadoPago(null, null, null, null, null, null)
		);
		service = new StripeRecurringBillingService(
				properties, http, recurringRepository, providerRepository, configurationRepository,
				objectMapper, auditService, eventPublisher
		);
	}

	@Test
	void setupRequiresExplicitCurrentConsent() {
		RecurringMembershipContext context = context();
		when(configurationRepository.configuration(context.organizationId(), context.branchId())).thenReturn(readyConfiguration());

		assertThatThrownBy(() -> service.beginSetup(new BeginMandateSetupCommand(
				context, StripeRecurringBillingService.TERMS_VERSION, false,
				"setup-key", "SELF_SERVICE_KIOSK", "correlation"
		))).isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.code()).isEqualTo("RECURRING_CONSENT_REQUIRED"));

		verify(recurringRepository, never()).createPendingMandate(any());
	}

	@Test
	void setupCheckoutUsesOnlySupportedSetupIntentParameters() throws Exception {
		RecurringMembershipContext context = context();
		UUID customerId = UUID.randomUUID();
		UUID mandateId = UUID.randomUUID();
		UUID operationId = UUID.randomUUID();
		CustomerRecord customer = new CustomerRecord(
				customerId, context.organizationId(), context.clientId(), "STRIPE", "cus_test"
		);
		MandateRecord mandate = new MandateRecord(
				mandateId, context.organizationId(), context.branchId(), context.clientId(),
				context.membershipId(), context.planId(), customerId, "cus_test", null, null,
				"STRIPE", "PENDING_SETUP", null, null, null, null,
				StripeRecurringBillingService.TERMS_VERSION, "Consentimiento",
				StripeRecurringBillingService.AMOUNT_RULE, OffsetDateTime.now(),
				"SELF_SERVICE_KIOSK", "setup-key", context.nextChargeOn(), 0, null, null
		);
		OperationRecord pendingOperation = new OperationRecord(
				operationId, context.organizationId(), context.branchId(), "STRIPE", "SETUP_SESSION",
				null, null, context.clientId(), mandateId, null, null, "stripe-setup:setup-key",
				"CREATED", 0, "MXN", null, "{}", null
		);
		OperationRecord createdOperation = new OperationRecord(
				operationId, context.organizationId(), context.branchId(), "STRIPE", "SETUP_SESSION",
				null, null, context.clientId(), mandateId, null, "cs_setup", "stripe-setup:setup-key",
				"OPEN", 0, "MXN", "https://checkout.stripe.test/setup", "{}", null
		);

		when(configurationRepository.configuration(context.organizationId(), context.branchId())).thenReturn(readyConfiguration());
		when(recurringRepository.reserveCustomer(context.organizationId(), context.clientId())).thenReturn(customer);
		when(recurringRepository.createPendingMandate(any())).thenReturn(mandateId);
		when(recurringRepository.mandate(mandateId)).thenReturn(Optional.of(mandate));
		when(providerRepository.createOperation(any())).thenReturn(operationId);
		when(providerRepository.operation(operationId)).thenReturn(
				Optional.of(pendingOperation), Optional.of(createdOperation)
		);
		when(http.exchange(
				eq("POST"), eq("https://stripe.test/v1/checkout/sessions"), any(), any(),
				eq("application/x-www-form-urlencoded")
		)).thenReturn(objectMapper.readTree(
				"{\"id\":\"cs_setup\",\"url\":\"https://checkout.stripe.test/setup\",\"status\":\"open\"}"
		));

		service.beginSetup(new BeginMandateSetupCommand(
				context, StripeRecurringBillingService.TERMS_VERSION, true,
				"setup-key", "SELF_SERVICE_KIOSK", "correlation"
		));

		ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
		verify(http).exchange(
				eq("POST"), eq("https://stripe.test/v1/checkout/sessions"), any(), body.capture(),
				eq("application/x-www-form-urlencoded")
		);
		assertThat(body.getValue()).contains("mode=setup");
		assertThat(body.getValue()).contains("setup_intent_data%5Bmetadata%5D%5Bgymos_mandate_id%5D");
		assertThat(body.getValue()).doesNotContain("setup_intent_data%5Busage%5D");
	}

	@Test
	void completedSetupPersistsOnlyOpaqueStripeReferenceAndCardMask() throws Exception {
		MandateRecord pending = mandate("PENDING_SETUP", null, null, null);
		OperationRecord operation = setupOperation(pending);
		MandateRecord active = mandate("ACTIVE", UUID.randomUUID(), "pm_tokenized", "4242");
		when(recurringRepository.mandate(pending.id())).thenReturn(Optional.of(pending));
		when(configurationRepository.providerConfiguration(pending.organizationId())).thenReturn(readyConfiguration());
		when(http.exchange(eq("GET"), eq("https://stripe.test/v1/checkout/sessions/cs_setup"), any(), isNull(), isNull()))
				.thenReturn(objectMapper.readTree("{\"id\":\"cs_setup\",\"mode\":\"setup\",\"status\":\"complete\",\"setup_intent\":\"seti_test\"}"));
		when(http.exchange(eq("GET"), eq("https://stripe.test/v1/setup_intents/seti_test"), any(), isNull(), isNull()))
				.thenReturn(objectMapper.readTree("{\"id\":\"seti_test\",\"status\":\"succeeded\",\"customer\":\"cus_test\",\"payment_method\":\"pm_tokenized\"}"));
		when(http.exchange(eq("GET"), eq("https://stripe.test/v1/payment_methods/pm_tokenized"), any(), isNull(), isNull()))
				.thenReturn(objectMapper.readTree("{\"id\":\"pm_tokenized\",\"type\":\"card\",\"card\":{\"brand\":\"visa\",\"last4\":\"4242\",\"exp_month\":12,\"exp_year\":2030}}"));
		when(recurringRepository.activateMandate(
				pending.id(), "pm_tokenized", "card", "visa", "4242", 12, 2030
		)).thenReturn(active);

		MandateRecord result = service.completeSetup(operation);

		assertThat(result.status()).isEqualTo("ACTIVE");
		verify(recurringRepository).activateMandate(
				pending.id(), "pm_tokenized", "card", "visa", "4242", 12, 2030
		);
		ArgumentCaptor<Object> event = ArgumentCaptor.forClass(Object.class);
		verify(eventPublisher).publishEvent(event.capture());
		assertThat(event.getValue()).isInstanceOf(RecurringMandateActivatedV1.class);
		verify(providerRepository).markOperationStatus(eq(operation.id()), eq("COMPLETED"), any(), eq(true));
	}

	@Test
	void uncertainCreateRetryReusesOriginalStripeIdempotencyKey() throws Exception {
		ChargeRecord charge = charge(2);
		String originalKey = "stripe-recurring:" + charge.id() + ":attempt:1";
		UUID operationId = UUID.randomUUID();
		OperationRecord uncertain = recurringOperation(charge, operationId, originalKey, null, "CREATED");
		OperationRecord created = recurringOperation(charge, operationId, originalKey, "pi_recovered", "REQUIRES_CONFIRMATION");
		OperationRecord processing = recurringOperation(charge, operationId, originalKey, "pi_recovered", "PROCESSING");
		when(configurationRepository.configuration(charge.organizationId(), charge.branchId())).thenReturn(readyConfiguration());
		when(configurationRepository.providerConfiguration(charge.organizationId())).thenReturn(readyConfiguration());
		when(providerRepository.latestOperationForRecurringCharge(charge.organizationId(), charge.id()))
				.thenReturn(Optional.of(uncertain));
		when(providerRepository.operation(uncertain.id())).thenReturn(
				Optional.of(uncertain), Optional.of(created), Optional.of(processing)
		);
		when(http.exchange(eq("POST"), eq("https://stripe.test/v1/payment_intents"), any(), any(), eq("application/x-www-form-urlencoded")))
				.thenReturn(objectMapper.readTree("{\"id\":\"pi_recovered\",\"status\":\"requires_confirmation\"}"));
		when(http.exchange(eq("POST"), eq("https://stripe.test/v1/payment_intents/pi_recovered/confirm"), any(), eq("off_session=true"), eq("application/x-www-form-urlencoded")))
				.thenReturn(objectMapper.readTree("{\"id\":\"pi_recovered\",\"status\":\"processing\"}"));

		OperationRecord result = service.startRecurringPayment(charge);

		assertThat(result.status()).isEqualTo("PROCESSING");
		verify(providerRepository, never()).createOperation(any());
		verify(http).exchange(
				eq("POST"), eq("https://stripe.test/v1/payment_intents"),
				org.mockito.ArgumentMatchers.argThat(headers -> originalKey.equals(headers.get("Idempotency-Key"))),
				any(), eq("application/x-www-form-urlencoded")
		);
	}

	private RecurringMembershipContext context() {
		return new RecurringMembershipContext(
				UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
				"Mensual", 75_000, "MXN", LocalDate.now().plusDays(10)
		);
	}

	private PaymentConfigurationView readyConfiguration() {
		return new PaymentConfigurationView(true, false, null, null, false, true, true, "acct_test", true);
	}

	private MandateRecord mandate(String status, UUID methodId, String externalMethodId, String lastFour) {
		return new MandateRecord(
				UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
				UUID.randomUUID(), UUID.randomUUID(), "cus_test", methodId, externalMethodId, "STRIPE", status,
				lastFour == null ? null : "visa", lastFour, lastFour == null ? null : 12,
				lastFour == null ? null : 2030, StripeRecurringBillingService.TERMS_VERSION,
				"Consentimiento", StripeRecurringBillingService.AMOUNT_RULE, OffsetDateTime.now(),
				"SELF_SERVICE_KIOSK", "setup-key", LocalDate.now().plusDays(10), 0, null, null
		);
	}

	private OperationRecord setupOperation(MandateRecord mandate) {
		return new OperationRecord(
				UUID.randomUUID(), mandate.organizationId(), mandate.branchId(), "STRIPE", "SETUP_SESSION",
				null, null, mandate.clientId(), mandate.id(), null, "cs_setup", "stripe-setup:key", "OPEN",
				0, "MXN", "https://checkout", "{}", null
		);
	}

	private ChargeRecord charge(int attempt) {
		return new ChargeRecord(
				UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
				UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "cus_test", UUID.randomUUID(),
				"pm_test", LocalDate.now(), "PROCESSING", UUID.randomUUID(), UUID.randomUUID(), null,
				75_000L, "MXN", attempt, OffsetDateTime.now(), OffsetDateTime.now().plusMinutes(5), null
		);
	}

	private OperationRecord recurringOperation(
			ChargeRecord charge,
			UUID operationId,
			String idempotencyKey,
			String paymentIntentId,
			String status
	) {
		return new OperationRecord(
				operationId, charge.organizationId(), charge.branchId(), "STRIPE", "RECURRING_PAYMENT_INTENT",
				charge.saleId(), charge.paymentId(), charge.clientId(), charge.mandateId(), charge.id(),
				paymentIntentId, idempotencyKey, status, charge.amountCents(), charge.currency(), null, "{}", null
		);
	}
}
