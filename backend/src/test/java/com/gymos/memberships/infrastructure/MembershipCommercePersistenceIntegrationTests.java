package com.gymos.memberships.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.gymos.clients.infrastructure.ClientRepository;
import com.gymos.eligibility.api.EligibilityPolicy.EligibleBenefit;
import com.gymos.eligibility.api.EligibilityPolicy.EligibilityRequest;
import com.gymos.eligibility.application.DefaultEligibilityPolicy;
import com.gymos.eligibility.infrastructure.EligibilityRepository;
import com.gymos.sales.api.operations.MembershipBenefitSnapshot;
import com.gymos.sales.infrastructure.MembershipSaleCommandRepository;
import com.gymos.sales.infrastructure.MembershipSaleCommandRepository.MembershipSaleRecord;
import com.gymos.sales.infrastructure.SalesReadRepository;
import com.gymos.sales.infrastructure.SalesReportRepository;
import java.sql.Types;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
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
class MembershipCommercePersistenceIntegrationTests {
	@Container
	static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");

	private DataSource dataSource;
	private JdbcClient jdbcClient;
	private MembershipRepository memberships;
	private ClientRepository clients;
	private UUID organizationId;
	private UUID branchId;
	private UUID clientId;
	private UUID firstPlanId;
	private UUID firstProductId;

	@BeforeEach
	void setUp() {
		dataSource = new DriverManagerDataSource(
				postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
		Flyway.configure().dataSource(dataSource).locations("classpath:db/migration")
				.cleanDisabled(false).load().clean();
		Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
		jdbcClient = JdbcClient.create(dataSource);
		memberships = new MembershipRepository(
				new MembershipClientRepository(jdbcClient),
				new MembershipQueryRepository(jdbcClient),
				new MembershipPlanLookupRepository(jdbcClient),
				new MembershipEventRepository(jdbcClient),
				new MembershipCommandRepository(jdbcClient)
		);
		clients = new ClientRepository(jdbcClient);
		organizationId = UUID.randomUUID();
		branchId = UUID.randomUUID();
		clientId = UUID.randomUUID();
		firstPlanId = UUID.randomUUID();
		firstProductId = UUID.randomUUID();
		seedTenant();
		insertPlan(firstPlanId, firstProductId, "PLAN-30", "Plan mensual");
	}

	@Test
	void concurrentRenewalsSerializeOnMembershipAndCreateConsecutivePeriods() throws Exception {
		LocalDate initialStart = LocalDate.of(2026, 7, 22);
		LocalDate initialEnd = LocalDate.of(2026, 8, 20);
		UUID membershipId = UUID.randomUUID();
		UUID initialSaleId = insertSale("S-INITIAL", "MEMBERSHIP_ENROLLMENT", 10_000, 0);
		UUID firstRenewalSaleId = insertSale("S-RENEW-1", "MEMBERSHIP_RENEWAL", 10_000, 0);
		UUID secondRenewalSaleId = insertSale("S-RENEW-2", "MEMBERSHIP_RENEWAL", 10_000, 0);
		insertMembership(membershipId, firstPlanId, initialSaleId, initialStart, initialEnd, initialEnd.plusDays(3));
		assertThat(memberships.byId(organizationId, branchId, membershipId)).isPresent();

		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		try (var executor = Executors.newFixedThreadPool(2)) {
			var first = executor.submit(() -> renewInTransaction(
					membershipId, firstRenewalSaleId, "event-renew-1", ready, start));
			var second = executor.submit(() -> renewInTransaction(
					membershipId, secondRenewalSaleId, "event-renew-2", ready, start));
			ready.await();
			start.countDown();
			first.get();
			second.get();
		}

		var current = memberships.byId(organizationId, branchId, membershipId).orElseThrow();
		assertThat(current.startsOn()).isEqualTo(initialStart);
		assertThat(current.endsOn()).isEqualTo(initialEnd.plusDays(60));

		var periods = memberships.periods(organizationId, branchId, membershipId).stream()
				.sorted(java.util.Comparator.comparing(MembershipRepository.MembershipPeriodSummary::startsOn))
				.toList();
		assertThat(periods).hasSize(2);
		assertThat(periods.get(0).startsOn()).isEqualTo(initialEnd.plusDays(1));
		assertThat(periods.get(0).endsOn().plusDays(1)).isEqualTo(periods.get(1).startsOn());
		assertThat(periods.get(1).endsOn()).isEqualTo(initialEnd.plusDays(60));
	}

	@Test
	void expirationReleasesTheSingleActiveMembershipSlot() {
		LocalDate today = LocalDate.of(2026, 7, 29);
		UUID saleId = insertSale("S-EXPIRED", "MEMBERSHIP_ENROLLMENT", 10_000, 0);
		UUID membershipId = UUID.randomUUID();
		insertMembership(
				membershipId,
				firstPlanId,
				saleId,
				today.minusDays(40),
				today.minusDays(4),
				today.minusDays(1)
		);

		assertThat(memberships.expireDueMemberships(organizationId, clientId, today)).isEqualTo(1);
		assertThat(memberships.byId(organizationId, branchId, membershipId).orElseThrow().status())
				.isEqualTo("EXPIRED");
		assertThat(memberships.hasOtherActiveMembership(organizationId, clientId, null)).isFalse();
	}

	@Test
	void completedMultiBranchRenewalCanBeResolvedFromASecondaryBranch() {
		UUID secondaryBranchId = UUID.randomUUID();
		jdbcClient.sql("""
				INSERT INTO branch (id, organization_id, name, code, timezone, status,
				 created_at, updated_at, version)
				VALUES (:id, :organizationId, 'Tecnológica', :code, 'America/Mexico_City',
				 'ACTIVE', now(), now(), 0)
				""").param("id", secondaryBranchId).param("organizationId", organizationId)
				.param("code", "TEC-" + secondaryBranchId).update();
		jdbcClient.sql("UPDATE membership_plan SET multi_branch_access=TRUE WHERE id=:planId")
				.param("planId", firstPlanId).update();
		UUID saleId = insertSale("S-MULTIBRANCH-RENEW", "MEMBERSHIP_RENEWAL", 10_000, 0);
		UUID membershipId = UUID.randomUUID();
		LocalDate today = LocalDate.now();
		insertMembership(
				membershipId, firstPlanId, saleId, today, today.plusDays(29), today.plusDays(32));

		assertThat(memberships.bySaleAndPlan(
				organizationId, secondaryBranchId, saleId, firstPlanId))
				.get().extracting(MembershipRepository.MembershipSummary::id).isEqualTo(membershipId);

		jdbcClient.sql("UPDATE membership_plan SET multi_branch_access=FALSE WHERE id=:planId")
				.param("planId", firstPlanId).update();
		assertThat(memberships.bySaleAndPlan(
				organizationId, secondaryBranchId, saleId, firstPlanId)).isEmpty();
	}

	@Test
	void clientSearchProjectsTheCurrentMembershipPlanAsActive() {
		LocalDate today = LocalDate.now();
		UUID saleId = insertSale("S-CLIENT-ACTIVE", "MEMBERSHIP_ENROLLMENT", 10_000, 0);
		insertMembership(
				UUID.randomUUID(),
				firstPlanId,
				saleId,
				today.minusDays(5),
				today.plusDays(24),
				today.plusDays(27)
		);

		var client = clients.search(organizationId, branchId, null, null, 10).getFirst();

		assertThat(client.currentPlanName()).isEqualTo("Plan mensual");
		assertThat(client.membershipStatus()).isEqualTo("ACTIVE");
	}

	@Test
	void clientSearchProjectsAnOverdueActiveRowAsExpired() {
		LocalDate today = LocalDate.now();
		UUID saleId = insertSale("S-CLIENT-EXPIRED", "MEMBERSHIP_ENROLLMENT", 10_000, 0);
		insertMembership(
				UUID.randomUUID(),
				firstPlanId,
				saleId,
				today.minusDays(40),
				today.minusDays(6),
				today.minusDays(3)
		);

		var client = clients.search(organizationId, branchId, null, null, 10).getFirst();

		assertThat(client.currentPlanName()).isEqualTo("Plan mensual");
		assertThat(client.membershipStatus()).isEqualTo("EXPIRED");
	}

	@Test
	void pricingEligibilityAndAccessInheritanceReactToCurrentConfiguration() {
		LocalDate today = LocalDate.of(2026, 7, 29);
		UUID secondPlanId = UUID.randomUUID();
		insertPlan(secondPlanId, UUID.randomUUID(), "PLAN-PLUS", "Plan plus");
		UUID saleId = insertSale("S-ACTIVE", "MEMBERSHIP_ENROLLMENT", 10_000, 0);
		UUID membershipId = UUID.randomUUID();
		insertMembership(
				membershipId, firstPlanId, saleId,
				today.minusDays(2), today.plusDays(27), today.plusDays(30));

		UUID pricingBenefitId = insertBenefit(
				"STUDENT", "Estudiante", "STUDENT", "PERCENT", null, 10.0,
				false, true, null, today.minusDays(1), today.plusMonths(1));
		assignBenefit(pricingBenefitId, "ASSIGNED", today.minusDays(1), today.plusMonths(1));
		jdbcClient.sql("UPDATE client_benefit SET branch_id=:branchId WHERE benefit_id=:benefitId")
				.param("branchId", branchId)
				.param("benefitId", pricingBenefitId)
				.update();
		UUID evidenceBenefitId = insertBenefit(
				"TEACHER", "Docente", "AGREEMENT", "FIXED", 1_000, null,
				true, false, null, null, null);
		assignBenefit(evidenceBenefitId, "ASSIGNED", today.minusDays(1), today.plusMonths(1));
		UUID accessBenefitId = insertBenefit(
				"ACCESS-SAUNA", "Acceso sauna", "ACCESS", "NONE", null, null,
				false, false, null, null, null);

		var eligibility = new DefaultEligibilityPolicy(new EligibilityRepository(jdbcClient));
		var pricing = eligibility.evaluate(
				EligibilityRequest.membershipPricing(organizationId, branchId, clientId, today));
		assertThat(pricing.benefits()).extracting(EligibleBenefit::id)
				.containsExactly(pricingBenefitId);

		assertThat(eligibility.evaluate(
				EligibilityRequest.access(organizationId, branchId, clientId, today, java.util.Set.of()))
				.benefits()).isEmpty();

		linkAccessBenefit(accessBenefitId, firstPlanId);
		assertThat(eligibility.evaluate(
				EligibilityRequest.access(organizationId, branchId, clientId, today, java.util.Set.of()))
				.benefits())
				.extracting(EligibleBenefit::id)
				.containsExactly(accessBenefitId);

		jdbcClient.sql("UPDATE membership SET grace_ends_on=:expiredOn WHERE id=:membershipId")
				.param("expiredOn", today.minusDays(1))
				.param("membershipId", membershipId)
				.update();
		assertThat(eligibility.evaluate(
				EligibilityRequest.access(organizationId, branchId, clientId, today, java.util.Set.of()))
				.benefits()).isEmpty();

		jdbcClient.sql("""
				UPDATE membership
				SET plan_id=:planId, grace_ends_on=:graceEndsOn
				WHERE id=:membershipId
				""")
				.param("planId", secondPlanId)
				.param("graceEndsOn", today.plusDays(30))
				.param("membershipId", membershipId)
				.update();
		assertThat(eligibility.evaluate(
				EligibilityRequest.access(organizationId, branchId, clientId, today, java.util.Set.of()))
				.benefits()).isEmpty();
	}

	@Test
	void membershipSaleKeepsBenefitSnapshotAndAppearsInFinancialBreakdown() {
		OffsetDateTime now = OffsetDateTime.now();
		UUID benefitId = insertBenefit(
				"STUDENT", "Descuento estudiante", "STUDENT", "PERCENT", null, 10.0,
				false, true, null, null, null);
		UUID saleId = UUID.randomUUID();
		assertThat(new MembershipSaleCommandRepository(jdbcClient).create(
				membershipSaleRecord(
						saleId, UUID.randomUUID(), "S-SNAPSHOT", "snapshot-sale", benefitId, now)))
				.isTrue();

		jdbcClient.sql("UPDATE benefit SET name='Nombre posterior', discount_percent=20 WHERE id=:id")
				.param("id", benefitId)
				.update();

		var snapshots = new SalesReadRepository(jdbcClient).appliedBenefits(organizationId, saleId);
		assertThat(snapshots).singleElement().satisfies(snapshot -> {
			assertThat(snapshot.code()).isEqualTo("STUDENT");
			assertThat(snapshot.name()).isEqualTo("Descuento estudiante");
			assertThat(snapshot.discountCents()).isEqualTo(1_000);
		});

		var breakdown = new SalesReportRepository(jdbcClient).salesByBusinessContext(
				organizationId, branchId, now.minusMinutes(1), now.plusMinutes(1));
		assertThat(breakdown).singleElement().satisfies(row -> {
			assertThat(row.businessContext()).isEqualTo("MEMBERSHIP_ENROLLMENT");
			assertThat(row.saleCount()).isEqualTo(1);
			assertThat(row.subtotalCents()).isEqualTo(10_000);
			assertThat(row.discountCents()).isEqualTo(1_000);
			assertThat(row.paidCents()).isEqualTo(9_000);
		});
		assertThat(new SalesReadRepository(jdbcClient).recentSales(organizationId, branchId))
				.singleElement()
				.satisfies(sale -> assertThat(sale.businessContext()).isEqualTo("MEMBERSHIP_ENROLLMENT"));
	}

	@Test
	void membershipEnrollmentPersistsRegistrationFeeAsItsOwnSaleItem() {
		OffsetDateTime now = OffsetDateTime.now();
		UUID benefitId = insertBenefit(
				"STUDENT-FEE", "Descuento estudiante", "STUDENT", "PERCENT", null, 10.0,
				false, true, null, null, null);
		UUID saleId = UUID.randomUUID();

		assertThat(new MembershipSaleCommandRepository(jdbcClient).create(
				membershipSaleRecord(
						saleId, UUID.randomUUID(), "S-REGISTRATION-FEE", "registration-fee-sale",
						benefitId, 1_500, now)))
				.isTrue();

		assertThat(jdbcClient.sql("""
				SELECT item_type
				FROM sale_item
				WHERE sale_id=:saleId
				ORDER BY item_type
				""").param("saleId", saleId).query(String.class).list())
				.containsExactly("MEMBERSHIP_PLAN", "REGISTRATION_FEE");
		assertThat(jdbcClient.sql("""
				SELECT unit_price_cents
				FROM sale_item
				WHERE sale_id=:saleId AND item_type='REGISTRATION_FEE'
				""").param("saleId", saleId).query(Long.class).single())
				.isEqualTo(1_500);
		assertThat(jdbcClient.sql("SELECT subtotal_cents FROM sale WHERE id=:saleId")
				.param("saleId", saleId).query(Long.class).single())
				.isEqualTo(11_500);
		assertThat(jdbcClient.sql("SELECT amount_cents FROM payment WHERE sale_id=:saleId")
				.param("saleId", saleId).query(Long.class).single())
				.isEqualTo(10_500);
	}

	@Test
	void concurrentRequestsWithSameIdempotencyKeyPersistExactlyOneMembershipSale() throws Exception {
		OffsetDateTime now = OffsetDateTime.now();
		UUID benefitId = insertBenefit(
				"STUDENT", "Descuento estudiante", "STUDENT", "PERCENT", null, 10.0,
				false, true, null, null, null);
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		try (var executor = Executors.newFixedThreadPool(2)) {
			var first = executor.submit(() -> createMembershipSaleInTransaction(
					membershipSaleRecord(
							UUID.randomUUID(), UUID.randomUUID(), "S-IDEMPOTENT-1",
							"same-membership-operation", benefitId, now),
					ready,
					start
			));
			var second = executor.submit(() -> createMembershipSaleInTransaction(
					membershipSaleRecord(
							UUID.randomUUID(), UUID.randomUUID(), "S-IDEMPOTENT-2",
							"same-membership-operation", benefitId, now),
					ready,
					start
			));
			ready.await();
			start.countDown();

			assertThat(List.of(first.get(), second.get())).containsExactlyInAnyOrder(true, false);
		}

		assertThat(count("sale", "idempotency_key", "same-membership-operation")).isEqualTo(1);
		assertThat(countBySale("sale_item")).isEqualTo(1);
		assertThat(countBySale("payment")).isEqualTo(1);
		assertThat(countBySale("membership_sale_benefit")).isEqualTo(1);
	}

	private Void renewInTransaction(
			UUID membershipId,
			UUID saleId,
			String eventKey,
			CountDownLatch ready,
			CountDownLatch start
	) {
		ready.countDown();
		try {
			start.await();
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(exception);
		}
		new TransactionTemplate(new DataSourceTransactionManager(dataSource)).executeWithoutResult(status -> {
			MembershipRepository transactionalMemberships = repositoryFor(dataSource);
			var current = transactionalMemberships
					.byIdForUpdate(organizationId, branchId, membershipId)
					.orElseThrow();
			LocalDate periodStart = current.endsOn().plusDays(1);
			LocalDate periodEnd = periodStart.plusDays(29);
			assertThat(transactionalMemberships.updateMembershipForRenewal(
					organizationId,
					membershipId,
					saleId,
					current.startsOn(),
					periodEnd,
					periodEnd.plusDays(3),
					eventKey
			)).isEqualTo(1);
			transactionalMemberships.insertPeriod(
					organizationId,
					branchId,
					membershipId,
					clientId,
					firstPlanId,
					saleId,
					"RENEWAL",
					periodStart,
					periodEnd,
					eventKey
			);
		});
		return null;
	}

	private boolean createMembershipSaleInTransaction(
			MembershipSaleRecord record,
			CountDownLatch ready,
			CountDownLatch start
	) {
		ready.countDown();
		try {
			start.await();
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(exception);
		}
		return new TransactionTemplate(new DataSourceTransactionManager(dataSource)).execute(status ->
				new MembershipSaleCommandRepository(JdbcClient.create(dataSource)).create(record));
	}

	private MembershipSaleRecord membershipSaleRecord(
			UUID saleId,
			UUID paymentId,
			String saleNumber,
			String idempotencyKey,
			UUID benefitId,
			OffsetDateTime now
	) {
		return membershipSaleRecord(
				saleId, paymentId, saleNumber, idempotencyKey, benefitId, 0, now);
	}

	private MembershipSaleRecord membershipSaleRecord(
			UUID saleId,
			UUID paymentId,
			String saleNumber,
			String idempotencyKey,
			UUID benefitId,
			long registrationFeeCents,
			OffsetDateTime now
	) {
		return new MembershipSaleRecord(
				saleId,
				paymentId,
				organizationId,
				branchId,
				clientId,
				UUID.randomUUID(),
				null,
				firstPlanId,
				firstProductId,
				"Plan mensual",
				saleNumber,
				"PAID",
				"CASH",
				"COMPLETED",
				10_000,
				registrationFeeCents,
				registrationFeeCents > 0 ? "Inscripción" : null,
				10_000 + registrationFeeCents,
				1_000,
				9_000 + registrationFeeCents,
				9_000 + registrationFeeCents,
				0,
				"MXN",
				List.of(new MembershipBenefitSnapshot(
						benefitId, "STUDENT", "Descuento estudiante", 1_000)),
				null,
				"Venta con beneficio",
				idempotencyKey,
				"sale-hash",
				idempotencyKey + ":payment",
				"payment-hash",
				"MEMBERSHIP_ENROLLMENT",
				now,
				now,
				null
		);
	}

	private long count(String table, String column, String value) {
		return jdbcClient.sql("SELECT COUNT(*) FROM " + table + " WHERE " + column + "=:value")
				.param("value", value)
				.query(Long.class)
				.single();
	}

	private long countBySale(String table) {
		return jdbcClient.sql("""
				SELECT COUNT(*)
				FROM %s child
				JOIN sale sale ON sale.id=child.sale_id
				WHERE sale.organization_id=:organizationId
				  AND sale.idempotency_key='same-membership-operation'
				""".formatted(table))
				.param("organizationId", organizationId)
				.query(Long.class)
				.single();
	}

	private MembershipRepository repositoryFor(DataSource source) {
		JdbcClient client = JdbcClient.create(source);
		return new MembershipRepository(
				new MembershipClientRepository(client),
				new MembershipQueryRepository(client),
				new MembershipPlanLookupRepository(client),
				new MembershipEventRepository(client),
				new MembershipCommandRepository(client)
		);
	}

	private void seedTenant() {
		jdbcClient.sql("""
				INSERT INTO organization (
				    id, legal_name, trade_name, status, timezone, default_currency,
				    created_at, updated_at, version
				)
				VALUES (:id, 'Gym Test', 'Gym Test', 'ACTIVE', 'America/Mexico_City', 'MXN',
				        now(), now(), 0)
				""")
				.param("id", organizationId)
				.update();
		jdbcClient.sql("""
				INSERT INTO branch (
				    id, organization_id, name, code, timezone, status,
				    created_at, updated_at, version
				)
				VALUES (:id, :organizationId, 'Centro', 'CENTRO', 'America/Mexico_City', 'ACTIVE',
				        now(), now(), 0)
				""")
				.param("id", branchId)
				.param("organizationId", organizationId)
				.update();
		UUID personId = UUID.randomUUID();
		jdbcClient.sql("""
				INSERT INTO person (
				    id, organization_id, first_name, last_name, status,
				    created_at, updated_at, version
				)
				VALUES (:id, :organizationId, 'Socio', 'Prueba', 'ACTIVE', now(), now(), 0)
				""")
				.param("id", personId)
				.param("organizationId", organizationId)
				.update();
		jdbcClient.sql("""
				INSERT INTO client (
				    id, organization_id, person_id, home_branch_id, status, joined_at,
				    created_at, updated_at, version
				)
				VALUES (:id, :organizationId, :personId, :branchId, 'ACTIVE', now(), now(), now(), 0)
				""")
				.param("id", clientId)
				.param("organizationId", organizationId)
				.param("personId", personId)
				.param("branchId", branchId)
				.update();
	}

	private void insertPlan(UUID planId, UUID productId, String sku, String name) {
		jdbcClient.sql("""
				INSERT INTO catalog_product (
				    id, organization_id, sku, name, product_type, commercial_subtype,
				    price_cents, currency, taxable, status, created_at, updated_at, version
				)
				VALUES (
				    :id, :organizationId, :sku, :name, 'MEMBERSHIP_PLAN', 'MEMBERSHIP',
				    10000, 'MXN', false, 'ACTIVE', now(), now(), 0
				)
				""")
				.param("id", productId)
				.param("organizationId", organizationId)
				.param("sku", sku)
				.param("name", name)
				.update();
		jdbcClient.sql("""
				INSERT INTO catalog_product_branch (
				    organization_id, branch_id, product_id, created_at
				)
				VALUES (:organizationId, :branchId, :productId, now())
				""")
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("productId", productId)
				.update();
		jdbcClient.sql("""
				INSERT INTO membership_plan (
				    id, organization_id, product_id, duration_days, grace_days,
				    multi_branch_access, status, created_at, updated_at, version
				)
				VALUES (
				    :id, :organizationId, :productId, 30, 3,
				    false, 'ACTIVE', now(), now(), 0
				)
				""")
				.param("id", planId)
				.param("organizationId", organizationId)
				.param("productId", productId)
				.update();
	}

	private UUID insertSale(
			String saleNumber,
			String businessContext,
			long subtotalCents,
			long discountCents
	) {
		UUID saleId = UUID.randomUUID();
		long totalCents = subtotalCents - discountCents;
		jdbcClient.sql("""
				INSERT INTO sale (
				    id, organization_id, branch_id, client_id, sale_number, status,
				    subtotal_cents, discount_cents, total_cents, paid_cents, balance_cents,
				    currency, sold_at, created_at, updated_at, version, business_context
				)
				VALUES (
				    :id, :organizationId, :branchId, :clientId, :saleNumber, 'PAID',
				    :subtotalCents, :discountCents, :totalCents, :totalCents, 0,
				    'MXN', now(), now(), now(), 0, :businessContext
				)
				""")
				.param("id", saleId)
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("clientId", clientId)
				.param("saleNumber", saleNumber)
				.param("subtotalCents", subtotalCents)
				.param("discountCents", discountCents)
				.param("totalCents", totalCents)
				.param("businessContext", businessContext)
				.update();
		return saleId;
	}

	private void insertMembership(
			UUID membershipId,
			UUID planId,
			UUID saleId,
			LocalDate startsOn,
			LocalDate endsOn,
			LocalDate graceEndsOn
	) {
		jdbcClient.sql("""
				INSERT INTO membership (
				    id, organization_id, branch_id, client_id, plan_id, sale_id, status,
				    activated_at, starts_on, ends_on, grace_ends_on,
				    created_at, updated_at, version
				)
				VALUES (
				    :id, :organizationId, :branchId, :clientId, :planId, :saleId, 'ACTIVE',
				    now(), :startsOn, :endsOn, :graceEndsOn, now(), now(), 0
				)
				""")
				.param("id", membershipId)
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("clientId", clientId)
				.param("planId", planId)
				.param("saleId", saleId)
				.param("startsOn", startsOn)
				.param("endsOn", endsOn)
				.param("graceEndsOn", graceEndsOn)
				.update();
	}

	private UUID insertBenefit(
			String code,
			String name,
			String benefitType,
			String discountType,
			Integer discountValueCents,
			Double discountPercent,
			boolean requiresEvidence,
			boolean combinable,
			UUID scopedBranchId,
			LocalDate validFrom,
			LocalDate validUntil
	) {
		UUID benefitId = UUID.randomUUID();
		jdbcClient.sql("""
				INSERT INTO benefit (
				    id, organization_id, branch_id, code, name, benefit_type, discount_type,
				    discount_value_cents, discount_percent, requires_evidence, combinable,
				    priority, valid_from, valid_until, status, created_at, updated_at, version
				)
				VALUES (
				    :id, :organizationId, :branchId, :code, :name, :benefitType, :discountType,
				    :discountValueCents, :discountPercent, :requiresEvidence, :combinable,
				    10, :validFrom, :validUntil, 'ACTIVE', now(), now(), 0
				)
				""")
				.param("id", benefitId)
				.param("organizationId", organizationId)
				.param("branchId", scopedBranchId, Types.OTHER)
				.param("code", code)
				.param("name", name)
				.param("benefitType", benefitType)
				.param("discountType", discountType)
				.param("discountValueCents", discountValueCents, Types.INTEGER)
				.param("discountPercent", discountPercent, Types.NUMERIC)
				.param("requiresEvidence", requiresEvidence)
				.param("combinable", combinable)
				.param("validFrom", validFrom, Types.DATE)
				.param("validUntil", validUntil, Types.DATE)
				.update();
		return benefitId;
	}

	private void assignBenefit(
			UUID benefitId,
			String status,
			LocalDate validFrom,
			LocalDate validUntil
	) {
		jdbcClient.sql("""
				INSERT INTO client_benefit (
				    id, organization_id, client_id, benefit_id, status, assigned_at,
				    valid_from, valid_until, created_at, updated_at, version
				)
				VALUES (
				    :id, :organizationId, :clientId, :benefitId, :status, now(),
				    :validFrom, :validUntil, now(), now(), 0
				)
				""")
				.param("id", UUID.randomUUID())
				.param("organizationId", organizationId)
				.param("clientId", clientId)
				.param("benefitId", benefitId)
				.param("status", status)
				.param("validFrom", validFrom, Types.DATE)
				.param("validUntil", validUntil, Types.DATE)
				.update();
	}

	private void linkAccessBenefit(UUID benefitId, UUID planId) {
		jdbcClient.sql("""
				INSERT INTO benefit_membership_plan (
				    benefit_id, membership_plan_id, organization_id, created_at
				)
				VALUES (:benefitId, :planId, :organizationId, now())
				""")
				.param("benefitId", benefitId)
				.param("planId", planId)
				.param("organizationId", organizationId)
				.update();
	}
}
