package com.gymos.payments.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gymos.common.api.BusinessException;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.payments.api.StripeCheckoutOperations.CreateCheckoutSessionCommand;
import com.gymos.payments.application.port.ProviderHttpGateway;
import com.gymos.payments.infrastructure.PaymentConfigurationRepository;
import com.gymos.payments.infrastructure.PaymentProviderRepository;
import com.gymos.payments.infrastructure.PaymentProviderRepository.CreateOperationData;
import com.gymos.sales.application.SalesService;
import com.gymos.sales.application.command.RequestRefundCommand;
import com.gymos.selfservice.application.SelfServiceService;
import com.gymos.selfservice.application.result.CreateSessionResult;
import com.gymos.selfservice.infrastructure.SelfServiceRepository;
import com.gymos.support.OperationalTestData;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Tag("postgres-integration")
@Testcontainers
@SpringBootTest(properties = {
		"spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=none",
		"gymos.payment-providers.mercado-pago.webhook-secret=point-test-secret",
		"gymos.payment-providers.mercado-pago.api-base-url=https://point.test",
		"gymos.payment-providers.stripe.secret-key=sk_test_fixture",
		"gymos.payment-providers.stripe.webhook-secret=stripe-test-secret",
		"gymos.payment-providers.stripe.api-base-url=https://stripe.test",
		"gymos.payment-providers.reconciliation-delay-ms=3600000"
})
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PointWebhookPostgresIntegrationTests {
	@Container
	static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");

	@DynamicPropertySource
	static void database(DynamicPropertyRegistry properties) {
		properties.add("spring.datasource.url", postgres::getJdbcUrl);
		properties.add("spring.datasource.username", postgres::getUsername);
		properties.add("spring.datasource.password", postgres::getPassword);
	}

	@Autowired JdbcClient jdbc;
	@Autowired MockMvc http;
	@Autowired PaymentProviderRepository operations;
	@Autowired PaymentConfigurationRepository paymentConfiguration;
	@Autowired StripeProviderService stripe;
	@Autowired SelfServiceService selfService;
	@Autowired SalesService sales;
	@Autowired ObjectMapper json;
	@MockitoBean MercadoPagoProviderService point;
	@MockitoBean ProviderHttpGateway gateway;

	@Test
	void signedWebhookKeepsAnUnverifiablePaymentPendingThenAppliesTheCorrectedOrderOnce() throws Exception {
		var tenant = new OperationalTestData(jdbc);
		UUID sale = UUID.randomUUID();
		UUID payment = UUID.randomUUID();
		jdbc.sql("""
				INSERT INTO sale(id, organization_id, branch_id, sale_number, status, subtotal_cents, total_cents,
				                 paid_cents, balance_cents, currency, sold_at, created_at, updated_at)
				VALUES (:id,:org,:branch,'POINT-QA','PENDING_PAYMENT',12345,12345,0,12345,'MXN',now(),now(),now())
				""").param("id", sale).param("org", tenant.organizationId).param("branch", tenant.branchId).update();
		jdbc.sql("""
				INSERT INTO payment(id, organization_id, branch_id, sale_id, method, status, amount_cents,
				                    currency, received_at, created_at, updated_at)
				VALUES (:id,:org,:branch,:sale,'CARD_MERCADO_PAGO','PENDING_VALIDATION',12345,'MXN',now(),now(),now())
				""").param("id", payment).param("org", tenant.organizationId).param("branch", tenant.branchId).param("sale", sale).update();

		String orderId = "ORD-test";
		UUID operationId = operations.createOperation(new CreateOperationData(tenant.organizationId, tenant.branchId,
				"MERCADO_PAGO", "POINT_ORDER", sale, payment, null, "point-fixture", 12345, "MXN"));
		operations.markOperationCreated(operationId, orderId, "AWAITING_PAYMENT", null,
				"{\"status\":\"created\"}", OffsetDateTime.now().plusMinutes(10));
		String order = """
				{"id":"ORD-test","type":"point","external_reference":"gymos-%s","status":"processed","status_detail":"accredited",
				 "transactions":{"payments":[{"id":"PAY-test","status":"processed","status_detail":"accredited",
				 "amount":"123.45","paid_amount":"123.45"}]}}
				""".formatted(sale);
		String url = "https://point.test/v1/orders/" + orderId;
		var headers = Map.of("Authorization", "Bearer point-test");
		when(point.accessToken(tenant.organizationId)).thenReturn("point-test");
		when(gateway.exchange("GET", url, headers, null, null)).thenReturn(json.readTree(order),
				((ObjectNode) json.readTree(order)).put("currency", "MXN"));

		String event = "{\"action\":\"order.processed\",\"data\":{\"id\":\"ORD-test\"}}";
		String eventId = "point-delivery";
		http.perform(webhook(event, orderId, eventId, "request-first"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("POINT_CURRENCY_UNVERIFIED"));
		assertThat(operations.operation(operationId).orElseThrow().status()).isEqualTo("RECONCILIATION_REQUIRED");
		assertThat(operations.operation(operationId).orElseThrow().providerPayloadJson()).contains("created");
		assertThat(operations.webhookHistory(tenant.organizationId, 0, 10).items().getFirst().errorMessage())
				.isEqualTo("POINT_CURRENCY_UNVERIFIED");
		assertThat(operations.reconcilableOperations(OffsetDateTime.now().plusSeconds(1), OffsetDateTime.now().minusDays(1), 10))
				.extracting(PaymentProviderRepository.OperationRecord::id).contains(operationId);
		assertThat(financialStatus("payment", payment)).isEqualTo("PENDING_VALIDATION");
		assertThat(financialStatus("sale", sale)).isEqualTo("PENDING_PAYMENT");
		assertThat(paidCents(sale)).isZero();

		http.perform(webhook(event, orderId, eventId, "request-retry")).andExpect(status().isOk());
		http.perform(webhook(event, orderId, eventId, "request-duplicate")).andExpect(status().isOk());

		assertThat(operations.operation(operationId).orElseThrow().status()).isEqualTo("COMPLETED");
		assertThat(operations.webhookHistory(tenant.organizationId, 0, 10).items().getFirst().status()).isEqualTo("PROCESSED");
		assertThat(financialStatus("payment", payment)).isEqualTo("COMPLETED");
		assertThat(financialStatus("sale", sale)).isEqualTo("PAID");
		assertThat(paidCents(sale)).isEqualTo(12345);
		verify(gateway, times(2)).exchange("GET", url, headers, null, null);
	}

	@Test
	void providerStatusWithoutVerificationCannotPersistAsCompleted() {
		var tenant = new OperationalTestData(jdbc);
		UUID operationId = operations.createOperation(new CreateOperationData(tenant.organizationId, tenant.branchId,
				"STRIPE", "RECURRING_PAYMENT_INTENT", null, null, null, "unverified-status", 12345, "MXN"));
		operations.markOperationCreated(operationId, "pi_unverified", "COMPLETED", null, "{}", null);
		assertThat(operations.operation(operationId).orElseThrow().status()).isEqualTo("AWAITING_PAYMENT");

		assertThat(operations.markOperationStatus(operationId, "COMPLETED", "{}", false)).isTrue();
		assertThat(operations.operation(operationId).orElseThrow().status()).isEqualTo("AWAITING_PAYMENT");
		assertThat(operations.markOperationStatus(operationId, "COMPLETED", "{}", true)).isTrue();
		assertThat(operations.operation(operationId).orElseThrow().status()).isEqualTo("COMPLETED");
	}

	@Test
	void stripeRetryAfterExpiryStaysScopedAndLateEventsDoNotDuplicatePayment() throws Exception {
		var fixture = checkoutFixture();
		var tenant = fixture.tenant();
		UUID sale = fixture.command().saleId();
		UUID payment = fixture.command().paymentId();
		UUID client = fixture.command().clientId();
		var headers = stripeHeaders();
		UUID first = stripeOperation(tenant, sale, payment, client, "cs_expired");
		var kiosk = new SelfServiceRepository(jdbc);
		var otherTenant = new OperationalTestData(jdbc);
		stubStripeSession(fixture, "cs_expired", "expired", "unpaid", "null");

		deliverStripe("evt_expired", "checkout.session.expired", "cs_expired").andExpect(status().isOk());
		assertThat(operations.operation(first).orElseThrow().status()).isEqualTo("EXPIRED");
		assertThat(financialStatus("sale", sale)).isEqualTo("PENDING_PAYMENT");
		assertThat(financialStatus("payment", payment)).isEqualTo("PENDING_VALIDATION");
		assertThat(activatedMemberships(sale)).isZero();
		assertThat(kiosk.recoverableCheckout(tenant.organizationId, tenant.branchId, client, payment)).isPresent();
		assertThat(kiosk.recoverableCheckout(otherTenant.organizationId, otherTenant.branchId, client, payment)).isEmpty();
		assertThat(kiosk.recoverableCheckout(tenant.organizationId, otherTenant.branchId, client, payment)).isEmpty();
		assertThat(kiosk.recoverableCheckout(tenant.organizationId, tenant.branchId, tenant.client(), payment)).isEmpty();

		var retryHeaders = Map.of("Authorization", "Bearer sk_test_fixture", "Stripe-Account", "acct_fixture",
				"Idempotency-Key", "stripe-retry:" + payment + ":" + first);
		when(gateway.exchange(eq("POST"), eq("https://stripe.test/v1/checkout/sessions"), eq(retryHeaders),
				anyString(), eq("application/x-www-form-urlencoded"))).thenReturn(json.readTree("""
						{"id":"cs_paid","status":"open","url":"https://checkout.stripe.test/cs_paid"}
						"""));
		var retry = fixture.command();
		stubStripeSession(fixture, "cs_paid", "open", "unpaid", "null");
		var reopened = stripe.createCheckout(retry);
		assertThat(stripe.createCheckout(retry)).isEqualTo(reopened);
		assertThat(reopened.providerSessionId()).isEqualTo("cs_paid");
		stubStripeSession(fixture, "cs_paid", "complete", "paid", "null");
		assertThat(stripe.createCheckout(retry).status()).isEqualTo("AWAITING_PAYMENT");
		assertThat(paidCents(sale)).isZero();
		deliverStripe("evt_paid", "checkout.session.completed", "cs_paid").andExpect(status().isOk());
		deliverStripe("evt_paid", "checkout.session.completed", "cs_paid").andExpect(status().isOk());
		deliverStripe("evt_late", "checkout.session.async_payment_failed", "cs_paid").andExpect(status().isOk());
		deliverStripe("evt_previous_late", "checkout.session.expired", "cs_expired").andExpect(status().isOk());

		assertThat(operations.operation(reopened.operationId()).orElseThrow().status()).isEqualTo("COMPLETED");
		assertThat(financialStatus("payment", payment)).isEqualTo("COMPLETED");
		assertThat(financialStatus("sale", sale)).isEqualTo("PAID");
		assertThat(paidCents(sale)).isEqualTo(12345);
		assertThat(activatedMemberships(sale)).isOne();
		assertThat(kiosk.recoverableCheckout(tenant.organizationId, tenant.branchId, client, payment))
				.hasValueSatisfying(result -> assertThat(result.paymentStatus()).isEqualTo("COMPLETED"));
		assertThat(stripe.createCheckout(retry).status()).isEqualTo("COMPLETED");
		assertThat(attemptCount(payment)).isEqualTo(2);
		verify(gateway, times(5)).exchange("GET", stripeUrl("cs_paid"), headers, null, null);

		for (long amount : new long[]{1_000, 12_345}) {
			assertThatThrownBy(() -> sales.requestRefund(tenant.owner, sale,
					new RequestRefundCommand(payment, amount, "Devolución Stripe", "refund-" + amount), "refund-test"))
					.isInstanceOfSatisfying(BusinessException.class, exception ->
							assertThat(exception.code()).isEqualTo("PROVIDER_REFUND_REQUIRES_RECONCILIATION"));
		}
		assertThat(jdbc.sql("SELECT count(*) FROM sale_refund WHERE sale_id=:sale")
				.param("sale", sale).query(Long.class).single()).isZero();
	}

	@Test
	void stripeRecoversAConnectionLossWithTheSameAttemptAndKey() throws Exception {
		var fixture = checkoutFixture();
		var command = fixture.command();
		var headers = stripeHeaders(command.idempotencyKey());
		stubStripeSession(fixture, "cs_after_timeout", "open", "unpaid", "null");
		var post = when(gateway.exchange(eq("POST"), eq("https://stripe.test/v1/checkout/sessions"), eq(headers),
				anyString(), eq("application/x-www-form-urlencoded")));
		post.thenThrow(new BusinessException("PAYMENT_PROVIDER_TIMEOUT", "timeout"))
				.thenReturn(createdSession("cs_after_timeout"));
		assertThatThrownBy(() -> stripe.createCheckout(command)).isInstanceOf(BusinessException.class);
		var reserved = operations.latestOperationForPayment(command.organizationId(), "STRIPE", "CHECKOUT_SESSION", command.paymentId()).orElseThrow();
		assertThat(reserved.status()).isEqualTo("CREATING");
		var resumed = stripe.createCheckout(command);
		assertThat(resumed.operationId()).isEqualTo(reserved.id());
		assertThat(resumed.providerSessionId()).isEqualTo("cs_after_timeout");
		var body = org.mockito.ArgumentCaptor.forClass(String.class);
		verify(gateway, times(2)).exchange(eq("POST"), eq("https://stripe.test/v1/checkout/sessions"), eq(headers),
				body.capture(), eq("application/x-www-form-urlencoded"));
		assertThat(body.getAllValues()).containsOnly(body.getValue());
		assertThat(attemptCount(command.paymentId())).isOne();
	}

	@Test
	void stripeDoesNotReplayAnUnresolvedCreationOutsideTheIdempotencyWindow() throws Exception {
		var fixture = checkoutFixture();
		var command = fixture.command();
		UUID attempt = operations.createOperation(new CreateOperationData(command.organizationId(), command.branchId(),
				"STRIPE", "CHECKOUT_SESSION", command.saleId(), command.paymentId(), command.clientId(),
				command.idempotencyKey(), 12345, "MXN"));
		jdbc.sql("UPDATE payment_provider_operation SET created_at=now()-interval '24 hours' WHERE id=:id")
				.param("id", attempt).update();

		assertThat(stripe.createCheckout(command).status()).isEqualTo("AWAITING_PAYMENT");
		assertThat(attemptCount(command.paymentId())).isOne();
		org.mockito.Mockito.verifyNoInteractions(gateway);
	}

	@Test
	void stripeOnlyReplacesAFailedSessionAfterCheckingProviderExposure() throws Exception {
		var fixture = checkoutFixture();
		var command = fixture.command();
		UUID first = stripeOperation(fixture.tenant(), command.saleId(), command.paymentId(), command.clientId(), "cs_failed");
		operations.markOperationStatus(first, "FAILED", "{}", false);
		stubStripeSession(fixture, "cs_failed", "complete", "unpaid",
				"{\"status\":\"processing\",\"amount_received\":0,\"amount_capturable\":0}");
		var pending = stripe.createCheckout(command);
		assertThat(pending.status()).isEqualTo("AWAITING_PAYMENT");
		assertThat(pending.checkoutUrl()).isNull();
		assertThat(attemptCount(command.paymentId())).isOne();

		stubStripeSession(fixture, "cs_failed", "complete", "unpaid",
				"{\"status\":\"canceled\",\"amount_received\":0,\"amount_capturable\":0}");
		stubStripeSession(fixture, "cs_replacement", "open", "unpaid", "null");
		when(gateway.exchange(eq("POST"), eq("https://stripe.test/v1/checkout/sessions"),
				eq(stripeHeaders("stripe-retry:" + command.paymentId() + ":" + first)),
				anyString(), eq("application/x-www-form-urlencoded"))).thenReturn(createdSession("cs_replacement"));
		assertThat(stripe.createCheckout(command).providerSessionId()).isEqualTo("cs_replacement");
		assertThat(attemptCount(command.paymentId())).isEqualTo(2);
	}

	@Test
	void simultaneousStripeRecoveryReservesOneReplacementAndSharesItsProviderKey() throws Exception {
		var fixture = checkoutFixture();
		var command = fixture.command();
		UUID first = stripeOperation(fixture.tenant(), command.saleId(), command.paymentId(), command.clientId(), "cs_before_race");
		stubStripeSession(fixture, "cs_before_race", "expired", "unpaid", "null");
		stubStripeSession(fixture, "cs_concurrent", "open", "unpaid", "null");
		var headers = stripeHeaders("stripe-retry:" + command.paymentId() + ":" + first);
		var barrier = new CyclicBarrier(2);
		var response = createdSession("cs_concurrent");
		when(gateway.exchange(eq("POST"), eq("https://stripe.test/v1/checkout/sessions"), eq(headers),
				anyString(), eq("application/x-www-form-urlencoded"))).thenAnswer(invocation -> {
					barrier.await(15, TimeUnit.SECONDS);
					return response;
				});
		try (var threads = Executors.newFixedThreadPool(2)) {
			var a = threads.submit(() -> stripe.createCheckout(command));
			var b = threads.submit(() -> stripe.createCheckout(command));
			assertThat(a.get(20, TimeUnit.SECONDS)).isEqualTo(b.get(20, TimeUnit.SECONDS));
			assertThat(a.get().providerSessionId()).isEqualTo("cs_concurrent");
		}
		assertThat(attemptCount(command.paymentId())).isEqualTo(2);
		assertThat(paidCents(command.saleId())).isZero();
	}

	@Test
	void kioskRecoveryEndpointRequiresTheVerifiedClientBranchAndTenant() throws Exception {
		var fixture = checkoutFixture();
		var tenant = fixture.tenant();
		var command = fixture.command();
		stripeOperation(tenant, command.saleId(), command.paymentId(), command.clientId(), "cs_scope");
		stubStripeSession(fixture, "cs_scope", "open", "unpaid", "null");
		UUID otherBranch = UUID.randomUUID();
		jdbc.sql("""
				INSERT INTO branch(id, organization_id, name, code, timezone, status, created_at, updated_at)
				VALUES (:branch,:org,'Otra sede','OTHER','America/Mexico_City','ACTIVE',now(),now())
				""").param("branch", otherBranch).param("org", tenant.organizationId).update();
		var otherTenant = new OperationalTestData(jdbc);
		for (var session : java.util.List.of(
				verifiedKiosk(tenant, tenant.branchId, tenant.client()),
				verifiedKiosk(tenant, otherBranch, command.clientId()),
				verifiedKiosk(otherTenant, otherTenant.branchId, otherTenant.client()))) {
			recoverKiosk(session, command.paymentId()).andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.code").value("SELF_SERVICE_CHECKOUT_NOT_AVAILABLE"));
		}
		org.mockito.Mockito.verifyNoInteractions(gateway);
		recoverKiosk(verifiedKiosk(tenant, tenant.branchId, command.clientId()), command.paymentId())
				.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("OPEN"))
				.andExpect(jsonPath("$.providerSessionId").value("cs_scope"));
		assertThat(attemptCount(command.paymentId())).isOne();
	}

	private CreateSessionResult verifiedKiosk(OperationalTestData tenant, UUID branch, UUID client) {
		var principal = new GymOsPrincipal(tenant.ownerId, tenant.organizationId, branch, "reception", "Recepción",
				Set.of("selfservice.session.launch"), Set.of("Recepcion"), false);
		var session = selfService.launchSession(principal, "127.0.0.1", "stripe-scope-test");
		new SelfServiceRepository(jdbc).markVerified(session.sessionId(), client, session.expiresAt(), "CONTACT_BIRTH_DATE");
		return session;
	}

	private org.springframework.test.web.servlet.ResultActions recoverKiosk(CreateSessionResult session, UUID payment) throws Exception {
		return http.perform(post("/api/v1/self-service/sessions/{sessionId}/payments/{paymentId}/checkout", session.sessionId(), payment)
				.header("X-Self-Service-Session-Token", session.sessionToken()));
	}

	private CheckoutFixture checkoutFixture() {
		var tenant = new OperationalTestData(jdbc);
		UUID client = tenant.client();
		UUID plan = tenant.plan("Membresía Checkout", 30);
		UUID sale = UUID.randomUUID();
		UUID payment = UUID.randomUUID();
		jdbc.sql("""
				INSERT INTO sale(id, organization_id, branch_id, client_id, sale_number, status, business_context,
				                 subtotal_cents, total_cents, paid_cents, balance_cents, currency, sold_at, created_at, updated_at)
				VALUES (:sale,:org,:branch,:client,'STRIPE-QA','PENDING_PAYMENT','MEMBERSHIP_ENROLLMENT',
				        12345,12345,0,12345,'MXN',now(),now(),now())
				""").param("sale", sale).param("org", tenant.organizationId).param("branch", tenant.branchId)
				.param("client", client).update();
		jdbc.sql("""
				INSERT INTO sale_item(id, sale_id, organization_id, product_id, item_type, name, quantity,
				                      unit_price_cents, total_cents, created_at)
				SELECT :id,:sale,:org,product_id,'MEMBERSHIP_PLAN','Membresía Checkout',1,12345,12345,now()
				FROM membership_plan WHERE id=:plan AND organization_id=:org
				""").param("id", UUID.randomUUID()).param("sale", sale)
				.param("org", tenant.organizationId).param("plan", plan).update();
		jdbc.sql("""
				INSERT INTO payment(id, organization_id, branch_id, sale_id, client_id, method, status, amount_cents,
				                    currency, received_at, created_at, updated_at)
				VALUES (:id,:org,:branch,:sale,:client,'CARD_ONLINE','PENDING_VALIDATION',12345,'MXN',now(),now(),now())
				""").param("id", payment).param("org", tenant.organizationId).param("branch", tenant.branchId)
				.param("sale", sale).param("client", client).update();
		paymentConfiguration.providerConfiguration(tenant.organizationId);
		operations.saveStripeAccount(tenant.organizationId, tenant.ownerId, "acct_fixture", true, "{}");
		jdbc.sql("UPDATE branch SET stripe_self_checkout_enabled=true WHERE id=:id").param("id", tenant.branchId).update();
		return new CheckoutFixture(tenant, new CreateCheckoutSessionCommand(tenant.organizationId, tenant.branchId, client,
				sale, payment, 12345, "MXN", false, "Contratacion de membresia", "stripe-checkout:" + payment));
	}

	private record CheckoutFixture(OperationalTestData tenant, CreateCheckoutSessionCommand command) {}

	private Map<String, String> stripeHeaders() {
		return Map.of("Authorization", "Bearer sk_test_fixture", "Stripe-Account", "acct_fixture");
	}

	private Map<String, String> stripeHeaders(String key) {
		var headers = new java.util.HashMap<>(stripeHeaders());
		headers.put("Idempotency-Key", key);
		return headers;
	}

	private ObjectNode createdSession(String id) {
		return json.createObjectNode().put("id", id).put("status", "open").put("url", "https://checkout.stripe.test/" + id);
	}

	private void stubStripeSession(CheckoutFixture fixture, String id, String status, String paymentStatus, String intent) throws Exception {
		var body = createdSession(id).put("status", status).put("payment_status", paymentStatus)
				.put("amount_total", 12345).put("currency", "mxn").put("mode", "payment")
				.put("client_reference_id", fixture.command().saleId().toString());
		body.set("payment_intent", json.readTree(intent));
		when(gateway.exchange("GET", stripeUrl(id), stripeHeaders(), null, null)).thenReturn(body);
	}

	private long attemptCount(UUID payment) {
		return jdbc.sql("SELECT count(*) FROM payment_provider_operation WHERE payment_id=:id")
				.param("id", payment).query(Long.class).single();
	}

	private UUID stripeOperation(OperationalTestData tenant, UUID sale, UUID payment, UUID client, String session) {
		UUID operation = operations.createOperation(new CreateOperationData(tenant.organizationId, tenant.branchId,
				"STRIPE", "CHECKOUT_SESSION", sale, payment, client, session, 12345, "MXN"));
		operations.markOperationCreated(operation, session, "OPEN", "https://checkout.stripe.test/" + session, "{}", null);
		return operation;
	}

	private String stripeUrl(String session) {
		return "https://stripe.test/v1/checkout/sessions/" + session + "?expand[]=payment_intent.latest_charge";
	}

	private org.springframework.test.web.servlet.ResultActions deliverStripe(String event, String type, String session) throws Exception {
		String body = """
				{"id":"%s","type":"%s","account":"acct_fixture","data":{"object":{"id":"%s"}}}
				""".formatted(event, type, session);
		long timestamp = Instant.now().getEpochSecond();
		var mac = Mac.getInstance("HmacSHA256");
		mac.init(new SecretKeySpec("stripe-test-secret".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
		String signature = HexFormat.of().formatHex(mac.doFinal((timestamp + "." + body).getBytes(StandardCharsets.UTF_8)));
		return http.perform(post("/api/v1/payment-webhooks/stripe").header("Stripe-Signature", "t=" + timestamp + ",v1=" + signature)
				.contentType("application/json").content(body));
	}

	private String financialStatus(String table, UUID id) {
		return jdbc.sql("SELECT status FROM " + table + " WHERE id=:id").param("id", id).query(String.class).single();
	}

	private long paidCents(UUID sale) {
		return jdbc.sql("SELECT paid_cents FROM sale WHERE id=:id").param("id", sale).query(Long.class).single();
	}

	private long activatedMemberships(UUID sale) {
		return jdbc.sql("SELECT count(*) FROM membership WHERE sale_id=:sale AND status='ACTIVE'")
				.param("sale", sale).query(Long.class).single();
	}

	private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder webhook(
			String body, String order, String event, String request) throws Exception {
		long ts = Instant.now().getEpochSecond();
		var mac = Mac.getInstance("HmacSHA256");
		mac.init(new SecretKeySpec("point-test-secret".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
		String manifest = "id:" + order.toLowerCase(java.util.Locale.ROOT) + ";request-id:" + request + ";ts:" + ts + ";";
		String signature = HexFormat.of().formatHex(mac.doFinal(manifest.getBytes(StandardCharsets.UTF_8)));
		return post("/api/v1/payment-webhooks/mercado-pago").param("data.id", order).param("id", event).param("type", "order")
				.header("x-request-id", request).header("x-signature", "ts=" + ts + ",v1=" + signature)
				.contentType("application/json").content(body);
	}
}
