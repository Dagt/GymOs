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
	void selfServiceVerificationDoesNotRequireMembershipAndKeepsBranchScope() {
		UUID clientId = client(organizationId, branchId, "Socio sin plan", now);

		assertThat(repository.verifyForSelfService(
				organizationId, branchId, clientId + "@example.test", LocalDate.of(1990, 1, 1)))
				.map(ClientRepository.SelfServiceClientRecord::id)
				.contains(clientId);
		assertThat(repository.verifyForSelfService(
				organizationId, otherBranchId, clientId + "@example.test", LocalDate.of(1990, 1, 1)))
				.isEmpty();
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
}
