package com.gymos.payments.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.gymos.payments.infrastructure.PaymentProviderRepository.CreateOperationData;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Tag("postgres-integration")
@Testcontainers(disabledWithoutDocker = true)
class RecurringBillingPostgresIntegrationTests {
	@Container
	static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");

	private JdbcClient jdbcClient;
	private RecurringBillingRepository recurringRepository;
	private PaymentProviderRepository providerRepository;
	private TenantFixture tenant;

	@BeforeEach
	void setUp() {
		var dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
		Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").cleanDisabled(false).load().clean();
		Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
		jdbcClient = JdbcClient.create(dataSource);
		recurringRepository = new RecurringBillingRepository(jdbcClient);
		providerRepository = new PaymentProviderRepository(jdbcClient);
		tenant = seedTenant("PRIMARY");
	}

	@Test
	void concurrentMaterializationAndLeasingProduceOneOccurrenceAndOneProcessor() throws Exception {
		CountDownLatch start = new CountDownLatch(1);
		try (var executor = Executors.newFixedThreadPool(8)) {
			List<Future<Integer>> materializations = new ArrayList<>();
			for (int index = 0; index < 8; index++) {
				materializations.add(executor.submit(() -> {
					start.await();
					return recurringRepository.materializeDueCharges(LocalDate.now(ZoneOffset.UTC), 100);
				}));
			}
			start.countDown();
			assertThat(sum(materializations)).isEqualTo(1);
		}

		UUID chargeId = jdbcClient.sql("SELECT id FROM recurring_charge_occurrence WHERE mandate_id = :mandateId")
				.param("mandateId", tenant.mandateId()).query(UUID.class).single();
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		CountDownLatch leaseStart = new CountDownLatch(1);
		try (var executor = Executors.newFixedThreadPool(8)) {
			List<Future<Boolean>> claims = new ArrayList<>();
			for (int index = 0; index < 8; index++) {
				claims.add(executor.submit(() -> {
					leaseStart.await();
					return recurringRepository.claimCharge(chargeId, now, now.plusMinutes(5));
				}));
			}
			leaseStart.countDown();
			assertThat(trueCount(claims)).isEqualTo(1);
		}

		assertThat(recurringRepository.charge(chargeId).orElseThrow().attemptNumber()).isEqualTo(1);
		assertThat(recurringRepository.claimCharge(chargeId, now.plusMinutes(6), now.plusMinutes(11))).isTrue();
		assertThat(recurringRepository.charge(chargeId).orElseThrow().attemptNumber()).isEqualTo(2);
	}

	@Test
	void concurrentProviderCompletionAllowsOnlyOneMembershipCompletionSignal() throws Exception {
		recurringRepository.materializeDueCharges(LocalDate.now(ZoneOffset.UTC), 10);
		var charge = recurringRepository.dueCharges(OffsetDateTime.now(ZoneOffset.UTC).plusSeconds(1), 10).getFirst();
		assertThat(recurringRepository.claimCharge(
				charge.id(), OffsetDateTime.now(ZoneOffset.UTC).plusSeconds(1), OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(5)
		)).isTrue();
		UUID operationId = providerRepository.createOperation(new CreateOperationData(
				tenant.organizationId(), tenant.branchId(), "STRIPE", "RECURRING_PAYMENT_INTENT",
				null, null, tenant.clientId(), "concurrency:" + charge.id(), 50_000, "MXN",
				tenant.mandateId(), charge.id()
		));
		recurringRepository.awaitProvider(charge.id(), operationId);

		CountDownLatch start = new CountDownLatch(1);
		try (var executor = Executors.newFixedThreadPool(8)) {
			List<Future<Boolean>> completions = new ArrayList<>();
			for (int index = 0; index < 8; index++) {
				completions.add(executor.submit(() -> {
					start.await();
					return recurringRepository.completeChargeForOperation(operationId).isPresent();
				}));
			}
			start.countDown();
			assertThat(trueCount(completions)).isEqualTo(1);
		}
		assertThat(recurringRepository.charge(charge.id()).orElseThrow().status()).isEqualTo("SUCCEEDED");
	}

	@Test
	void duplicateWebhookIsClaimedOnceAndFailedDeliveryCanBeRecoveredOnce() throws Exception {
		UUID claimed;
		String eventId = "evt_concurrent_" + UUID.randomUUID();
		CountDownLatch start = new CountDownLatch(1);
		try (var executor = Executors.newFixedThreadPool(8)) {
			List<Future<UUID>> registrations = new ArrayList<>();
			for (int index = 0; index < 8; index++) {
				registrations.add(executor.submit(() -> {
					start.await();
					UUID attempt = UUID.randomUUID();
					return providerRepository.registerWebhook(
							"STRIPE", eventId, "payment_intent.succeeded", "hash", tenant.organizationId(), attempt
					) ? attempt : null;
				}));
			}
			start.countDown();
			List<UUID> claims = new ArrayList<>();
			for (Future<UUID> registration : registrations) {
				UUID claim = registration.get(10, java.util.concurrent.TimeUnit.SECONDS);
				if (claim != null) claims.add(claim);
			}
			assertThat(claims).hasSize(1);
			claimed = claims.getFirst();
		}

		providerRepository.completeWebhook("STRIPE", eventId, "FAILED", "timeout", claimed);
		assertThat(providerRepository.registerWebhook(
				"STRIPE", eventId, "payment_intent.succeeded", "hash", tenant.organizationId(), UUID.randomUUID()
		)).isTrue();
		assertThat(providerRepository.registerWebhook(
				"STRIPE", eventId, "payment_intent.succeeded", "hash", tenant.organizationId(), UUID.randomUUID()
		)).isFalse();
	}

	@Test
	void abandonedWebhookCanBeReclaimedWithoutLettingItsOldAttemptOverwriteTheResult() {
		String event = "evt_abandoned";
		UUID abandoned = UUID.randomUUID();
		UUID replacement = UUID.randomUUID();
		assertThat(providerRepository.registerWebhook("STRIPE", event, "checkout.session.completed", "hash", tenant.organizationId(), abandoned)).isTrue();
		jdbcClient.sql("UPDATE payment_provider_webhook_event SET processing_started_at = now() - interval '6 minutes' WHERE external_event_id = :id")
				.param("id", event).update();
		assertThat(providerRepository.registerWebhook("STRIPE", event, "checkout.session.completed", "changed", tenant.organizationId(), replacement)).isFalse();
		assertThat(providerRepository.registerWebhook("STRIPE", event, "checkout.session.completed", "hash", tenant.organizationId(), replacement)).isTrue();
		providerRepository.completeWebhook("STRIPE", event, "PROCESSED", null, replacement);
		providerRepository.completeWebhook("STRIPE", event, "FAILED", "abandoned", abandoned);
		assertThat(providerRepository.webhookHistory(tenant.organizationId(), 0, 10).items().getFirst().status()).isEqualTo("PROCESSED");
	}

	@Test
	void paginatedHistoriesAreStableAndNeverLeakAnotherTenant() {
		TenantFixture otherTenant = seedTenant("OTHER");
		recurringRepository.materializeDueCharges(LocalDate.now(ZoneOffset.UTC), 100);
		for (int index = 0; index < 3; index++) {
			providerRepository.registerWebhook(
					"STRIPE", "evt_primary_" + index, "payment_intent.succeeded", "hash" + index,
					tenant.organizationId(), UUID.randomUUID()
			);
		}
		providerRepository.registerWebhook(
				"STRIPE", "evt_other", "payment_intent.succeeded", "other", otherTenant.organizationId(), UUID.randomUUID()
		);

		var firstWebhooks = providerRepository.webhookHistory(tenant.organizationId(), 0, 2);
		var secondWebhooks = providerRepository.webhookHistory(tenant.organizationId(), 1, 2);
		assertThat(firstWebhooks.totalElements()).isEqualTo(3);
		assertThat(firstWebhooks.items()).hasSize(2);
		assertThat(secondWebhooks.items()).hasSize(1);
		assertThat(firstWebhooks.items()).extracting(PaymentProviderRepository.WebhookHistoryRecord::externalEventId)
				.doesNotContain("evt_other");

		assertThat(recurringRepository.mandateHistory(tenant.organizationId(), 0, 10).totalElements()).isEqualTo(1);
		assertThat(recurringRepository.chargeHistory(tenant.organizationId(), 0, 10).totalElements()).isEqualTo(1);
		assertThat(recurringRepository.chargeHistory(otherTenant.organizationId(), 0, 10).totalElements()).isEqualTo(1);
	}

	private TenantFixture seedTenant(String code) {
		UUID organizationId = UUID.randomUUID();
		UUID branchId = UUID.randomUUID();
		UUID personId = UUID.randomUUID();
		UUID clientId = UUID.randomUUID();
		UUID productId = UUID.randomUUID();
		UUID planId = UUID.randomUUID();
		UUID saleId = UUID.randomUUID();
		UUID membershipId = UUID.randomUUID();
		UUID customerId = UUID.randomUUID();
		UUID methodId = UUID.randomUUID();
		UUID mandateId = UUID.randomUUID();
		jdbcClient.sql("""
				INSERT INTO organization (id, legal_name, trade_name, status, timezone, default_currency, created_at, updated_at, version)
				VALUES (:id, :name, :name, 'ACTIVE', 'America/Mexico_City', 'MXN', now(), now(), 0)
				""").param("id", organizationId).param("name", "Gym " + code).update();
		jdbcClient.sql("""
				INSERT INTO branch (
				 id, organization_id, name, code, timezone, status,
				 stripe_self_checkout_enabled, stripe_recurring_enabled,
				 created_at, updated_at, version)
				VALUES (
				 :id, :organizationId, :name, :code, 'America/Mexico_City', 'ACTIVE',
				 true, true, now(), now(), 0)
				""").param("id", branchId).param("organizationId", organizationId)
				.param("name", "Branch " + code).param("code", code).update();
		jdbcClient.sql("""
				INSERT INTO person (id, organization_id, first_name, last_name, status, created_at, updated_at, version)
				VALUES (:id, :organizationId, 'Client', :code, 'ACTIVE', now(), now(), 0)
				""").param("id", personId).param("organizationId", organizationId).param("code", code).update();
		jdbcClient.sql("""
				INSERT INTO client (id, organization_id, person_id, home_branch_id, status, joined_at, created_at, updated_at, version)
				VALUES (:id, :organizationId, :personId, :branchId, 'ACTIVE', now(), now(), now(), 0)
				""").param("id", clientId).param("organizationId", organizationId)
				.param("personId", personId).param("branchId", branchId).update();
		jdbcClient.sql("""
				INSERT INTO catalog_product (
				 id, organization_id, sku, name, product_type, commercial_subtype,
				 price_cents, currency, taxable, status, created_at, updated_at, version)
				VALUES (:id, :organizationId, :sku, :name, 'MEMBERSHIP_PLAN', 'MEMBERSHIP',
				 50000, 'MXN', false, 'ACTIVE', now(), now(), 0)
				""").param("id", productId).param("organizationId", organizationId)
				.param("sku", "PLAN-" + code).param("name", "Plan " + code).update();
		jdbcClient.sql("""
				INSERT INTO catalog_product_branch (organization_id, branch_id, product_id, created_at)
				VALUES (:organizationId, :branchId, :productId, now())
				""").param("organizationId", organizationId).param("branchId", branchId)
				.param("productId", productId).update();
		jdbcClient.sql("""
				INSERT INTO membership_plan (
				 id, organization_id, product_id, duration_days, grace_days, multi_branch_access,
				 status, created_at, updated_at, version)
				VALUES (:id, :organizationId, :productId, 30, 3, false, 'ACTIVE', now(), now(), 0)
				""").param("id", planId).param("organizationId", organizationId)
				.param("productId", productId).update();
		jdbcClient.sql("""
				INSERT INTO sale (
				 id, organization_id, branch_id, client_id, sale_number, status,
				 subtotal_cents, discount_cents, total_cents, paid_cents, balance_cents,
				 currency, sold_at, created_at, updated_at, version, business_context)
				VALUES (:id, :organizationId, :branchId, :clientId, :number, 'PAID',
				 50000, 0, 50000, 50000, 0, 'MXN', now(), now(), now(), 0, 'MEMBERSHIP_ENROLLMENT')
				""").param("id", saleId).param("organizationId", organizationId)
				.param("branchId", branchId).param("clientId", clientId)
				.param("number", "SALE-" + code).update();
		jdbcClient.sql("""
				INSERT INTO membership (
				 id, organization_id, branch_id, client_id, plan_id, sale_id, status,
				 activated_at, starts_on, ends_on, grace_ends_on, created_at, updated_at, version)
				VALUES (:id, :organizationId, :branchId, :clientId, :planId, :saleId, 'ACTIVE',
				 now(), current_date - 30, current_date, current_date + 3, now(), now(), 0)
				""").param("id", membershipId).param("organizationId", organizationId)
				.param("branchId", branchId).param("clientId", clientId).param("planId", planId)
				.param("saleId", saleId).update();
		jdbcClient.sql("""
				INSERT INTO organization_payment_configuration (
				 organization_id, reception_manual_terminal_enabled, reception_mercado_pago_enabled,
				 stripe_self_checkout_enabled, stripe_recurring_enabled, stripe_account_id,
				 stripe_connected_at, created_at, updated_at, version)
				VALUES (:organizationId, true, false, true, true, 'acct_test', now(), now(), now(), 0)
				""").param("organizationId", organizationId).update();
		jdbcClient.sql("""
				INSERT INTO payment_customer (
				 id, organization_id, client_id, provider, external_customer_id, created_at, updated_at, version)
				VALUES (:id, :organizationId, :clientId, 'STRIPE', :externalId, now(), now(), 0)
				""").param("id", customerId).param("organizationId", organizationId)
				.param("clientId", clientId).param("externalId", "cus_" + code).update();
		jdbcClient.sql("""
				INSERT INTO stored_payment_method (
				 id, organization_id, client_id, payment_customer_id, provider,
				 external_payment_method_id, method_type, brand, last_four,
				 expires_month, expires_year, status, created_at, updated_at, version)
				VALUES (:id, :organizationId, :clientId, :customerId, 'STRIPE',
				 :externalId, 'card', 'visa', '4242', 12, 2035, 'ACTIVE', now(), now(), 0)
				""").param("id", methodId).param("organizationId", organizationId)
				.param("clientId", clientId).param("customerId", customerId)
				.param("externalId", "pm_" + code).update();
		jdbcClient.sql("""
				INSERT INTO recurring_payment_mandate (
				 id, organization_id, branch_id, client_id, membership_id, plan_id,
				 payment_customer_id, stored_payment_method_id, provider, status,
				 terms_version, consent_text, amount_rule, consented_at, consent_origin,
				 idempotency_key, next_charge_on, created_at, updated_at, version)
				VALUES (:id, :organizationId, :branchId, :clientId, :membershipId, :planId,
				 :customerId, :methodId, 'STRIPE', 'ACTIVE', '2026-08-03', 'Consent',
				 'CURRENT_MEMBERSHIP_RENEWAL_TOTAL', now(), 'TEST', :key, current_date, now(), now(), 0)
				""").param("id", mandateId).param("organizationId", organizationId)
				.param("branchId", branchId).param("clientId", clientId)
				.param("membershipId", membershipId).param("planId", planId)
				.param("customerId", customerId).param("methodId", methodId)
				.param("key", "mandate-" + code).update();
		return new TenantFixture(organizationId, branchId, clientId, membershipId, mandateId);
	}

	private int sum(List<Future<Integer>> futures) throws Exception {
		int total = 0;
		for (Future<Integer> future : futures) total += future.get();
		return total;
	}

	private int trueCount(List<Future<Boolean>> futures) throws Exception {
		int total = 0;
		for (Future<Boolean> future : futures) if (future.get()) total++;
		return total;
	}

	private record TenantFixture(
			UUID organizationId, UUID branchId, UUID clientId, UUID membershipId, UUID mandateId
	) {}
}
