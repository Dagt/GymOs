package com.gymos.clients.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.gymos.clients.infrastructure.ClientCommandRepository.CreateClientRecord;
import com.gymos.clients.infrastructure.ClientRepository.ClientSummary;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
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
class ClientPaginationIntegrationTests {
	@Container
	static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");
	private static JdbcClient jdbc;
	private ClientRepository repository;
	private ClientCommandRepository commands;
	private ClientLifecycleRepository lifecycle;
	private UUID organizationId;
	private UUID branchId;
	private UUID otherBranchId;
	private final OffsetDateTime now = OffsetDateTime.parse("2026-08-25T12:00:00Z");

	@BeforeAll
	static void migrate() {
		var source = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
		Flyway.configure().dataSource(source).locations("classpath:db/migration").load().migrate();
		jdbc = JdbcClient.create(source);
	}

	@BeforeEach
	void setUp() {
		repository = new ClientRepository(jdbc);
		commands = new ClientCommandRepository(jdbc);
		lifecycle = new ClientLifecycleRepository(jdbc);
		organizationId = organization();
		branchId = branch(organizationId, "CENTRO");
		otherBranchId = branch(organizationId, "NORTE");
	}

	@Test
	void directoryCanReachEveryClientBeyondTheLegacyFiftyWithStableOrdering() {
		List<UUID> expected = new ArrayList<>();
		for (int index = 0; index < 127; index++) expected.add(client(organizationId, branchId, "Socio " + index, now));
		List<UUID> visited = new ArrayList<>();
		for (int page = 0; page < 6; page++) {
			var result = repository.searchPage(organizationId, branchId, null, null, page, 25);
			assertThat(result.page()).isEqualTo(page);
			assertThat(result.totalElements()).isEqualTo(127);
			assertThat(result.totalPages()).isEqualTo(6);
			assertThat(result.items()).hasSize(page == 5 ? 2 : 25);
			visited.addAll(result.items().stream().map(ClientSummary::id).toList());
		}
		assertThat(visited).doesNotHaveDuplicates().containsExactlyInAnyOrderElementsOf(expected);
		assertThat(repository.searchPage(organizationId, branchId, null, null, 0, 25).items())
				.extracting(ClientSummary::id).containsExactlyElementsOf(visited.subList(0, 25));
		assertThat(repository.search(organizationId, branchId, null, null, 50)).hasSize(50);
		for (int size : List.of(25, 50, 100)) {
			var firstPage = repository.searchPage(organizationId, branchId, null, null, 0, size);
			assertThat(firstPage.items()).hasSize(size);
			assertThat(firstPage.totalElements()).isEqualTo(127);
			assertThat(firstPage.totalPages()).isEqualTo((int) Math.ceil(127.0 / size));
		}
		var beyondEnd = repository.searchPage(organizationId, branchId, null, null, 9, 25);
		assertThat(beyondEnd.items()).isEmpty();
		assertThat(beyondEnd.totalElements()).isEqualTo(127);
	}

	@Test
	void searchAndStatusCountAllMatchesIncludingTheOldestRecord() {
		for (int index = 0; index < 110; index++) client(organizationId, branchId, "Reciente " + index, now);
		UUID oldest = client(organizationId, branchId, "Antigua única", now.minusYears(1));
		jdbc.sql("UPDATE client SET status='INACTIVE' WHERE id=:id").param("id", oldest).update();
		assertThat(repository.search(organizationId, branchId, null, null, 50)).extracting(ClientSummary::id).doesNotContain(oldest);
		var byName = repository.searchPage(organizationId, branchId, "  ANTIGUA ÚNICA ", " inactive ", 0, 25);
		assertThat(byName.totalElements()).isEqualTo(1);
		assertThat(byName.items()).extracting(ClientSummary::id).containsExactly(oldest);
		assertThat(repository.searchPage(organizationId, branchId, oldest + "@example.test", null, 0, 25).totalElements()).isEqualTo(1);
		assertThat(repository.searchPage(organizationId, branchId, "Antigua", "ACTIVE", 0, 25).totalElements()).isZero();
	}

	@Test
	void itemsAndTotalsPreserveTenantBranchAndSoftDeleteVisibility() {
		UUID visible = client(organizationId, branchId, "Visible", now);
		UUID unassigned = client(organizationId, null, "Sin sucursal", now);
		client(organizationId, otherBranchId, "Otra sucursal", now);
		UUID foreignOrg = organization();
		client(foreignOrg, branch(foreignOrg, "EXTERNA"), "Otro gimnasio", now);
		UUID deletedClient = client(organizationId, branchId, "Eliminado", now);
		UUID deletedPerson = client(organizationId, branchId, "Persona eliminada", now);
		jdbc.sql("UPDATE client SET deleted_at=now() WHERE id=:id").param("id", deletedClient).update();
		jdbc.sql("UPDATE person SET deleted_at=now() WHERE id=(SELECT person_id FROM client WHERE id=:id)").param("id", deletedPerson).update();
		var page = repository.searchPage(organizationId, branchId, null, null, 0, 25);
		assertThat(page.totalElements()).isEqualTo(2);
		assertThat(page.items()).extracting(ClientSummary::id).containsExactlyInAnyOrder(visible, unassigned);
		assertThat(repository.searchPage(foreignOrg, branchId, null, null, 0, 25).totalElements()).isZero();
	}

	@Test
	void normalizesPageBoundsWithoutOverflowingOffset() {
		client(organizationId, branchId, "Único", now);
		assertThat(repository.searchPage(organizationId, branchId, null, null, -1, 0).size()).isEqualTo(1);
		var bounded = repository.searchPage(organizationId, branchId, null, null, 0, 10000);
		assertThat(bounded.size()).isEqualTo(100);
		var distant = repository.searchPage(organizationId, branchId, null, null, Integer.MAX_VALUE, 100);
		assertThat(distant.items()).isEmpty();
		assertThat(distant.totalElements()).isEqualTo(1);
	}

	@Test
	void selfServiceVerificationDoesNotRequireMembershipOrHideAnOrganizationClientByHomeBranch() {
		UUID clientId = client(organizationId, branchId, "Socio sin plan", now);

		assertThat(repository.verifyForSelfService(
				organizationId, branchId, clientId + "@example.test", LocalDate.of(1990, 1, 1)))
				.map(ClientRepository.SelfServiceClientRecord::id)
				.contains(clientId);
		assertThat(repository.verifyForSelfService(
				organizationId, otherBranchId, clientId + "@example.test", LocalDate.of(1990, 1, 1)))
				.map(ClientRepository.SelfServiceClientRecord::id)
				.contains(clientId);
	}

	@Test
	void automaticInactivityUsesJoinDateWhenClientNeverHadMembership() {
		UUID oldClient = client(organizationId, branchId, "Socio antiguo", now.minusMonths(7));
		jdbc.sql("""
				INSERT INTO client_lifecycle_configuration (
				    organization_id, auto_inactivation_enabled, inactivity_months
				) VALUES (:organizationId, true, 6)
				""").param("organizationId", organizationId).update();

		var candidates = lifecycle.automaticInactivityCandidates(LocalDate.of(2026, 8, 25), 100);

		assertThat(candidates).extracting(ClientLifecycleRepository.ClientInactivityCandidate::clientId)
				.contains(oldClient);
		assertThat(lifecycle.markAutomaticallyInactive(organizationId, oldClient, now)).isTrue();
		assertThat(jdbc.sql("SELECT status FROM client WHERE id=:id").param("id", oldClient)
				.query(String.class).single()).isEqualTo("INACTIVE");
		assertThat(jdbc.sql("SELECT inactivation_reason FROM client WHERE id=:id").param("id", oldClient)
				.query(String.class).single()).isEqualTo("MEMBERSHIP_INACTIVITY");
	}

	@Test
	void automaticInactivityUsesTheExactCanonicalThresholdAndHonorsItsOwnToggle() {
		UUID exactThresholdClient = client(
				organizationId, branchId, "Socio en umbral", now.minusMonths(6));
		jdbc.sql("""
				INSERT INTO client_lifecycle_configuration (
				    organization_id, registration_fee_enabled,
				    auto_inactivation_enabled, inactivity_months
				) VALUES (:organizationId, true, false, 6)
				""").param("organizationId", organizationId).update();

		assertThat(lifecycle.automaticInactivityCandidates(LocalDate.of(2026, 8, 25), 100))
				.extracting(ClientLifecycleRepository.ClientInactivityCandidate::clientId)
				.doesNotContain(exactThresholdClient);

		jdbc.sql("""
				UPDATE client_lifecycle_configuration
				SET auto_inactivation_enabled = true
				WHERE organization_id = :organizationId
				""").param("organizationId", organizationId).update();

		assertThat(lifecycle.automaticInactivityCandidates(LocalDate.of(2026, 8, 25), 100))
				.extracting(ClientLifecycleRepository.ClientInactivityCandidate::clientId)
				.contains(exactThresholdClient);
	}

	@Test
	void activeVisitPackagePreventsAutomaticInactivityAndBecomesTheLatestActivity() {
		UUID clientId = client(organizationId, branchId, "Socio con visitas", now.minusMonths(9));
		LocalDate validUntil = LocalDate.of(2026, 9, 30);
		activeVisitPackage(clientId, validUntil);
		jdbc.sql("""
				INSERT INTO client_lifecycle_configuration (
				    organization_id, auto_inactivation_enabled, inactivity_months
				) VALUES (:organizationId, true, 6)
				""").param("organizationId", organizationId).update();

		assertThat(lifecycle.latestVisitPackageEnd(organizationId, clientId)).contains(validUntil);
		assertThat(lifecycle.automaticInactivityCandidates(LocalDate.of(2026, 8, 25), 100))
				.extracting(ClientLifecycleRepository.ClientInactivityCandidate::clientId)
				.doesNotContain(clientId);
	}

	@Test
	void selfServiceReactivationAcceptsAutomaticInactivityButRejectsManualInactivity() {
		UUID automaticallyInactive = client(
				organizationId, branchId, "Baja automática", now.minusMonths(7));
		UUID manuallyInactive = client(
				organizationId, branchId, "Baja manual", now.minusMonths(7));
		jdbc.sql("""
				UPDATE client
				SET status = 'INACTIVE', inactivation_reason = 'MEMBERSHIP_INACTIVITY', inactivated_at = now()
				WHERE id = :id
				""").param("id", automaticallyInactive).update();
		jdbc.sql("""
				UPDATE client
				SET status = 'INACTIVE', inactivation_reason = 'MANUAL', inactivated_at = now()
				WHERE id = :id
				""").param("id", manuallyInactive).update();

		assertThat(commands.reactivateClient(
				organizationId, automaticallyInactive, branchId, now)).isTrue();
		assertThat(commands.reactivateClient(
				organizationId, manuallyInactive, branchId, now)).isFalse();
		assertThat(jdbc.sql("SELECT status FROM client WHERE id = :id")
				.param("id", automaticallyInactive).query(String.class).single()).isEqualTo("ACTIVE");
		assertThat(jdbc.sql("SELECT status FROM client WHERE id = :id")
				.param("id", manuallyInactive).query(String.class).single()).isEqualTo("INACTIVE");
	}

	private UUID client(UUID org, UUID branch, String name, OffsetDateTime timestamp) {
		UUID id = UUID.randomUUID();
		commands.createClient(new CreateClientRecord(org, branch, null, UUID.randomUUID(), id, name, "Prueba", null,
				LocalDate.of(1990, 1, 1), "FEMALE", null, id + "@example.test", null, null, timestamp));
		return id;
	}

	private UUID organization() {
		UUID id = UUID.randomUUID();
		jdbc.sql("INSERT INTO organization (id,legal_name,trade_name,status,timezone,default_currency,created_at,updated_at,version) VALUES (:id,'Pagination','Pagination','ACTIVE','America/Mexico_City','MXN',now(),now(),0)")
				.param("id", id).update();
		return id;
	}

	private UUID branch(UUID organization, String code) {
		UUID id = UUID.randomUUID();
		jdbc.sql("INSERT INTO branch (id,organization_id,name,code,status,timezone,created_at,updated_at,version) VALUES (:id,:org,:code,:code,'ACTIVE','America/Mexico_City',now(),now(),0)")
				.param("id", id).param("org", organization).param("code", code).update();
		return id;
	}

	private void activeVisitPackage(UUID clientId, LocalDate validUntil) {
		UUID productId = UUID.randomUUID();
		UUID planId = UUID.randomUUID();
		UUID saleId = UUID.randomUUID();
		UUID entitlementId = UUID.randomUUID();
		jdbc.sql("""
				INSERT INTO catalog_product (
				    id, organization_id, sku, name, product_type, commercial_subtype,
				    price_cents, currency, taxable, status, created_at, updated_at, version
				) VALUES (
				    :id, :organizationId, :sku, '10 visitas', 'SERVICE', 'VISIT_PASS',
				    90000, 'MXN', true, 'ACTIVE', now(), now(), 0
				)
				""").param("id", productId).param("organizationId", organizationId)
				.param("sku", "VISIT-" + productId).update();
		jdbc.sql("""
				INSERT INTO visit_package_plan (
				    id, organization_id, product_id, total_visits, validity_days, branch_scope,
				    self_service_acquisition_enabled, registration_fee_policy, status,
				    created_at, updated_at, version
				) VALUES (
				    :id, :organizationId, :productId, 10, 45, 'SELECTED_BRANCHES',
				    true, 'DEFAULT', 'ACTIVE', now(), now(), 0
				)
				""").param("id", planId).param("organizationId", organizationId)
				.param("productId", productId).update();
		jdbc.sql("""
				INSERT INTO sale (
				    id, organization_id, branch_id, client_id, sale_number, status,
				    subtotal_cents, discount_cents, total_cents, paid_cents, balance_cents,
				    currency, sold_at, created_at, updated_at, version
				) VALUES (
				    :id, :organizationId, :branchId, :clientId, :number, 'PAID',
				    90000, 0, 90000, 90000, 0, 'MXN', now(), now(), now(), 0
				)
				""").param("id", saleId).param("organizationId", organizationId)
				.param("branchId", branchId).param("clientId", clientId)
				.param("number", "VP-" + saleId.toString().substring(0, 8)).update();
		jdbc.sql("""
				INSERT INTO visit_package_entitlement (
				    id, organization_id, home_branch_id, client_id, visit_package_plan_id,
				    sale_id, status, total_visits, remaining_visits, validity_days,
				    valid_from, valid_until, activated_at, created_at, updated_at, version
				) VALUES (
				    :id, :organizationId, :branchId, :clientId, :planId,
				    :saleId, 'ACTIVE', 10, 10, 45, :validFrom, :validUntil,
				    now(), now(), now(), 0
				)
				""").param("id", entitlementId).param("organizationId", organizationId)
				.param("branchId", branchId).param("clientId", clientId).param("planId", planId)
				.param("saleId", saleId).param("validFrom", validUntil.minusDays(44))
				.param("validUntil", validUntil).update();
	}
}
