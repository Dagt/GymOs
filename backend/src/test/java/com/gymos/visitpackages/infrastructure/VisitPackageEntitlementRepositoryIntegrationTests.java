package com.gymos.visitpackages.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gymos.common.api.BusinessException;
import com.gymos.access.infrastructure.AccessRepository;
import com.gymos.benefits.infrastructure.BenefitRepository;
import com.gymos.catalog.infrastructure.VisitPackagePlanRepository;
import com.gymos.visitpackages.api.operations.VisitPackageAccessOperations.VisitPackageAccessResult;
import com.gymos.visitpackages.application.port.VisitPackageEntitlementPort.BenefitCoverage;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Tag("postgres-integration")
@Testcontainers(disabledWithoutDocker = true)
class VisitPackageEntitlementRepositoryIntegrationTests {
	@Container
	static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");

	@BeforeAll
	static void migrate() {
		Flyway.configure()
				.dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
				.locations("classpath:db/migration").load().migrate();
	}

	@Test
	void localBenefitInTwoBranchPackageConsumesOnlyAtItsOwnBranch() {
		Fixture fixture = fixture(2);
		UUID sauna = fixture.insertAccessBenefit(fixture.branchA(), "SAUNA", "LOCAL");
		fixture.activate(List.of(new BenefitCoverage(sauna, List.of(fixture.branchA()))));

		assertThat(fixture.consume(
				fixture.branchB(), fixture.clientId(), List.of(sauna), fixture.deviceB(), "sauna-b")).isEmpty();
		assertThat(fixture.remaining()).isEqualTo(2);
		assertThat(fixture.consume(
				fixture.branchA(), fixture.clientId(), List.of(sauna), fixture.deviceA(), "sauna-a"))
				.get().extracting(VisitPackageAccessResult::remainingAfter).isEqualTo(1);
	}

	@Test
	void benefitCoverageUsesBenefitScopeRatherThanExpandingToPackageBranches() {
		Fixture fixture = fixture(2);
		UUID localSauna = fixture.insertAccessBenefit(fixture.branchA(), "SAUNA", "LOCAL");
		UUID planCoverage = fixture.insertAccessBenefit(fixture.branchA(), "GENERAL", "PLAN_COVERAGE");
		UUID membershipProductId = UUID.randomUUID();
		UUID membershipPlanId = UUID.randomUUID();
		fixture.jdbc().sql("""
				INSERT INTO catalog_product (id, organization_id, sku, name, product_type, commercial_subtype,
				 price_cents, currency, taxable, status, created_at, updated_at, version)
				VALUES (:id, :organizationId, :sku, 'VIP', 'MEMBERSHIP_PLAN', 'MEMBERSHIP', 1000,
				 'MXN', TRUE, 'ACTIVE', now(), now(), 0)
				""").param("id", membershipProductId).param("organizationId", fixture.organizationId())
				.param("sku", "VIP-" + membershipProductId).update();
		fixture.jdbc().sql("""
				INSERT INTO membership_plan (id, organization_id, product_id, duration_days, grace_days,
				 benefits_json, multi_branch_access, self_service_acquisition_enabled,
				 registration_fee_policy, status, created_at, updated_at, version)
				VALUES (:id, :organizationId, :productId, 30, 0, '{}'::jsonb, TRUE, TRUE,
				 'DEFAULT', 'ACTIVE', now(), now(), 0)
				""").param("id", membershipPlanId).param("organizationId", fixture.organizationId())
				.param("productId", membershipProductId).update();
		fixture.jdbc().sql("""
				INSERT INTO benefit_membership_plan (benefit_id, membership_plan_id, organization_id, created_at)
				VALUES (:benefitId, :planId, :organizationId, now())
				""").param("benefitId", planCoverage).param("planId", membershipPlanId)
				.param("organizationId", fixture.organizationId()).update();
		var plans = new VisitPackagePlanRepository(fixture.jdbc());

		assertThat(plans.benefitCoverages(
				fixture.organizationId(), List.of(localSauna), List.of(fixture.branchA(), fixture.branchB())))
				.singleElement().satisfies(coverage -> assertThat(coverage.branchIds())
						.containsExactly(fixture.branchA()));
		assertThat(plans.benefitCoverages(
				fixture.organizationId(), List.of(planCoverage), List.of(fixture.branchA(), fixture.branchB())))
				.singleElement().satisfies(coverage -> assertThat(coverage.branchIds())
						.containsExactlyInAnyOrder(fixture.branchA(), fixture.branchB()));
		var access = new AccessRepository(fixture.jdbc());
		assertThat(access.physicalAccessBenefits(fixture.organizationId(), fixture.branchA()))
				.extracting(record -> record.id()).contains(localSauna, planCoverage);
		assertThat(access.physicalAccessBenefits(fixture.organizationId(), fixture.branchB()))
				.extracting(record -> record.id()).contains(planCoverage).doesNotContain(localSauna);
		var benefits = new BenefitRepository(fixture.jdbc());
		assertThat(benefits.benefit(fixture.organizationId(), localSauna)).get()
				.satisfies(benefit -> assertThat(benefit.accessBranchIds())
						.containsExactly(fixture.branchA()));
		assertThat(benefits.benefit(fixture.organizationId(), planCoverage)).get()
				.satisfies(benefit -> assertThat(benefit.accessBranchIds())
						.containsExactlyInAnyOrder(fixture.branchA(), fixture.branchB()));

		fixture.jdbc().sql("UPDATE membership_plan SET multi_branch_access=FALSE WHERE id=:id")
				.param("id", membershipPlanId).update();
		fixture.jdbc().sql("""
				INSERT INTO catalog_product_branch (product_id, branch_id, organization_id, created_at)
				VALUES (:productId, :branchId, :organizationId, now())
				""").param("productId", membershipProductId).param("branchId", fixture.branchA())
				.param("organizationId", fixture.organizationId()).update();
		assertThat(plans.benefitCoverages(
				fixture.organizationId(), List.of(planCoverage), List.of(fixture.branchA(), fixture.branchB())))
				.singleElement().satisfies(coverage -> assertThat(coverage.branchIds())
						.containsExactly(fixture.branchA()));

		assertThat(access.physicalAccessBenefits(fixture.organizationId(), fixture.branchA()))
				.extracting(record -> record.id()).contains(localSauna, planCoverage);
		assertThat(access.physicalAccessBenefits(fixture.organizationId(), fixture.branchB()))
				.extracting(record -> record.id()).doesNotContain(localSauna, planCoverage);
	}

	@Test
	void oneRemainingVisitAllowsExactlyOneOfTwoConcurrentRequests() throws Exception {
		Fixture fixture = fixture(1);
		fixture.activate(List.of());
		var barrier = new CyclicBarrier(2);
		var executor = Executors.newFixedThreadPool(2);
		try {
			var first = executor.submit(() -> {
				barrier.await();
				return fixture.consume(fixture.branchA(), fixture.clientId(), List.of(), fixture.deviceA(), "race-1");
			});
			var second = executor.submit(() -> {
				barrier.await();
				return fixture.consume(fixture.branchA(), fixture.clientId(), List.of(), fixture.deviceA(), "race-2");
			});
			assertThat(List.of(first.get(), second.get()).stream().filter(Optional::isPresent).count()).isEqualTo(1);
			assertThat(fixture.remaining()).isZero();
			assertThat(fixture.consumptionCount()).isEqualTo(1);
		} finally {
			executor.shutdownNow();
		}
	}

	@Test
	void tenVisitsAcceptTenOfElevenConcurrentUniqueRequestsWithoutGoingNegative() throws Exception {
		Fixture fixture = fixture(10);
		fixture.activate(List.of());
		var barrier = new CyclicBarrier(11);
		var executor = Executors.newFixedThreadPool(11);
		try {
			List<java.util.concurrent.Future<Optional<VisitPackageAccessResult>>> futures = new ArrayList<>();
			for (int index = 0; index < 11; index++) {
				int request = index;
				futures.add(executor.submit(() -> {
					barrier.await();
					return fixture.consume(
							fixture.branchA(), fixture.clientId(), List.of(), fixture.deviceA(), "burst-" + request);
				}));
			}
			long accepted = 0;
			for (var future : futures) if (future.get().isPresent()) accepted++;
			assertThat(accepted).isEqualTo(10);
			assertThat(fixture.remaining()).isZero();
			assertThat(fixture.consumptionCount()).isEqualTo(10);
		} finally {
			executor.shutdownNow();
		}
	}

	@Test
	void idempotencyRetryDoesNotConsumeTwiceAndRejectsChangedContext() {
		Fixture fixture = fixture(3);
		fixture.activate(List.of());
		var first = fixture.consume(
				fixture.branchA(), fixture.clientId(), List.of(), fixture.deviceA(), "same-event").orElseThrow();
		var retry = fixture.consume(
				fixture.branchA(), fixture.clientId(), List.of(), fixture.deviceA(), "same-event").orElseThrow();

		assertThat(retry).isEqualTo(first);
		assertThat(fixture.remaining()).isEqualTo(2);
		assertThatThrownBy(() -> fixture.consume(
				fixture.branchB(), fixture.clientId(), List.of(), fixture.deviceB(), "same-event"))
				.isInstanceOfSatisfying(BusinessException.class,
						error -> assertThat(error.code()).isEqualTo("VISIT_PACKAGE_ACCESS_KEY_REUSED"));
	}

	@Test
	void twoBranchesShareOneBalanceAndHistoryKeepsBranchAndDevice() {
		Fixture fixture = fixture(3);
		fixture.activate(List.of());
		fixture.consume(fixture.branchA(), fixture.clientId(), List.of(), fixture.deviceA(), "shared-a");
		fixture.consume(fixture.branchB(), fixture.clientId(), List.of(), fixture.deviceB(), "shared-b");

		assertThat(fixture.remaining()).isEqualTo(1);
		var history = fixture.repository().history(fixture.organizationId(), fixture.clientId(), 20);
		assertThat(history).singleElement().satisfies(entitlement -> {
			assertThat(entitlement.branchIds()).containsExactlyInAnyOrder(fixture.branchA(), fixture.branchB());
			assertThat(entitlement.consumptions()).hasSize(2);
			assertThat(entitlement.consumptions()).extracting(consumption -> consumption.branchId())
					.containsExactlyInAnyOrder(fixture.branchA(), fixture.branchB());
			assertThat(entitlement.consumptions()).extracting(consumption -> consumption.deviceName())
					.containsExactlyInAnyOrder("Entrada A", "Entrada B");
		});
	}

	private Fixture fixture(int totalVisits) {
		var dataSource = new DriverManagerDataSource(
				postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
		var jdbc = JdbcClient.create(dataSource);
		UUID organizationId = UUID.randomUUID();
		UUID branchA = UUID.randomUUID();
		UUID branchB = UUID.randomUUID();
		UUID clientId = UUID.randomUUID();
		UUID packageProductId = UUID.randomUUID();
		UUID packagePlanId = UUID.randomUUID();
		UUID saleId = UUID.randomUUID();
		UUID deviceA = UUID.randomUUID();
		UUID deviceB = UUID.randomUUID();
		jdbc.sql("""
				INSERT INTO organization (id, legal_name, trade_name, status, timezone, default_currency, created_at, updated_at, version)
				VALUES (:id, 'Visit Gym', 'Visit Gym', 'ACTIVE', 'America/Mexico_City', 'MXN', now(), now(), 0)
				""").param("id", organizationId).update();
		insertBranch(jdbc, organizationId, branchA, "Centro", "A-" + branchA);
		insertBranch(jdbc, organizationId, branchB, "Tecnológica", "B-" + branchB);
		UUID personId = UUID.randomUUID();
		jdbc.sql("""
				INSERT INTO person (id, organization_id, first_name, last_name, email, phone, status, created_at, updated_at, version)
				VALUES (:id, :organizationId, 'Paola', 'Visitas', :email, :phone, 'ACTIVE', now(), now(), 0)
				""").param("id", personId).param("organizationId", organizationId)
				.param("email", personId + "@visit.test").param("phone", "+52" + Math.abs(personId.hashCode())).update();
		jdbc.sql("""
				INSERT INTO client (id, organization_id, person_id, home_branch_id, status, joined_at, created_at, updated_at, version)
				VALUES (:id, :organizationId, :personId, :branchId, 'ACTIVE', now(), now(), now(), 0)
				""").param("id", clientId).param("organizationId", organizationId)
				.param("personId", personId).param("branchId", branchA).update();
		insertProduct(jdbc, organizationId, packageProductId, "VP-" + packageProductId, "Paquete");
		jdbc.sql("""
				INSERT INTO visit_package_plan (id, organization_id, product_id, total_visits, validity_days,
				 branch_scope, self_service_acquisition_enabled, registration_fee_policy, status, created_at, updated_at, version)
				VALUES (:id, :organizationId, :productId, :visits, 30, 'ALL_BRANCHES', TRUE, 'DEFAULT', 'ACTIVE', now(), now(), 0)
				""").param("id", packagePlanId).param("organizationId", organizationId)
				.param("productId", packageProductId).param("visits", totalVisits).update();
		jdbc.sql("""
				INSERT INTO sale (id, organization_id, branch_id, client_id, sale_number, status, subtotal_cents,
				 discount_cents, total_cents, paid_cents, balance_cents, currency, sold_at, created_at, updated_at,
				 version, idempotency_key)
				VALUES (:id, :organizationId, :branchId, :clientId, :number, 'PAID', 1000, 0, 1000, 1000, 0,
				 'MXN', now(), now(), now(), 0, :key)
				""").param("id", saleId).param("organizationId", organizationId).param("branchId", branchA)
				.param("clientId", clientId).param("number", "VP-" + saleId).param("key", "sale-" + saleId).update();
		insertDevice(jdbc, organizationId, branchA, deviceA, "Entrada A");
		insertDevice(jdbc, organizationId, branchB, deviceB, "Entrada B");
		return new Fixture(
				jdbc, new VisitPackageEntitlementRepository(jdbc),
				new TransactionTemplate(new DataSourceTransactionManager(dataSource)), organizationId,
				branchA, branchB, clientId, packagePlanId, saleId, deviceA, deviceB, totalVisits);
	}

	private static void insertBranch(JdbcClient jdbc, UUID organizationId, UUID branchId, String name, String code) {
		jdbc.sql("""
				INSERT INTO branch (id, organization_id, name, code, timezone, status, created_at, updated_at, version)
				VALUES (:id, :organizationId, :name, :code, 'America/Mexico_City', 'ACTIVE', now(), now(), 0)
				""").param("id", branchId).param("organizationId", organizationId)
				.param("name", name).param("code", code.substring(0, Math.min(code.length(), 40))).update();
	}

	private static void insertProduct(JdbcClient jdbc, UUID organizationId, UUID id, String sku, String name) {
		jdbc.sql("""
				INSERT INTO catalog_product (id, organization_id, sku, name, product_type, commercial_subtype,
				 price_cents, currency, taxable, status, created_at, updated_at, version)
				VALUES (:id, :organizationId, :sku, :name, 'SERVICE', 'VISIT_PASS', 1000, 'MXN', TRUE,
				 'ACTIVE', now(), now(), 0)
				""").param("id", id).param("organizationId", organizationId)
				.param("sku", sku.substring(0, Math.min(sku.length(), 60))).param("name", name).update();
	}

	private static void insertDevice(
			JdbcClient jdbc, UUID organizationId, UUID branchId, UUID id, String name
	) {
		jdbc.sql("""
				INSERT INTO access_device (id, organization_id, branch_id, name, device_code, device_type,
				 status, provider, created_at, updated_at, version)
				VALUES (:id, :organizationId, :branchId, :name, :code, 'TURNSTILE', 'ACTIVE',
				 'SIMULATED', now(), now(), 0)
				""").param("id", id).param("organizationId", organizationId).param("branchId", branchId)
				.param("name", name).param("code", "D-" + id).update();
	}

	private record Fixture(
			JdbcClient jdbc, VisitPackageEntitlementRepository repository, TransactionTemplate transaction,
			UUID organizationId, UUID branchA, UUID branchB, UUID clientId, UUID packagePlanId,
			UUID saleId, UUID deviceA, UUID deviceB, int totalVisits
	) {
		UUID insertAccessBenefit(UUID branchId, String code, String scope) {
			UUID benefitId = UUID.randomUUID();
			jdbc.sql("""
					INSERT INTO benefit (id, organization_id, branch_id, code, name, benefit_type, discount_type,
					 requires_evidence, combinable, priority, status, access_scope, created_at, updated_at, version)
					VALUES (:id, :organizationId, :branchId, :code, :code, 'ACCESS', 'NONE', FALSE, FALSE,
					 100, 'ACTIVE', :scope, now(), now(), 0)
					""").param("id", benefitId).param("organizationId", organizationId)
					.param("branchId", branchId).param("code", code + "-" + benefitId)
					.param("scope", scope).update();
			return benefitId;
		}

		void activate(List<BenefitCoverage> benefits) {
			transaction.executeWithoutResult(status -> {
				repository.createPending(
						organizationId, branchA, clientId, packagePlanId, saleId, "Paquete", totalVisits, 30,
						OffsetDateTime.now().plusMinutes(30), List.of(branchA, branchB), benefits, OffsetDateTime.now());
				repository.activateBySale(organizationId, saleId, LocalDate.now(), OffsetDateTime.now());
			});
		}

		Optional<VisitPackageAccessResult> consume(
				UUID branchId, UUID targetClientId, List<UUID> benefits, UUID deviceId, String key
		) {
			return transaction.execute(status -> repository.consumeForAccess(
					organizationId, branchId, targetClientId, benefits, deviceId, key,
					LocalDate.now(), OffsetDateTime.now()));
		}

		int remaining() {
			return jdbc.sql("SELECT remaining_visits FROM visit_package_entitlement WHERE sale_id=:saleId")
					.param("saleId", saleId).query(Integer.class).single();
		}

		int consumptionCount() {
			return jdbc.sql("SELECT COUNT(*) FROM visit_package_consumption WHERE organization_id=:organizationId")
					.param("organizationId", organizationId).query(Integer.class).single();
		}
	}
}
