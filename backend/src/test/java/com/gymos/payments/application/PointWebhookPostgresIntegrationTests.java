package com.gymos.payments.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gymos.payments.application.port.ProviderHttpGateway;
import com.gymos.payments.infrastructure.PaymentProviderRepository;
import com.gymos.payments.infrastructure.PaymentProviderRepository.CreateOperationData;
import com.gymos.support.OperationalTestData;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
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
		UUID operationId = operations.createOperation(new CreateOperationData(tenant.organizationId(), tenant.branchId,
				"STRIPE", "RECURRING_PAYMENT_INTENT", null, null, null, "unverified-status", 12345, "MXN"));
		operations.markOperationCreated(operationId, "pi_unverified", "COMPLETED", null, "{}", null);
		assertThat(operations.operation(operationId).orElseThrow().status()).isEqualTo("AWAITING_PAYMENT");

		assertThat(operations.markOperationStatus(operationId, "COMPLETED", "{}", false)).isTrue();
		assertThat(operations.operation(operationId).orElseThrow().status()).isEqualTo("AWAITING_PAYMENT");
		assertThat(operations.markOperationStatus(operationId, "COMPLETED", "{}", true)).isTrue();
		assertThat(operations.operation(operationId).orElseThrow().status()).isEqualTo("COMPLETED");
	}

	private String financialStatus(String table, UUID id) {
		return jdbc.sql("SELECT status FROM " + table + " WHERE id=:id").param("id", id).query(String.class).single();
	}

	private long paidCents(UUID sale) {
		return jdbc.sql("SELECT paid_cents FROM sale WHERE id=:id").param("id", sale).query(Long.class).single();
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
