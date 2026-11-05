package com.gymos.visitpackages.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gymos.common.api.BusinessException;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.access.application.ManualCheckInService;
import com.gymos.access.infrastructure.ManualCheckInRepository;
import com.gymos.access.infrastructure.ManualCheckInRepository.Entry;
import com.gymos.audit.api.AuditService;
import com.gymos.eligibility.application.DefaultEligibilityPolicy;
import com.gymos.eligibility.infrastructure.EligibilityRepository;
import com.gymos.visitpackages.application.VisitPackageAccessService;
import com.gymos.access.infrastructure.AccessRepository;
import com.gymos.benefits.infrastructure.BenefitRepository;
import com.gymos.catalog.api.VisitPackageBenefitUsage;
import com.gymos.catalog.api.VisitPackageBenefitUsageMode;
import com.gymos.catalog.infrastructure.VisitPackagePlanRepository;
import com.gymos.visitpackages.api.operations.VisitPackageAccessOperations.VisitPackageAccessResult;
import com.gymos.visitpackages.application.port.VisitPackageEntitlementPort.BenefitCoverage;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.springframework.security.access.AccessDeniedException;
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
	private static final LocalDate TEST_BUSINESS_DATE = LocalDate.of(2026, 8, 28);
	private static final OffsetDateTime TEST_OCCURRED_AT = OffsetDateTime.parse("2026-08-28T14:00:00Z");

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
		fixture.activate(List.of(new BenefitCoverage(
				sauna, VisitPackageBenefitUsageMode.CONSUMES_VISIT, List.of(fixture.branchA()))));

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
				fixture.organizationId(), List.of(consumes(localSauna)),
				List.of(fixture.branchA(), fixture.branchB())))
				.singleElement().satisfies(coverage -> assertThat(coverage.branchIds())
						.containsExactly(fixture.branchA()));
		assertThat(plans.benefitCoverages(
				fixture.organizationId(), List.of(consumes(planCoverage)),
				List.of(fixture.branchA(), fixture.branchB())))
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
				fixture.organizationId(), List.of(consumes(planCoverage)),
				List.of(fixture.branchA(), fixture.branchB())))
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
		UUID general = fixture.activateConsuming(List.of(fixture.branchA()));
		var barrier = new CyclicBarrier(2);
		var executor = Executors.newFixedThreadPool(2);
		try {
			var first = executor.submit(() -> {
				barrier.await();
				return fixture.consume(
						fixture.branchA(), fixture.clientId(), List.of(general), fixture.deviceA(), "race-1");
			});
			var second = executor.submit(() -> {
				barrier.await();
				return fixture.consume(
						fixture.branchA(), fixture.clientId(), List.of(general), fixture.deviceA(), "race-2");
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
		UUID general = fixture.activateConsuming(List.of(fixture.branchA()));
		var barrier = new CyclicBarrier(11);
		var executor = Executors.newFixedThreadPool(11);
		try {
			List<java.util.concurrent.Future<Optional<VisitPackageAccessResult>>> futures = new ArrayList<>();
			for (int index = 0; index < 11; index++) {
				int request = index;
				futures.add(executor.submit(() -> {
					barrier.await();
					return fixture.consume(
							fixture.branchA(), fixture.clientId(), List.of(general), fixture.deviceA(), "burst-" + request);
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
		UUID general = fixture.activateConsuming(List.of(fixture.branchA()));
		var first = fixture.consume(
				fixture.branchA(), fixture.clientId(), List.of(general), fixture.deviceA(), "same-event").orElseThrow();
		var retry = fixture.consume(
				fixture.branchA(), fixture.clientId(), List.of(general), fixture.deviceA(), "same-event").orElseThrow();

		assertThat(retry).isEqualTo(first);
		assertThat(fixture.remaining()).isEqualTo(2);
		assertThatThrownBy(() -> fixture.consume(
				fixture.branchB(), fixture.clientId(), List.of(general), fixture.deviceB(), "same-event"))
				.isInstanceOfSatisfying(BusinessException.class,
						error -> assertThat(error.code()).isEqualTo("VISIT_PACKAGE_ACCESS_KEY_REUSED"));
	}

	@Test
	void twoBranchesShareOneBalanceAndHistoryKeepsBranchAndDevice() {
		Fixture fixture = fixture(3);
		UUID general = fixture.activateConsuming(List.of(fixture.branchA(), fixture.branchB()));
		fixture.consume(fixture.branchA(), fixture.clientId(), List.of(general), fixture.deviceA(), "shared-a");
		fixture.consume(fixture.branchB(), fixture.clientId(), List.of(general), fixture.deviceB(), "shared-b");

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

	@Test
	void includedAccessRequiresABaseVisitAndNeverDecrementsAgain() {
		Fixture fixture = fixture(4);
		UUID general = fixture.insertAccessBenefit(fixture.branchA(), "GENERAL", "PLAN_COVERAGE");
		UUID sauna = fixture.insertAccessBenefit(fixture.branchA(), "SAUNA", "LOCAL");
		fixture.activate(List.of(
				new BenefitCoverage(
						general, VisitPackageBenefitUsageMode.CONSUMES_VISIT,
						List.of(fixture.branchA(), fixture.branchB())),
				new BenefitCoverage(
						sauna, VisitPackageBenefitUsageMode.INCLUDED_WITH_VISIT,
						List.of(fixture.branchA()))));
		var entitlement = fixture.repository().open(fixture.organizationId(), fixture.clientId()).orElseThrow();
		assertThat(entitlement.benefits()).hasSize(2);
		assertThat(entitlement.benefits()).anySatisfy(benefit -> {
			assertThat(benefit.benefitId()).isEqualTo(general);
			assertThat(benefit.usageMode()).isEqualTo("CONSUMES_VISIT");
			assertThat(benefit.branchIds()).containsExactlyInAnyOrder(
					fixture.branchA(), fixture.branchB());
		});
		assertThat(entitlement.benefits()).anySatisfy(benefit -> {
			assertThat(benefit.benefitId()).isEqualTo(sauna);
			assertThat(benefit.usageMode()).isEqualTo("INCLUDED_WITH_VISIT");
			assertThat(benefit.branchIds()).containsExactly(fixture.branchA());
		});
		LocalDate date = LocalDate.of(2026, 8, 28);
		OffsetDateTime start = OffsetDateTime.parse("2026-08-28T14:00:00Z");

		VisitPackageAccessResult deniedBeforeBase = fixture.authorize(
				fixture.branchA(), fixture.clientId(), sauna, fixture.deviceA(), "sauna-before",
				date, start).orElseThrow();
		assertThat(deniedBeforeBase.granted()).isFalse();
		assertThat(deniedBeforeBase.baseConsumptionId()).isNull();
		VisitPackageAccessResult base = fixture.authorize(
				fixture.branchA(), fixture.clientId(), general, fixture.deviceA(), "general-centro",
				date, start.plusMinutes(1)).orElseThrow();
		VisitPackageAccessResult included = fixture.authorize(
				fixture.branchA(), fixture.clientId(), sauna, fixture.deviceA(), "sauna-centro",
				date, start.plusHours(1)).orElseThrow();

		assertThat(base.usageMode()).isEqualTo(VisitPackageBenefitUsageMode.CONSUMES_VISIT);
		assertThat(included.usageMode()).isEqualTo(VisitPackageBenefitUsageMode.INCLUDED_WITH_VISIT);
		assertThat(included.baseConsumptionId()).isEqualTo(base.usageId());
		assertThat(included.remainingAfter()).isEqualTo(3);
		assertThat(fixture.remaining()).isEqualTo(3);
		assertThat(fixture.consumptionCount()).isEqualTo(1);
		assertThat(fixture.includedAccessCount()).isEqualTo(2);
		assertThat(fixture.authorize(
				fixture.branchA(), fixture.clientId(), sauna, fixture.deviceA(), "sauna-before",
				date, start.plusHours(2))).contains(deniedBeforeBase);
		assertThat(fixture.authorize(
				fixture.branchA(), fixture.clientId(), sauna, fixture.deviceA(), "sauna-centro",
				date, start.plusHours(2))).contains(included);
		assertThat(fixture.includedAccessCount()).isEqualTo(2);
		assertThat(fixture.authorize(
				fixture.branchB(), fixture.clientId(), sauna, fixture.deviceB(), "sauna-tecnologica",
				date, start.plusHours(3))).isEmpty();
	}

	@Test
	void includedAccessDoesNotReuseABaseVisitFromAnotherDayOrBranch() {
		Fixture fixture = fixture(5);
		UUID general = fixture.insertAccessBenefit(fixture.branchA(), "GENERAL", "PLAN_COVERAGE");
		UUID recovery = fixture.insertAccessBenefit(fixture.branchA(), "RECOVERY", "PLAN_COVERAGE");
		fixture.activate(List.of(
				new BenefitCoverage(
						general, VisitPackageBenefitUsageMode.CONSUMES_VISIT,
						List.of(fixture.branchA(), fixture.branchB())),
				new BenefitCoverage(
						recovery, VisitPackageBenefitUsageMode.INCLUDED_WITH_VISIT,
						List.of(fixture.branchA(), fixture.branchB()))));
		LocalDate firstDay = LocalDate.of(2026, 8, 28);
		OffsetDateTime occurredAt = OffsetDateTime.parse("2026-08-28T14:00:00Z");
		fixture.authorize(
				fixture.branchA(), fixture.clientId(), general, fixture.deviceA(), "base-a",
				firstDay, occurredAt).orElseThrow();

		assertThat(fixture.authorize(
				fixture.branchB(), fixture.clientId(), recovery, fixture.deviceB(), "included-b",
				firstDay, occurredAt.plusHours(1))).get()
				.extracting(VisitPackageAccessResult::granted).isEqualTo(false);
		assertThat(fixture.authorize(
				fixture.branchA(), fixture.clientId(), recovery, fixture.deviceA(), "included-next-day",
				firstDay.plusDays(1), occurredAt.plusDays(1))).get()
				.extracting(VisitPackageAccessResult::granted).isEqualTo(false);
		assertThat(fixture.remaining()).isEqualTo(4);
	}

	@Test
	void packagePurchasedInTecnologicaKeepsCentroPremiumAccessLocalAndUsesOneSharedBalance() {
		Fixture fixture = fixture(6);
		UUID generalCentro = fixture.insertAccessBenefit(fixture.branchA(), "GENERAL-CENTRO", "LOCAL");
		UUID generalTecnologica = fixture.insertAccessBenefit(
				fixture.branchB(), "GENERAL-TECNOLOGICA", "LOCAL");
		UUID sauna = fixture.insertAccessBenefit(fixture.branchA(), "SAUNA", "LOCAL");
		UUID cryotherapy = fixture.insertAccessBenefit(fixture.branchA(), "CRIOTERAPIA", "LOCAL");
		UUID premiumLockers = fixture.insertAccessBenefit(fixture.branchA(), "LOCKERS-PREMIUM", "LOCAL");
		fixture.activateFrom(fixture.branchB(), List.of(
				new BenefitCoverage(
						generalCentro, VisitPackageBenefitUsageMode.CONSUMES_VISIT,
						List.of(fixture.branchA())),
				new BenefitCoverage(
						generalTecnologica, VisitPackageBenefitUsageMode.CONSUMES_VISIT,
						List.of(fixture.branchB())),
				new BenefitCoverage(
						sauna, VisitPackageBenefitUsageMode.INCLUDED_WITH_VISIT,
						List.of(fixture.branchA())),
				new BenefitCoverage(
						cryotherapy, VisitPackageBenefitUsageMode.INCLUDED_WITH_VISIT,
						List.of(fixture.branchA())),
				new BenefitCoverage(
						premiumLockers, VisitPackageBenefitUsageMode.INCLUDED_WITH_VISIT,
						List.of(fixture.branchA()))));
		LocalDate date = LocalDate.of(2026, 8, 28);
		OffsetDateTime occurredAt = OffsetDateTime.parse("2026-08-28T14:00:00Z");

		assertThat(fixture.authorize(
				fixture.branchB(), fixture.clientId(), sauna, fixture.deviceB(), "tec-sauna",
				date, occurredAt)).isEmpty();
		assertThat(fixture.authorize(
				fixture.branchB(), fixture.clientId(), generalTecnologica, fixture.deviceB(),
				"tec-general", date, occurredAt.plusMinutes(1)))
				.get().extracting(VisitPackageAccessResult::remainingAfter).isEqualTo(5);
		assertThat(fixture.authorize(
				fixture.branchA(), fixture.clientId(), sauna, fixture.deviceA(),
				"centro-sauna-before", date, occurredAt.plusHours(1)))
				.get().extracting(VisitPackageAccessResult::granted).isEqualTo(false);
		assertThat(fixture.authorize(
				fixture.branchA(), fixture.clientId(), generalCentro, fixture.deviceA(),
				"centro-general", date, occurredAt.plusHours(2)))
				.get().extracting(VisitPackageAccessResult::remainingAfter).isEqualTo(4);
		for (var premium : List.of(sauna, cryotherapy, premiumLockers)) {
			assertThat(fixture.authorize(
					fixture.branchA(), fixture.clientId(), premium, fixture.deviceA(),
					"centro-premium-" + premium, date, occurredAt.plusHours(3)))
					.get().satisfies(result -> {
						assertThat(result.granted()).isTrue();
						assertThat(result.usageMode())
								.isEqualTo(VisitPackageBenefitUsageMode.INCLUDED_WITH_VISIT);
						assertThat(result.remainingAfter()).isEqualTo(4);
					});
		}

		assertThat(fixture.remaining()).isEqualTo(4);
		assertThat(fixture.consumptionCount()).isEqualTo(2);
		assertThat(fixture.includedAccessCount()).isEqualTo(4);
	}

	@Test
	void includedAccessDoesNotReuseABaseVisitFromAnotherEntitlement() {
		Fixture fixture = fixture(3);
		UUID general = fixture.insertAccessBenefit(fixture.branchA(), "GENERAL", "LOCAL");
		UUID sauna = fixture.insertAccessBenefit(fixture.branchA(), "SAUNA", "LOCAL");
		List<BenefitCoverage> coverages = List.of(
				new BenefitCoverage(
						general, VisitPackageBenefitUsageMode.CONSUMES_VISIT,
						List.of(fixture.branchA())),
				new BenefitCoverage(
						sauna, VisitPackageBenefitUsageMode.INCLUDED_WITH_VISIT,
						List.of(fixture.branchA())));
		fixture.activate(coverages);
		LocalDate date = LocalDate.of(2026, 8, 28);
		OffsetDateTime occurredAt = OffsetDateTime.parse("2026-08-28T14:00:00Z");
		fixture.authorize(
				fixture.branchA(), fixture.clientId(), general, fixture.deviceA(), "first-entitlement-base",
				date, occurredAt).orElseThrow();

		fixture.cancelAndActivateReplacement(coverages, date, occurredAt.plusMinutes(5));

		assertThat(fixture.authorize(
				fixture.branchA(), fixture.clientId(), sauna, fixture.deviceA(), "replacement-sauna",
				date, occurredAt.plusHours(1))).get()
				.extracting(VisitPackageAccessResult::granted).isEqualTo(false);
		assertThat(fixture.activeRemaining()).isEqualTo(3);
		assertThat(fixture.consumptionCount()).isEqualTo(1);
	}

	@Test
	void saunaCanBeTheConsumingAccessOfAnExclusivePackage() {
		Fixture fixture = fixture(2);
		UUID sauna = fixture.insertAccessBenefit(fixture.branchA(), "SAUNA-SESSION", "LOCAL");
		fixture.activate(List.of(new BenefitCoverage(
				sauna, VisitPackageBenefitUsageMode.CONSUMES_VISIT, List.of(fixture.branchA()))));

		assertThat(fixture.consume(
				fixture.branchA(), fixture.clientId(), List.of(sauna), fixture.deviceA(), "sauna-1"))
				.get().extracting(VisitPackageAccessResult::remainingAfter).isEqualTo(1);
		assertThat(fixture.consume(
				fixture.branchA(), fixture.clientId(), List.of(sauna), fixture.deviceA(), "sauna-2"))
				.get().extracting(VisitPackageAccessResult::remainingAfter).isEqualTo(0);
		assertThat(fixture.consumptionCount()).isEqualTo(2);
	}

	@Test
	void soldEntitlementKeepsItsUsageModeAfterCommercialConfigurationChanges() {
		Fixture fixture = fixture(3);
		UUID general = fixture.insertAccessBenefit(fixture.branchA(), "GENERAL", "LOCAL");
		UUID sauna = fixture.insertAccessBenefit(fixture.branchA(), "SAUNA", "LOCAL");
		fixture.setCommercialUsage(general, VisitPackageBenefitUsageMode.CONSUMES_VISIT);
		fixture.setCommercialUsage(sauna, VisitPackageBenefitUsageMode.INCLUDED_WITH_VISIT);
		fixture.activate(List.of(
				new BenefitCoverage(
						general, VisitPackageBenefitUsageMode.CONSUMES_VISIT, List.of(fixture.branchA())),
				new BenefitCoverage(
						sauna, VisitPackageBenefitUsageMode.INCLUDED_WITH_VISIT, List.of(fixture.branchA()))));
		fixture.setCommercialUsage(sauna, VisitPackageBenefitUsageMode.CONSUMES_VISIT);
		LocalDate date = LocalDate.of(2026, 8, 28);
		OffsetDateTime occurredAt = OffsetDateTime.parse("2026-08-28T14:00:00Z");

		fixture.authorize(
				fixture.branchA(), fixture.clientId(), general, fixture.deviceA(), "snapshot-base",
				date, occurredAt).orElseThrow();
		VisitPackageAccessResult saunaUsage = fixture.authorize(
				fixture.branchA(), fixture.clientId(), sauna, fixture.deviceA(), "snapshot-sauna",
				date, occurredAt.plusHours(1)).orElseThrow();

		assertThat(fixture.commercialUsage(sauna))
				.isEqualTo(VisitPackageBenefitUsageMode.CONSUMES_VISIT);
		assertThat(fixture.snapshotUsage(sauna))
				.isEqualTo(VisitPackageBenefitUsageMode.INCLUDED_WITH_VISIT);
		assertThat(saunaUsage.usageMode()).isEqualTo(VisitPackageBenefitUsageMode.INCLUDED_WITH_VISIT);
		assertThat(fixture.remaining()).isEqualTo(2);
	}

	@Test
	void idempotencyAndBalancesRemainIsolatedByOrganization() {
		Fixture first = fixture(2);
		Fixture second = fixture(2);
		UUID firstGeneral = first.activateConsuming(List.of(first.branchA()));
		UUID secondGeneral = second.activateConsuming(List.of(second.branchA()));

		assertThat(first.consume(
				first.branchA(), first.clientId(), List.of(firstGeneral), first.deviceA(), "shared-key"))
				.isPresent();
		assertThat(second.consume(
				second.branchA(), second.clientId(), List.of(secondGeneral), second.deviceA(), "shared-key"))
				.isPresent();
		assertThat(first.remaining()).isEqualTo(1);
		assertThat(second.remaining()).isEqualTo(1);
		assertThat(first.repository().authorizeForAccess(
				first.organizationId(), second.branchA(), second.clientId(), List.of(secondGeneral),
				second.deviceA(), "cross-tenant", TEST_BUSINESS_DATE, TEST_OCCURRED_AT)).isEmpty();
	}

	@Test
	void businessDateUsesTheTimezoneOfTheRequestedBranch() {
		Fixture fixture = fixture(1);
		assertThat(fixture.repository().businessDate(
				fixture.organizationId(), fixture.branchA(),
				OffsetDateTime.parse("2026-08-29T05:30:00Z")))
				.isEqualTo(LocalDate.of(2026, 8, 28));
	}

	@Test
	void receptionConsumesFiveVisitsWithoutChargingIncludedBenefitsAndRejectsSixth() {
		Fixture fixture = fixture(5);
		UUID general = fixture.insertAccessBenefit(fixture.branchA(), "GENERAL", "PLAN_COVERAGE");
		UUID shower = fixture.insertAccessBenefit(fixture.branchA(), "SHOWER", "LOCAL");
		fixture.activate(List.of(
				new BenefitCoverage(general, VisitPackageBenefitUsageMode.CONSUMES_VISIT, List.of(fixture.branchA())),
				new BenefitCoverage(shower, VisitPackageBenefitUsageMode.INCLUDED_WITH_VISIT, List.of(fixture.branchA()))));
		fixture.makeCurrent();
		var desk = fixture.desk();
		var receiver = fixture.actor(fixture.branchA(), false);
		var preview = desk.preview(receiver, fixture.clientId());
		assertThat(preview.options()).extracting(ManualCheckInService.AccessOption::id).containsExactly(general);
		assertThat(preview.visitPackage().benefits()).extracting(ManualCheckInService.PackageBenefit::id)
				.containsExactlyInAnyOrder(general, shower);
		assertThat(fixture.register(desk, receiver, UUID.randomUUID(), shower).decision()).isEqualTo("DENIED");

		for (int before = 5; before > 0; before--) {
			UUID key = UUID.randomUUID();
			Entry entry = fixture.register(desk, receiver, key, general);
			assertThat(entry.decision()).isEqualTo("GRANTED");
			assertThat(entry.remainingBefore()).isEqualTo(before);
			assertThat(entry.remainingAfter()).isEqualTo(before - 1);
			assertThat(entry.method()).isEqualTo("MANUAL");
			assertThat(entry.actorUserId()).isEqualTo(receiver.userId());
			assertThat(entry.businessDate()).isEqualTo(fixture.today());
			assertThat(fixture.register(desk, receiver, key, general)).isEqualTo(entry);
			fixture.age(entry);
		}
		Entry denied = fixture.register(desk, receiver, UUID.randomUUID(), general);
		assertThat(denied.decision()).isEqualTo("DENIED");
		assertThat(denied.reason()).contains("agotado");
		assertThat(fixture.consumptionCount()).isEqualTo(5);
		assertThat(fixture.includedAccessCount()).isZero();
		assertThat(fixture.remaining()).isZero();
		assertThat(desk.history(receiver).entries()).hasSize(7);
		assertThat(fixture.count("audit_log")).isEqualTo(7);
		assertThat(fixture.count("sale")).isEqualTo(1);
		assertThat(fixture.count("payment")).isZero();
		assertThat(fixture.jdbc().sql("SELECT count(*) FROM access_log WHERE organization_id=:org AND device_id IS NOT NULL")
				.param("org", fixture.organizationId()).query(Integer.class).single()).isZero();
	}

	@Test
	void receptionUsesExistingMembershipGraceAndNeverConsumesWhenMembershipGrantsTheSameRight() {
		Fixture fixture = fixture(2);
		UUID general = fixture.activateConsuming(List.of(fixture.branchA()));
		fixture.makeCurrent();
		UUID membership = fixture.addMembership(general, fixture.today().minusDays(1), fixture.today());
		var desk = fixture.desk();
		var receiver = fixture.actor(fixture.branchA(), false);
		var supervisor = fixture.actor(fixture.branchA(), true);
		var grant = fixture.register(desk, receiver, UUID.randomUUID(), general);
		assertThat(grant.membershipId()).isEqualTo(membership);
		assertThat(grant.entitlementId()).isNull();
		assertThat(fixture.remaining()).isEqualTo(2);
		assertThat(fixture.reverse(desk, supervisor, grant.id(), "Error de identificación").reversal().remainingAfter()).isNull();
		assertThat(fixture.remaining()).isEqualTo(2);

		fixture.jdbc().sql("UPDATE membership SET grace_ends_on=ends_on WHERE id=:id").param("id", membership).update();
		Entry packageGrant = fixture.register(desk, receiver, UUID.randomUUID(), general);
		assertThat(packageGrant.entitlementId()).isNotNull();
		assertThat(packageGrant.membershipId()).isNull();
		assertThat(packageGrant.remainingAfter()).isEqualTo(1);
		fixture.age(packageGrant);
		fixture.jdbc().sql("UPDATE visit_package_entitlement SET valid_until=:end WHERE sale_id=:sale")
				.param("end", fixture.today().minusDays(1)).param("sale", fixture.saleId()).update();
		assertThat(fixture.register(desk, receiver, UUID.randomUUID(), general).reason()).contains("vencido");
		assertThat(fixture.remaining()).isEqualTo(1);
	}

	@Test
	void receptionDeniesExpiredMembershipSuspendedInactiveAndWrongBranchPackage() {
		Fixture fixture = fixture(2);
		UUID general = fixture.activateConsuming(List.of(fixture.branchA()));
		fixture.makeCurrent();
		fixture.addMembership(general, fixture.today().minusDays(2), fixture.today().minusDays(1));
		var desk = fixture.desk();
		var receiver = fixture.actor(fixture.branchA(), false);
		for (String status : List.of("SUSPENDED", "INACTIVE")) {
			fixture.jdbc().sql("UPDATE client SET status=:status WHERE id=:id")
					.param("id", fixture.clientId()).param("status", status).update();
			assertThat(desk.preview(receiver, fixture.clientId()).options()).isEmpty();
			assertThat(fixture.register(desk, receiver, UUID.randomUUID(), general).reason()).contains("inactivo");
		}
		fixture.jdbc().sql("UPDATE client SET status='ACTIVE' WHERE id=:id").param("id", fixture.clientId()).update();
		fixture.jdbc().sql("UPDATE visit_package_entitlement SET valid_until=:end WHERE sale_id=:sale")
				.param("end", fixture.today().minusDays(1)).param("sale", fixture.saleId()).update();
		assertThat(desk.preview(receiver, fixture.clientId()).membership()).isNull();
		assertThat(fixture.register(desk, receiver, UUID.randomUUID(), general).decision()).isEqualTo("DENIED");

		fixture.makeCurrent();
		fixture.jdbc().sql("UPDATE client SET home_branch_id=:branch WHERE id=:id")
				.param("branch", fixture.branchB()).param("id", fixture.clientId()).update();
		fixture.jdbc().sql("DELETE FROM visit_package_entitlement_branch WHERE organization_id=:org AND branch_id=:branch")
				.param("org", fixture.organizationId()).param("branch", fixture.branchB()).update();
		var branchBActor = fixture.actor(fixture.branchB(), false);
		assertThat(desk.preview(branchBActor, fixture.clientId()).options()).isEmpty();
		assertThat(fixture.register(desk, branchBActor, UUID.randomUUID(), general).reason()).contains("no cubre");
		assertThat(fixture.consumptionCount()).isZero();
	}

	@Test
	void receptionSerializesConcurrentReceptionistsAndSupportsAnAuditedDistinctEntry() throws Exception {
		Fixture fixture = fixture(2);
		UUID general = fixture.activateConsuming(List.of(fixture.branchA()));
		fixture.makeCurrent();
		var desk = fixture.desk();
		var first = fixture.actor(fixture.branchA(), false);
		var second = fixture.actor(fixture.branchA(), false);
		var supervisor = fixture.actor(fixture.branchA(), true);
		UUID firstKey = UUID.randomUUID();
		UUID secondKey = UUID.randomUUID();
		var attempts = simultaneous(
				() -> fixture.register(desk, first, firstKey, general),
				() -> fixture.register(desk, second, secondKey, general));
		assertThat(attempts).extracting(Entry::decision).containsExactlyInAnyOrder("GRANTED", "DUPLICATE");
		var replays = simultaneous(
				() -> fixture.register(desk, first, firstKey, general),
				() -> fixture.register(desk, second, firstKey, general));
		assertThat(replays).containsExactly(attempts.getFirst(), attempts.getFirst());
		Entry original = attempts.stream().filter(entry -> entry.decision().equals("GRANTED")).findFirst().orElseThrow();
		Entry duplicate = attempts.stream().filter(entry -> entry.decision().equals("DUPLICATE")).findFirst().orElseThrow();
		assertThat(duplicate.duplicateOf()).isEqualTo(original.id());
		assertThat(fixture.remaining()).isEqualTo(1);
		assertThatThrownBy(() -> fixture.register(desk, first, firstKey, UUID.randomUUID()))
				.isInstanceOfSatisfying(BusinessException.class, error -> assertThat(error.code()).isEqualTo("CHECK_IN_KEY_REUSED"));
		assertThatThrownBy(() -> fixture.transaction().execute(tx -> desk.register(second, second.branchId(), UUID.randomUUID(),
				fixture.clientId(), general, original.id(), "Reingreso", "test")))
				.isInstanceOf(AccessDeniedException.class);
		Entry distinct = fixture.transaction().execute(tx -> desk.register(supervisor, supervisor.branchId(), UUID.randomUUID(),
				fixture.clientId(), general, original.id(), "Reingreso confirmado", "test"));
		assertThat(distinct.decision()).isEqualTo("GRANTED");
		assertThat(distinct.overrideOf()).isEqualTo(original.id());
		assertThat(distinct.overrideReason()).isEqualTo("Reingreso confirmado");
		assertThat(fixture.remaining()).isZero();
		assertThat(fixture.consumptionCount()).isEqualTo(2);
	}

	@Test
	void lastVisitIsSharedAcrossBranchesAndConcurrentReversalCreditsExactlyOnce() throws Exception {
		Fixture fixture = fixture(1);
		UUID general = fixture.activateConsuming(List.of(fixture.branchA(), fixture.branchB()));
		fixture.makeCurrent();
		var desk = fixture.desk();
		var first = fixture.actor(fixture.branchA(), false);
		var second = fixture.actor(fixture.branchB(), false);
		var entries = simultaneous(
				() -> fixture.register(desk, first, UUID.randomUUID(), general),
				() -> fixture.register(desk, second, UUID.randomUUID(), general));
		assertThat(entries).extracting(Entry::decision).containsExactlyInAnyOrder("GRANTED", "DENIED");
		Entry grant = entries.stream().filter(entry -> entry.decision().equals("GRANTED")).findFirst().orElseThrow();
		var supervisor = fixture.actor(grant.branchId(), true);
		assertThat(fixture.remaining()).isZero();
		assertThatThrownBy(() -> fixture.reverse(desk, fixture.actor(grant.branchId(), false), grant.id(), "Error"))
				.isInstanceOf(AccessDeniedException.class);
		var corrections = simultaneous(
				() -> fixture.reverse(desk, supervisor, grant.id(), "Error"),
				() -> fixture.reverse(desk, supervisor, grant.id(), "Error"));
		assertThat(corrections.getFirst()).isEqualTo(corrections.getLast());
		assertThat(corrections.getFirst().reversal().remainingBefore()).isZero();
		assertThat(corrections.getFirst().reversal().remainingAfter()).isEqualTo(1);
		assertThat(fixture.remaining()).isEqualTo(1);
		assertThat(fixture.count("access_manual_reversal")).isEqualTo(1);
		assertThat(fixture.count("audit_log")).isEqualTo(3);
		assertThatThrownBy(() -> fixture.reverse(desk, supervisor, grant.id(), "Otro motivo"))
				.isInstanceOfSatisfying(BusinessException.class, error -> assertThat(error.code()).isEqualTo("CHECK_IN_ALREADY_REVERSED"));
		assertThat(fixture.register(desk, supervisor, UUID.randomUUID(), general).remainingAfter()).isZero();
	}

	@Test
	void receptionSearchAndWritesStayInTenantAndBranch() {
		Fixture fixture = fixture(1);
		Fixture other = fixture(1);
		var desk = fixture.desk();
		var first = fixture.actor(fixture.branchA(), true);
		var second = fixture.actor(fixture.branchB(), true);
		UUID general = fixture.activateConsuming(List.of(fixture.branchA()));
		fixture.makeCurrent();
		var person = desk.search(first, fixture.clientId().toString()).getFirst();
		assertThat(desk.search(first, "Paola")).containsExactly(person);
		assertThat(desk.search(first, person.phone())).containsExactly(person);
		assertThat(desk.search(first, other.clientId().toString())).isEmpty();
		assertThat(desk.search(second, fixture.clientId().toString())).isEmpty();
		assertThatThrownBy(() -> desk.preview(second, fixture.clientId())).isInstanceOf(BusinessException.class);
		assertThatThrownBy(() -> fixture.transaction().execute(tx -> desk.register(
				first, second.branchId(), UUID.randomUUID(), fixture.clientId(), general, null, null, "test")))
				.isInstanceOf(AccessDeniedException.class);
		assertThatThrownBy(() -> fixture.transaction().execute(tx -> desk.register(
				first, first.branchId(), UUID.randomUUID(), other.clientId(), general, null, null, "test")))
				.isInstanceOf(BusinessException.class);
		Entry grant = fixture.register(desk, first, UUID.randomUUID(), general);
		assertThat(desk.history(second).entries()).isEmpty();
		assertThatThrownBy(() -> fixture.reverse(desk, second, grant.id(), "Error")).isInstanceOf(BusinessException.class);
		assertThatThrownBy(() -> fixture.reverse(desk, other.actor(other.branchA(), true), grant.id(), "Error"))
				.isInstanceOf(BusinessException.class);
		assertThat(desk.history(first).entries()).containsExactly(grant);
	}

	private static <T> List<T> simultaneous(java.util.concurrent.Callable<T> first, java.util.concurrent.Callable<T> second)
			throws Exception {
		var gate = new CyclicBarrier(2);
		try (var pool = Executors.newFixedThreadPool(2)) {
			var a = pool.submit(() -> { gate.await(10, TimeUnit.SECONDS); return first.call(); });
			var b = pool.submit(() -> { gate.await(10, TimeUnit.SECONDS); return second.call(); });
			return List.of(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS));
		}
	}

	private static VisitPackageBenefitUsage consumes(UUID benefitId) {
		return new VisitPackageBenefitUsage(benefitId, VisitPackageBenefitUsageMode.CONSUMES_VISIT);
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
		insertBranch(jdbc, organizationId, branchA, "Sucursal Centro", "CTR-" + branchA.toString().substring(0, 8));
		insertBranch(jdbc, organizationId, branchB, "Sucursal Tecnológica", "TEC-" + branchB.toString().substring(0, 8));
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
				.param("name", name).param("code", code.substring(0, Math.min(code.length(), 32))).update();
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
		ManualCheckInService desk() {
			return new ManualCheckInService(new ManualCheckInRepository(jdbc),
					new DefaultEligibilityPolicy(new EligibilityRepository(jdbc)),
					new VisitPackageAccessService(repository), new AuditService(jdbc));
		}

		GymOsPrincipal actor(UUID branch, boolean supervisor) {
			return new GymOsPrincipal(UUID.randomUUID(), organizationId, branch, "fixture", "Fixture",
					supervisor ? Set.of("access.check-in", "access.check-in.override", "access.check-in.reverse")
							: Set.of("access.check-in"));
		}

		LocalDate today() {
			return repository.businessDate(organizationId, branchA, OffsetDateTime.now());
		}

		void makeCurrent() {
			jdbc.sql("UPDATE visit_package_entitlement SET valid_from=:from, valid_until=:until WHERE sale_id=:sale")
					.param("from", today().minusDays(2)).param("until", today().plusDays(27)).param("sale", saleId).update();
		}

		void age(Entry entry) {
			jdbc.sql("UPDATE access_log SET occurred_at=occurred_at - interval '2 minutes' WHERE id=:id")
					.param("id", entry.id()).update();
		}

		int count(String table) {
			return jdbc.sql("SELECT count(*) FROM " + table + " WHERE organization_id=:org")
					.param("org", organizationId).query(Integer.class).single();
		}

		Entry register(ManualCheckInService desk, GymOsPrincipal actor, UUID key, UUID benefit) {
			return transaction.execute(tx -> desk.register(actor, actor.branchId(), key, clientId, benefit, null, null, "test"));
		}

		Entry reverse(ManualCheckInService desk, GymOsPrincipal actor, UUID log, String reason) {
			return transaction.execute(tx -> desk.reverse(actor, actor.branchId(), log, reason, "test"));
		}

		UUID addMembership(UUID benefit, LocalDate ends, LocalDate graceEnds) {
			UUID product = UUID.randomUUID();
			UUID plan = UUID.randomUUID();
			UUID membership = UUID.randomUUID();
			jdbc.sql("""
					INSERT INTO catalog_product (id, organization_id, sku, name, product_type, commercial_subtype,
					    price_cents, currency, taxable, status, created_at, updated_at, version)
					VALUES (:id, :org, :sku, 'Plan mensual', 'MEMBERSHIP_PLAN', 'MEMBERSHIP',
					    1000, 'MXN', true, 'ACTIVE', now(), now(), 0)
					""").param("id", product).param("org", organizationId).param("sku", "M-" + product).update();
			jdbc.sql("""
					INSERT INTO membership_plan (id, organization_id, product_id, duration_days, grace_days,
					    benefits_json, multi_branch_access, status, created_at, updated_at, version)
					VALUES (:id, :org, :product, 30, 1, '{}'::jsonb, false, 'ACTIVE', now(), now(), 0)
					""").param("id", plan).param("org", organizationId).param("product", product).update();
			jdbc.sql("""
					INSERT INTO benefit_membership_plan (benefit_id, membership_plan_id, organization_id, created_at)
					VALUES (:benefit, :plan, :org, now())
					""").param("benefit", benefit).param("plan", plan).param("org", organizationId).update();
			jdbc.sql("""
					INSERT INTO membership (id, organization_id, branch_id, client_id, plan_id, sale_id, status,
					    activated_at, starts_on, ends_on, grace_ends_on, created_at, updated_at, version)
					VALUES (:id, :org, :branch, :client, :plan, :sale, 'ACTIVE',
					    now(), :start, :end, :grace, now(), now(), 0)
					""").param("id", membership).param("org", organizationId).param("branch", branchA)
					.param("client", clientId).param("plan", plan).param("sale", saleId)
					.param("start", ends.minusDays(29)).param("end", ends).param("grace", graceEnds).update();
			return membership;
		}

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
			activateFrom(branchA, benefits);
		}

		void activateFrom(UUID originBranchId, List<BenefitCoverage> benefits) {
			jdbc.sql("""
					UPDATE sale SET branch_id=:branchId, updated_at=now()
					WHERE organization_id=:organizationId AND id=:saleId
					""")
					.param("branchId", originBranchId).param("organizationId", organizationId)
					.param("saleId", saleId).update();
			transaction.executeWithoutResult(status -> {
				repository.createPending(
						organizationId, originBranchId, clientId, packagePlanId, saleId, "Paquete", totalVisits, 30,
						TEST_OCCURRED_AT.plusMinutes(30), List.of(branchA, branchB), benefits, TEST_OCCURRED_AT);
				repository.activateBySale(organizationId, saleId, TEST_BUSINESS_DATE, TEST_OCCURRED_AT);
			});
		}

		void cancelAndActivateReplacement(
				List<BenefitCoverage> benefits, LocalDate validFrom, OffsetDateTime now
		) {
			jdbc.sql("""
					UPDATE visit_package_entitlement
					SET status='CANCELLED', updated_at=:now, version=version + 1
					WHERE organization_id=:organizationId AND sale_id=:saleId
					""")
					.param("organizationId", organizationId).param("saleId", saleId)
					.param("now", now).update();
			UUID replacementSaleId = UUID.randomUUID();
			jdbc.sql("""
					INSERT INTO sale (id, organization_id, branch_id, client_id, sale_number, status,
					 subtotal_cents, discount_cents, total_cents, paid_cents, balance_cents, currency,
					 sold_at, created_at, updated_at, version, idempotency_key)
					VALUES (:id, :organizationId, :branchId, :clientId, :number, 'PAID', 1000, 0,
					 1000, 1000, 0, 'MXN', :now, :now, :now, 0, :key)
					""")
					.param("id", replacementSaleId).param("organizationId", organizationId)
					.param("branchId", branchA).param("clientId", clientId)
					.param("number", "VP-" + replacementSaleId)
					.param("key", "sale-" + replacementSaleId).param("now", now).update();
			transaction.executeWithoutResult(status -> {
				repository.createPending(
						organizationId, branchA, clientId, packagePlanId, replacementSaleId,
						"Paquete reemplazo", totalVisits, 30, now.plusMinutes(30),
						List.of(branchA, branchB), benefits, now);
				repository.activateBySale(organizationId, replacementSaleId, validFrom, now);
			});
		}

		UUID activateConsuming(List<UUID> branches) {
			UUID general = insertAccessBenefit(branchA, "GENERAL", "PLAN_COVERAGE");
			activate(List.of(new BenefitCoverage(
					general, VisitPackageBenefitUsageMode.CONSUMES_VISIT, branches)));
			return general;
		}

		void setCommercialUsage(UUID benefitId, VisitPackageBenefitUsageMode usageMode) {
			jdbc.sql("""
					INSERT INTO visit_package_plan_benefit
					    (visit_package_plan_id, benefit_id, organization_id, usage_mode, created_at)
					VALUES (:planId, :benefitId, :organizationId, :usageMode, now())
					ON CONFLICT (visit_package_plan_id, benefit_id)
					DO UPDATE SET usage_mode=EXCLUDED.usage_mode
					""")
					.param("planId", packagePlanId).param("benefitId", benefitId)
					.param("organizationId", organizationId).param("usageMode", usageMode.name())
					.update();
		}

		VisitPackageBenefitUsageMode commercialUsage(UUID benefitId) {
			return VisitPackageBenefitUsageMode.valueOf(jdbc.sql("""
					SELECT usage_mode FROM visit_package_plan_benefit
					WHERE organization_id=:organizationId
					  AND visit_package_plan_id=:planId AND benefit_id=:benefitId
					""")
					.param("organizationId", organizationId).param("planId", packagePlanId)
					.param("benefitId", benefitId).query(String.class).single());
		}

		VisitPackageBenefitUsageMode snapshotUsage(UUID benefitId) {
			return VisitPackageBenefitUsageMode.valueOf(jdbc.sql("""
					SELECT usage_mode FROM visit_package_entitlement_benefit_branch
					WHERE organization_id=:organizationId AND benefit_id=:benefitId
					ORDER BY created_at DESC LIMIT 1
					""")
					.param("organizationId", organizationId).param("benefitId", benefitId)
					.query(String.class).single());
		}

		Optional<VisitPackageAccessResult> consume(
				UUID branchId, UUID targetClientId, List<UUID> benefits, UUID deviceId, String key
		) {
			return transaction.execute(status -> repository.authorizeForAccess(
					organizationId, branchId, targetClientId, benefits, deviceId, key,
					TEST_BUSINESS_DATE, TEST_OCCURRED_AT));
		}

		Optional<VisitPackageAccessResult> authorize(
				UUID branchId, UUID targetClientId, UUID benefitId, UUID deviceId, String key,
				LocalDate businessDate, OffsetDateTime occurredAt
		) {
			return transaction.execute(status -> repository.authorizeForAccess(
					organizationId, branchId, targetClientId, List.of(benefitId), deviceId, key,
					businessDate, occurredAt));
		}

		int remaining() {
			return jdbc.sql("SELECT remaining_visits FROM visit_package_entitlement WHERE sale_id=:saleId")
					.param("saleId", saleId).query(Integer.class).single();
		}

		int activeRemaining() {
			return jdbc.sql("""
					SELECT remaining_visits FROM visit_package_entitlement
					WHERE organization_id=:organizationId AND client_id=:clientId AND status='ACTIVE'
					""")
					.param("organizationId", organizationId).param("clientId", clientId)
					.query(Integer.class).single();
		}

		int consumptionCount() {
			return jdbc.sql("SELECT COUNT(*) FROM visit_package_consumption WHERE organization_id=:organizationId")
					.param("organizationId", organizationId).query(Integer.class).single();
		}

		int includedAccessCount() {
			return jdbc.sql("SELECT COUNT(*) FROM visit_package_included_access WHERE organization_id=:organizationId")
					.param("organizationId", organizationId).query(Integer.class).single();
		}
	}
}
