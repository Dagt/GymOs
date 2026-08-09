package com.gymos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymos.coaching.api.operations.CoachProfileProvisioningAdapter;
import com.gymos.coaching.infrastructure.CoachingCatalogRepository;
import com.gymos.coaching.infrastructure.CoachingRepository;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

@Tag("postgres-integration")
@Testcontainers(disabledWithoutDocker = true)
class DatabaseMigrationTests {
	@Container
	static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");

	@BeforeAll
	static void migrate() {
		Flyway.configure()
				.dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
				.locations("classpath:db/migration")
				.load()
				.migrate();
	}

	@Test
	void flywayAddsArchitecturalStabilizationColumns() throws Exception {
		try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
			assertThat(columnExists(connection, "sale", "idempotency_key")).isTrue();
			assertThat(columnExists(connection, "sale", "payment_completed_at")).isTrue();
			assertThat(columnExists(connection, "payment", "idempotency_key")).isTrue();
			assertThat(columnExists(connection, "self_service_session", "session_token_hash")).isTrue();
			assertThat(columnExists(connection, "self_service_session", "verification_attempts")).isTrue();
			assertThat(columnExists(connection, "membership", "source_payment_event_key")).isTrue();
			assertThat(columnExists(connection, "sale", "idempotency_request_hash")).isTrue();
			assertThat(columnExists(connection, "payment", "idempotency_request_hash")).isTrue();
			assertThat(tableExists(connection, "processed_domain_event")).isTrue();
			assertThat(tableExists(connection, "access_device")).isTrue();
			assertThat(tableExists(connection, "inventory_stock")).isTrue();
			assertThat(tableExists(connection, "resource")).isTrue();
			assertThat(tableExists(connection, "booking")).isTrue();
			assertThat(indexExists(connection, "ux_inventory_movement_sale_product_effect")).isTrue();
			assertThat(tableExists(connection, "coach")).isTrue();
			assertThat(tableExists(connection, "workout")).isTrue();
			assertThat(tableExists(connection, "workout_version")).isTrue();
			assertThat(tableExists(connection, "workout_assignment")).isTrue();
			assertThat(tableExists(connection, "exercise")).isTrue();
			assertThat(tableExists(connection, "exercise_alias")).isTrue();
			assertThat(tableExists(connection, "workout_session")).isTrue();
			assertThat(tableExists(connection, "workout_session_block")).isTrue();
			assertThat(tableExists(connection, "workout_exercise")).isTrue();
			assertThat(columnExists(connection, "workout_assignment", "idempotency_key")).isTrue();
			assertThat(indexExists(connection, "ux_workout_assignment_idempotency")).isTrue();
			assertThat(roleHasPermission(connection, "Coach", "exercise.read")).isTrue();
			assertThat(roleHasPermission(connection, "Coach", "workout.assign")).isTrue();
			assertThat(roleHasPermission(connection, "Coach", "workout.manage.own")).isTrue();
			assertThat(roleHasPermission(connection, "Dueno del gimnasio", "coach.metrics.read")).isTrue();
			assertThat(roleHasPermission(connection, "Dueno del gimnasio", "coach.profile.manage")).isTrue();
			assertThat(roleHasPermission(connection, "Dueno del gimnasio", "workout.read.all")).isFalse();
			assertThat(roleHasPermission(connection, "Dueno del gimnasio", "workout.manage.all")).isFalse();
			assertThat(roleHasPermission(connection, "Dueno del gimnasio", "workout.assign")).isFalse();
			assertThat(roleHasPermission(connection, "Dueno del gimnasio", "exercise.read")).isFalse();
			assertThat(roleHasPermission(connection, "Gerente", "coach.metrics.read")).isTrue();
			assertThat(roleHasPermission(connection, "Gerente", "coach.profile.manage")).isTrue();
			assertThat(roleHasPermission(connection, "Gerente", "workout.read.all")).isFalse();
			assertThat(roleHasPermission(connection, "Gerente", "workout.manage.all")).isFalse();
			assertThat(roleHasPermission(connection, "Gerente", "workout.assign")).isFalse();
			assertThat(roleHasPermission(connection, "Gerente", "exercise.read")).isFalse();
			assertThat(tableExists(connection, "professional")).isTrue();
			assertThat(tableExists(connection, "professional_service")).isTrue();
			assertThat(tableExists(connection, "professional_availability")).isTrue();
			assertThat(tableExists(connection, "notification_log")).isTrue();
			assertThat(tableExists(connection, "automation_rule")).isTrue();
			assertThat(tableExists(connection, "automation_execution")).isTrue();
			assertThat(tableExists(connection, "client_import_batch")).isTrue();
			assertThat(tableExists(connection, "client_import_row")).isTrue();
			assertThat(tableExists(connection, "benefit")).isTrue();
			assertThat(tableExists(connection, "client_benefit")).isTrue();
			assertThat(tableExists(connection, "benefit_evidence")).isTrue();
			assertThat(tableExists(connection, "benefit_membership_plan")).isTrue();
			assertThat(tableExists(connection, "access_device_benefit")).isTrue();
			assertThat(tableExists(connection, "membership_period")).isTrue();
			assertThat(tableExists(connection, "membership_sale_benefit")).isTrue();
			assertThat(indexExists(connection, "ux_membership_single_active_client")).isTrue();
			assertThat(indexExists(connection, "ux_membership_period_event")).isTrue();
			assertThat(indexExists(connection, "ix_membership_sale_benefit_sale")).isTrue();
			assertThat(indexExists(connection, "ux_benefit_org_branch_code")).isTrue();
			assertThat(indexExists(connection, "ux_benefit_org_global_code")).isTrue();
			assertThat(tableExists(connection, "visit_pass")).isTrue();
			assertThat(tableExists(connection, "sale_refund")).isTrue();
			assertThat(tableExists(connection, "booking_payment")).isTrue();
			assertThat(tableExists(connection, "booking_policy")).isTrue();
			assertThat(tableExists(connection, "auto_renewal_attempt")).isTrue();
			assertThat(tableExists(connection, "notification_template")).isTrue();
			assertThat(tableExists(connection, "resource_allowed_membership_plan")).isTrue();
			assertThat(columnExists(connection, "client", "home_branch_id")).isTrue();
			assertThat(columnExists(connection, "membership", "auto_renew")).isTrue();
			assertThat(columnExists(connection, "membership", "cancelled_at")).isTrue();
			assertThat(columnExists(connection, "membership_plan", "multi_branch_access")).isTrue();
			assertThat(columnExists(connection, "automation_rule", "trigger_type")).isTrue();
			assertThat(columnExists(connection, "automation_rule", "next_run_at")).isTrue();
			assertThat(columnExists(connection, "branch", "brand_name")).isFalse();
			assertThat(columnExists(connection, "branch", "logo_url")).isFalse();
			assertThat(columnExists(connection, "organization", "logo_object_key")).isTrue();
			assertThat(roleHasPermission(connection, "Dueno del gimnasio", "organization.read")).isTrue();
			assertThat(roleHasPermission(connection, "Dueno del gimnasio", "organization.update")).isTrue();
			assertThat(roleHasPermission(connection, "Gerente", "client.read")).isTrue();
			assertThat(roleHasPermission(connection, "Gerente", "client.create")).isTrue();
			assertThat(roleHasPermission(connection, "Gerente", "client.update")).isTrue();
			assertThat(roleHasPermission(connection, "Gerente", "payment.settings.manage")).isTrue();
			assertThat(roleHasPermission(connection, "Gerente", "selfservice.settings.manage")).isTrue();
			assertThat(roleHasPermission(connection, "Gerente", "notification.settings.manage")).isTrue();
			assertThat(roleHasPermission(connection, "Recepcion", "client.read")).isTrue();
			assertThat(roleHasPermission(connection, "Recepcion", "client.create")).isTrue();
			assertThat(roleHasPermission(connection, "Recepcion", "client.update")).isTrue();
			assertThat(columnExists(connection, "branch", "primary_color")).isFalse();
			assertThat(columnExists(connection, "branch", "accent_color")).isFalse();
			assertThat(columnExists(connection, "branch", "surface_color")).isFalse();
			assertThat(roleHasPermission(connection, "Recepcion", "resource.manage")).isFalse();
			assertThat(roleHasPermission(connection, "Recepcion", "booking.manage")).isTrue();
		}
	}

	@Test
	void saleIdempotencyKeyIsUniqueWithinOrganization() throws Exception {
		try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
			UUID organizationId = UUID.randomUUID();
			UUID branchId = UUID.randomUUID();
			insertOrganization(connection, organizationId);
			insertBranch(connection, organizationId, branchId);
			insertSale(connection, UUID.randomUUID(), organizationId, branchId, "S-1", "repeat-key");

			assertThatThrownBy(() -> insertSale(connection, UUID.randomUUID(), organizationId, branchId, "S-2", "repeat-key"))
					.isInstanceOf(SQLException.class);
		}
	}

	@Test
	void coachingQueriesAcceptNullUuidFiltersOnPostgresql() {
		var dataSource = new DriverManagerDataSource(
				postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()
		);
		var jdbcClient = JdbcClient.create(dataSource);
		var coaching = new CoachingRepository(jdbcClient);
		var catalog = new CoachingCatalogRepository(jdbcClient);
		UUID organizationId = UUID.randomUUID();
		UUID branchId = UUID.randomUUID();
		LocalDate from = LocalDate.of(2026, 7, 1);
		LocalDate to = LocalDate.of(2026, 7, 31);

		assertThatCode(() -> {
			coaching.workouts(organizationId, branchId, null);
			catalog.assignmentStats(organizationId, branchId, from, to, null, null, null);
			catalog.assignments(organizationId, branchId, from, to, null, null, null, 25, 0);
			catalog.metrics(organizationId, branchId, from, to, null, null, null);
			catalog.weeklyTrend(organizationId, branchId, from, to, null, null, null);
			catalog.exerciseEquipmentOptions(organizationId);
		}).doesNotThrowAnyException();
		assertThat(catalog.exerciseEquipmentOptions(organizationId)).contains("Barra");
	}

	@Test
	void coachProvisioningPersistsCreationDetailsOnPostgresql() throws Exception {
		try (var connection = DriverManager.getConnection(
				postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()
		)) {
			UUID organizationId = UUID.randomUUID();
			UUID branchId = UUID.randomUUID();
			UUID userId = UUID.randomUUID();
			insertOrganization(connection, organizationId);
			insertBranch(connection, organizationId, branchId);
			insertUser(connection, organizationId, userId);
			var dataSource = new SingleConnectionDataSource(connection, true);
			var adapter = new CoachProfileProvisioningAdapter(
					JdbcClient.create(dataSource), new ObjectMapper()
			);

			adapter.synchronize(
					organizationId, userId, "Ana Coach", "ana@example.com", "+521234567890",
					List.of(branchId), List.of("Fuerza", "Movilidad"),
					"Entrenadora funcional.", true, userId
			);

			try (var statement = connection.prepareStatement("""
					SELECT specialties_json::text, bio
					FROM coach
					WHERE organization_id = ? AND branch_id = ? AND user_account_id = ?
					""")) {
				statement.setObject(1, organizationId);
				statement.setObject(2, branchId);
				statement.setObject(3, userId);
				try (var resultSet = statement.executeQuery()) {
					assertThat(resultSet.next()).isTrue();
					assertThat(resultSet.getString("specialties_json"))
							.contains("Fuerza", "Movilidad");
					assertThat(resultSet.getString("bio")).isEqualTo("Entrenadora funcional.");
				}
			}
		}
	}

	private boolean columnExists(java.sql.Connection connection, String table, String column) throws Exception {
		try (var statement = connection.prepareStatement("""
				SELECT 1
				FROM information_schema.columns
				WHERE table_schema = 'public'
				  AND table_name = ?
				  AND column_name = ?
				""")) {
			statement.setString(1, table);
			statement.setString(2, column);
			try (var resultSet = statement.executeQuery()) {
				return resultSet.next();
			}
		}
	}

	private boolean tableExists(java.sql.Connection connection, String table) throws Exception {
		try (var statement = connection.prepareStatement("""
				SELECT 1
				FROM information_schema.tables
				WHERE table_schema = 'public'
				  AND table_name = ?
				""")) {
			statement.setString(1, table);
			try (var resultSet = statement.executeQuery()) {
				return resultSet.next();
			}
		}
	}

	private boolean indexExists(java.sql.Connection connection, String indexName) throws Exception {
		try (var statement = connection.prepareStatement("""
				SELECT 1
				FROM pg_indexes
				WHERE schemaname = 'public'
				  AND indexname = ?
				""")) {
			statement.setString(1, indexName);
			try (var resultSet = statement.executeQuery()) {
				return resultSet.next();
			}
		}
	}

	private boolean roleHasPermission(java.sql.Connection connection, String roleName, String permissionCode) throws Exception {
		try (var statement = connection.prepareStatement("""
				SELECT 1
				FROM role r
				JOIN role_permission rp ON rp.role_id = r.id
				JOIN permission p ON p.id = rp.permission_id
				WHERE r.name = ?
				  AND p.code = ?
				""")) {
			statement.setString(1, roleName);
			statement.setString(2, permissionCode);
			try (var resultSet = statement.executeQuery()) {
				return resultSet.next();
			}
		}
	}

	private void insertOrganization(java.sql.Connection connection, UUID organizationId) throws Exception {
		try (var statement = connection.prepareStatement("""
				INSERT INTO organization (
				    id, legal_name, trade_name, status, timezone, default_currency, created_at, updated_at, version
				)
				VALUES (?, 'Test Gym Legal', 'Test Gym', 'ACTIVE', 'America/Mexico_City', 'MXN', now(), now(), 0)
				""")) {
			statement.setObject(1, organizationId);
			statement.executeUpdate();
		}
	}

	private void insertBranch(java.sql.Connection connection, UUID organizationId, UUID branchId) throws Exception {
		try (var statement = connection.prepareStatement("""
				INSERT INTO branch (
				    id, organization_id, name, code, timezone, status, created_at, updated_at, version
				)
				VALUES (?, ?, 'Main', 'MAIN', 'America/Mexico_City', 'ACTIVE', now(), now(), 0)
				""")) {
			statement.setObject(1, branchId);
			statement.setObject(2, organizationId);
			statement.executeUpdate();
		}
	}

	private void insertUser(
			java.sql.Connection connection, UUID organizationId, UUID userId
	) throws Exception {
		UUID personId = UUID.randomUUID();
		try (var person = connection.prepareStatement("""
				INSERT INTO person (
				    id, organization_id, first_name, last_name, email, phone, status,
				    created_at, updated_at, version
				)
				VALUES (?, ?, 'Ana', 'Coach', ?, ?, 'ACTIVE', now(), now(), 0)
				""")) {
			person.setObject(1, personId);
			person.setObject(2, organizationId);
			person.setString(3, "ana-" + userId + "@example.com");
			person.setString(4, "+52" + String.format("%010d", Math.abs(userId.hashCode())));
			person.executeUpdate();
		}
		try (var user = connection.prepareStatement("""
				INSERT INTO user_account (
				    id, organization_id, person_id, username, password_hash, status,
				    failed_attempts, created_at, updated_at, version
				)
				VALUES (?, ?, ?, ?, 'encoded', 'ACTIVE', 0, now(), now(), 0)
				""")) {
			user.setObject(1, userId);
			user.setObject(2, organizationId);
			user.setObject(3, personId);
			user.setString(4, "ana-" + userId);
			user.executeUpdate();
		}
	}

	private void insertSale(
			java.sql.Connection connection,
			UUID saleId,
			UUID organizationId,
			UUID branchId,
			String saleNumber,
			String idempotencyKey
	) throws Exception {
		try (var statement = connection.prepareStatement("""
				INSERT INTO sale (
				    id, organization_id, branch_id, sale_number, status, subtotal_cents,
				    discount_cents, total_cents, paid_cents, balance_cents, currency,
				    sold_at, created_at, updated_at, version, idempotency_key
				)
				VALUES (?, ?, ?, ?, 'PENDING_PAYMENT', 1000, 0, 1000, 0, 1000, 'MXN', now(), now(), now(), 0, ?)
				""")) {
			statement.setObject(1, saleId);
			statement.setObject(2, organizationId);
			statement.setObject(3, branchId);
			statement.setString(4, saleNumber);
			statement.setString(5, idempotencyKey);
			statement.executeUpdate();
		}
	}
}
