package com.gymos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymos.coaching.api.operations.CoachProfileProvisioningAdapter;
import com.gymos.coaching.infrastructure.CoachingCatalogRepository;
import com.gymos.coaching.infrastructure.CoachingRepository;
import com.gymos.platformadmin.infrastructure.PlatformCustomerAccountRepository;
import com.gymos.platformadmin.infrastructure.PlatformCustomerAccountRepository.CustomerAccountWrite;
import com.gymos.platformbilling.infrastructure.PlatformBillingRepository;
import com.gymos.platformbilling.infrastructure.PlatformBillingRepository.BillingPaymentWrite;
import com.gymos.platformbilling.infrastructure.PlatformBillingRepository.BillingUpdateRecord;
import java.math.BigDecimal;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
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
	private static final UUID HISTORICAL_DEMO_ORGANIZATION_ID =
			UUID.fromString("11111111-1111-1111-1111-111111111111");
	private static final UUID HISTORICAL_DEMO_BRANCH_ID =
			UUID.fromString("22222222-2222-2222-2222-222222222222");

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
			assertThat(columnExists(connection, "user_account", "credential_version")).isTrue();
			assertThat(tableExists(connection, "user_password_reset")).isTrue();
			assertThat(indexExists(connection, "ix_workout_assignment_due_completion")).isTrue();
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
			assertThat(tableExists(connection, "platform_branch_billing")).isTrue();
			assertThat(tableExists(connection, "platform_billing_payment")).isTrue();
			assertThat(indexExists(connection, "ix_platform_billing_payment_branch_paid_on")).isTrue();
			assertThat(tableExists(connection, "platform_customer_account")).isTrue();
			assertThat(columnExists(connection, "platform_branch_billing", "version")).isTrue();
			assertThat(columnExists(connection, "platform_billing_payment", "idempotency_key")).isTrue();
			assertThat(columnExists(connection, "user_account", "account_scope")).isTrue();
			assertThat(columnExists(connection, "person", "account_scope")).isTrue();
			assertThat(columnExists(connection, "role", "account_scope")).isTrue();
			assertThat(columnExists(connection, "organization", "data_origin")).isTrue();
			assertThat(tableExists(connection, "user_invitation")).isTrue();
			assertThat(columnExists(connection, "user_invitation", "email_delivery_status")).isTrue();
			assertThat(columnExists(connection, "user_invitation", "email_attempt_count")).isTrue();
			assertThat(columnExists(connection, "user_invitation", "last_email_attempt_at")).isTrue();
			assertThat(columnExists(connection, "user_invitation", "last_email_sent_at")).isTrue();
			assertThat(indexExists(connection, "ix_user_invitation_user_created")).isTrue();
			assertThat(indexExists(connection, "ix_person_login_email_active")).isTrue();
			assertThat(indexExists(connection, "ux_platform_billing_payment_idempotency")).isTrue();
			assertThat(tableExists(connection, "access_provider_connection")).isTrue();
			assertThat(tableExists(connection, "access_provider_benefit_mapping")).isTrue();
			assertThat(tableExists(connection, "access_provider_client_link")).isTrue();
			assertThat(tableExists(connection, "access_provider_sync_state")).isTrue();
			assertThat(indexExists(connection, "ix_access_provider_sync_state_pending")).isTrue();
			assertThat(tableExists(connection, "access_provider_device_link")).isTrue();
			assertThat(tableExists(connection, "access_connector_activation")).isTrue();
			assertThat(tableExists(connection, "access_connector_credential")).isTrue();
			assertThat(tableExists(connection, "access_provider_sync_job")).isTrue();
			assertThat(tableExists(connection, "access_provider_person_candidate")).isTrue();
			assertThat(columnExists(connection, "access_provider_sync_job", "claim_token")).isTrue();
			assertThat(columnExists(connection, "access_provider_sync_state", "desired_version")).isTrue();
			assertThat(columnExists(connection, "access_provider_sync_state", "latest_job_id")).isTrue();
			assertThat(columnExists(connection, "access_log", "external_event_id")).isTrue();
			assertThat(indexExists(connection, "ux_access_provider_connector_key")).isTrue();
			assertThat(indexExists(connection, "ux_access_provider_connection_branch")).isTrue();
			assertThat(indexExists(connection, "ux_access_connector_credential_tenant_identity")).isTrue();
			assertThat(indexExists(connection, "ux_access_provider_sync_job_idempotency")).isTrue();
			assertThat(indexExists(connection, "ux_access_log_external_event")).isTrue();
			assertThat(roleHasPermission(connection, "Dueno del gimnasio", "access.physical.manage")).isTrue();
			assertThat(roleHasPermission(connection, "Gerente", "access.physical.manage")).isTrue();
			assertThat(roleHasPermission(connection, "Recepcion", "access.physical.manage")).isFalse();
			assertThat(roleHasPermission(connection, "Dueno del gimnasio", "access.manual.open")).isTrue();
			assertThat(roleHasPermission(connection, "Gerente", "access.manual.open")).isTrue();
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
			assertThat(columnExists(connection, "client", "inactivated_at")).isTrue();
			assertThat(columnExists(connection, "client", "inactivation_reason")).isTrue();
			assertThat(tableExists(connection, "client_lifecycle_configuration")).isTrue();
			assertThat(columnExists(connection, "client_lifecycle_configuration", "inactivity_months")).isTrue();
			assertThat(columnExists(connection, "client_lifecycle_configuration", "registration_recharge_months")).isFalse();
			assertThat(indexExists(connection, "ix_client_automatic_inactivation")).isTrue();
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
			assertThat(roleHasPermission(connection, "Gerente", "organization.read")).isTrue();
			assertThat(roleHasPermission(connection, "Gerente", "branch.read")).isTrue();
			assertThat(roleHasPermission(connection, "Gerente", "payment.settings.read")).isTrue();
			assertThat(roleHasPermission(connection, "Gerente", "payment.settings.manage")).isTrue();
			assertThat(roleHasPermission(connection, "Gerente", "selfservice.settings.manage")).isTrue();
			assertThat(roleHasPermission(connection, "Gerente", "notification.settings.manage")).isTrue();
			assertThat(roleHasPermission(connection, "Gerente", "benefit.manage")).isTrue();
			assertThat(roleHasPermission(connection, "Gerente", "resource.manage")).isTrue();
			assertThat(roleHasPermission(connection, "Gerente", "selfservice.session.launch")).isFalse();
			assertThat(roleHasPermission(connection, "Dueno del gimnasio", "selfservice.session.launch")).isFalse();
			assertThat(roleHasPermission(connection, "Administrador", "selfservice.session.launch")).isFalse();
			assertThat(roleHasPermission(connection, "Administrador GymOS", "selfservice.session.launch")).isFalse();
			assertThat(roleHasPermission(connection, "Recepcion", "client.read")).isTrue();
			assertThat(roleHasPermission(connection, "Recepcion", "client.create")).isTrue();
			assertThat(roleHasPermission(connection, "Recepcion", "client.update")).isTrue();
			assertThat(roleHasPermission(connection, "Recepcion", "selfservice.session.launch")).isTrue();
			assertThat(columnExists(connection, "branch", "primary_color")).isFalse();
			assertThat(columnExists(connection, "branch", "accent_color")).isFalse();
			assertThat(columnExists(connection, "branch", "surface_color")).isFalse();
			assertThat(roleHasPermission(connection, "Recepcion", "resource.manage")).isFalse();
			assertThat(roleHasPermission(connection, "Recepcion", "booking.manage")).isTrue();
		}
	}

	@Test
	void upgradeFromV93KeepsInactivityMonthsAsCanonicalThreshold() throws Exception {
		String schema = migrationSchema("client_lifecycle_v93");
		migrateSchema(schema, "93");
		UUID organizationId = UUID.randomUUID();
		try (var connection = schemaConnection(schema)) {
			insertOrganization(connection, organizationId);
			try (var statement = connection.prepareStatement("""
					INSERT INTO client_lifecycle_configuration (
					    organization_id, registration_fee_enabled, registration_fee_cents,
					    registration_fee_label, registration_recharge_months,
					    auto_inactivation_enabled, inactivity_months
					) VALUES (?, TRUE, 15000, 'Inscripción', 12, TRUE, 6)
					""")) {
				statement.setObject(1, organizationId);
				statement.executeUpdate();
			}
		}

		migrateSchema(schema, null);

		try (var connection = schemaConnection(schema)) {
			assertThat(columnExists(connection, schema, "client_lifecycle_configuration", "registration_recharge_months")).isFalse();
			assertThat(columnExists(connection, schema, "client_lifecycle_configuration", "inactivity_months")).isTrue();
			try (var statement = connection.prepareStatement("""
					SELECT inactivity_months
					FROM client_lifecycle_configuration
					WHERE organization_id = ?
					""")) {
				statement.setObject(1, organizationId);
				try (var resultSet = statement.executeQuery()) {
					assertThat(resultSet.next()).isTrue();
					assertThat(resultSet.getInt("inactivity_months")).isEqualTo(6);
				}
			}
		}
	}

	@Test
	void upgradeFromV90CorrectsUtcAssignmentDateWithoutChangingPeriodOrRemovingHistory() throws Exception {
		String schema = migrationSchema("operational_hardening");
		migrateSchema(schema, "90");
		UUID assignment = UUID.randomUUID(), intentionallyHistorical = UUID.randomUUID();
		UUID user;
		try (var connection = schemaConnection(schema)) {
			JdbcClient jdbc = JdbcClient.create(new SingleConnectionDataSource(connection, true));
			var data = new com.gymos.support.OperationalTestData(jdbc);
			user = data.ownerId;
			var workout = data.workout(data.coach());
			UUID client = data.client();
			for (UUID id : List.of(assignment, intentionallyHistorical)) {
				jdbc.sql("""
						INSERT INTO workout_assignment(id, organization_id, branch_id, workout_id, workout_version_id, client_id,
						status, assigned_on, starts_on, ends_on, created_at, updated_at)
						VALUES (:id, :org, :branch, :workout, :version, :client, 'ACTIVE', :assigned,
						'2026-08-26', '2026-09-20', '2026-08-26T02:00:00Z', '2026-08-26T02:00:00Z')
						""").param("id", id).param("org", data.organizationId).param("branch", data.branchId)
						.param("workout", workout.id()).param("version", workout.versionId()).param("client", client)
						.param("assigned", id.equals(assignment) ? LocalDate.parse("2026-08-26") : LocalDate.parse("2026-08-20")).update();
			}
		}
		migrateSchema(schema, null);
		try (var connection = schemaConnection(schema)) {
			JdbcClient jdbc = JdbcClient.create(new SingleConnectionDataSource(connection, true));
			assertThat(jdbc.sql("SELECT assigned_on FROM workout_assignment WHERE id = :id").param("id", assignment).query(LocalDate.class).single()).isEqualTo("2026-08-25");
			assertThat(jdbc.sql("SELECT assigned_on FROM workout_assignment WHERE id = :id").param("id", intentionallyHistorical).query(LocalDate.class).single()).isEqualTo("2026-08-20");
			assertThat(jdbc.sql("SELECT starts_on FROM workout_assignment WHERE id = :id").param("id", assignment).query(LocalDate.class).single()).isEqualTo("2026-08-26");
			assertThat(jdbc.sql("SELECT ends_on FROM workout_assignment WHERE id = :id").param("id", assignment).query(LocalDate.class).single()).isEqualTo("2026-09-20");
			assertThat(jdbc.sql("SELECT status FROM workout_assignment WHERE id = :id").param("id", assignment).query(String.class).single()).isEqualTo("ACTIVE");
			assertThat(jdbc.sql("SELECT credential_version FROM user_account WHERE id = :id").param("id", user).query(Long.class).single()).isZero();
			assertThat(jdbc.sql("SELECT count(*) FROM user_password_reset").query(Long.class).single()).isZero();
		}
	}

	@Test
	void cleanInstallationEndsWithoutHistoricalDemoFixture() throws Exception {
		try (var connection = DriverManager.getConnection(
				postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
			assertThat(rowExists(connection, "organization", HISTORICAL_DEMO_ORGANIZATION_ID)).isFalse();
			assertThat(rowExists(connection, "branch", HISTORICAL_DEMO_BRANCH_ID)).isFalse();
		}
	}

	@Test
	void upgradeRemovesAnExactEmptyHistoricalDemoFixture() throws Exception {
		String schema = migrationSchema("empty_demo");
		migrateSchema(schema, "87");
		try (var connection = schemaConnection(schema)) {
			assertThat(rowExists(connection, "organization", HISTORICAL_DEMO_ORGANIZATION_ID)).isTrue();
			assertThat(rowExists(connection, "branch", HISTORICAL_DEMO_BRANCH_ID)).isTrue();
		}

		migrateSchema(schema, null);

		try (var connection = schemaConnection(schema)) {
			assertThat(rowExists(connection, "organization", HISTORICAL_DEMO_ORGANIZATION_ID)).isFalse();
			assertThat(rowExists(connection, "branch", HISTORICAL_DEMO_BRANCH_ID)).isFalse();
		}
	}

	@Test
	void upgradeKeepsHistoricalDemoFixtureWhenItContainsActivity() throws Exception {
		String schema = migrationSchema("active_demo");
		migrateSchema(schema, "87");
		UUID personId = UUID.randomUUID();
		try (var connection = schemaConnection(schema);
			 var statement = connection.prepareStatement("""
					INSERT INTO person (
					    id, account_scope, organization_id, first_name, last_name, email, status,
					    created_at, updated_at, version
					) VALUES (?, 'TENANT', ?, 'Real', 'Activity', ?, 'ACTIVE', now(), now(), 0)
					""")) {
			statement.setObject(1, personId);
			statement.setObject(2, HISTORICAL_DEMO_ORGANIZATION_ID);
			statement.setString(3, "activity-" + personId + "@example.mx");
			statement.executeUpdate();
		}

		migrateSchema(schema, null);

		try (var connection = schemaConnection(schema)) {
			assertThat(rowExists(connection, "organization", HISTORICAL_DEMO_ORGANIZATION_ID)).isTrue();
			assertThat(rowExists(connection, "branch", HISTORICAL_DEMO_BRANCH_ID)).isTrue();
			assertThat(rowExists(connection, "person", personId)).isTrue();
		}
	}

	@Test
	void migrationNeverDeletesAnotherOrganizationWithSimilarNames() throws Exception {
		String schema = migrationSchema("similar_demo");
		migrateSchema(schema, "87");
		UUID similarOrganizationId = UUID.randomUUID();
		try (var connection = schemaConnection(schema);
			 var statement = connection.prepareStatement("""
					INSERT INTO organization (
					    id, legal_name, trade_name, tax_id, status, timezone, default_currency,
					    data_origin, created_at, updated_at, version
					) VALUES (?, 'GymOS Demo S.A. de C.V.', 'GymOS Demo', ?, 'ACTIVE',
					          'America/Mexico_City', 'MXN', 'PRODUCTIVE', now(), now(), 0)
					""")) {
			statement.setObject(1, similarOrganizationId);
			statement.setString(2, "SIMILAR-" + similarOrganizationId);
			statement.executeUpdate();
		}

		migrateSchema(schema, null);

		try (var connection = schemaConnection(schema)) {
			assertThat(rowExists(connection, "organization", similarOrganizationId)).isTrue();
		}
	}

	@Test
	void databaseEnforcesPlatformAndTenantIdentityBoundaries() throws Exception {
		try (var connection = DriverManager.getConnection(
				postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
			UUID personId = UUID.randomUUID();
			UUID userId = UUID.randomUUID();
			try (var statement = connection.prepareStatement("""
					INSERT INTO person (
					    id, account_scope, organization_id, first_name, last_name, email, status,
					    created_at, updated_at, version
					) VALUES (?, 'PLATFORM', NULL, 'Platform', 'Admin', ?, 'ACTIVE', now(), now(), 0)
					""")) {
				statement.setObject(1, personId);
				statement.setString(2, "platform-" + userId + "@gymfort.mx");
				statement.executeUpdate();
			}
			try (var statement = connection.prepareStatement("""
					INSERT INTO user_account (
					    id, account_scope, organization_id, person_id, username, password_hash, status,
					    failed_attempts, created_at, updated_at, version
					) VALUES (?, 'PLATFORM', NULL, ?, ?, 'encoded', 'ACTIVE', 0, now(), now(), 0)
					""")) {
				statement.setObject(1, userId);
				statement.setObject(2, personId);
				statement.setString(3, "platform-" + userId + "@gymfort.mx");
				statement.executeUpdate();
			}

			UUID platformRoleId = roleId(connection, "Administrador GymOS");
			UUID tenantRoleId = roleId(connection, "Dueno del gimnasio");
			UUID platformAdminPermissionId = permissionId(connection, "platform.admin");
			try (var statement = connection.prepareStatement(
					"INSERT INTO user_role (user_id, role_id) VALUES (?, ?)")) {
				statement.setObject(1, userId);
				statement.setObject(2, platformRoleId);
				statement.executeUpdate();
			}
			assertThatThrownBy(() -> insertUserRole(connection, userId, tenantRoleId))
					.isInstanceOf(SQLException.class);
			assertThatThrownBy(() -> insertRolePermission(connection, tenantRoleId, platformAdminPermissionId))
					.isInstanceOf(SQLException.class);
			assertThatThrownBy(() -> insertUserBranchAccess(
					connection, userId, UUID.fromString("22222222-2222-2222-2222-222222222222")))
					.isInstanceOf(SQLException.class);

			assertThatThrownBy(() -> insertPlatformPersonWithOrganization(connection, UUID.randomUUID()))
					.isInstanceOf(SQLException.class);
		}
	}

	@Test
	void physicalAccessMigrationUpgradesAnExistingV83Schema() throws Exception {
		String schema = "upgrade_" + UUID.randomUUID().toString().replace("-", "");
		Flyway.configure()
				.dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
				.locations("classpath:db/migration")
				.schemas(schema)
				.defaultSchema(schema)
				.target("83")
				.load()
				.migrate();
		UUID organizationId = UUID.randomUUID();
		UUID branchId = UUID.randomUUID();
		UUID personId = UUID.randomUUID();
		UUID clientId = UUID.randomUUID();
		UUID connectionId = UUID.randomUUID();
		UUID linkId = UUID.randomUUID();
		UUID syncStateId = UUID.randomUUID();
		try (var connection = DriverManager.getConnection(
				postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
			try (var statement = connection.createStatement()) {
				statement.execute("SET search_path TO " + schema);
			}
			insertOrganization(connection, organizationId);
			insertBranch(connection, organizationId, branchId);
			try (var statement = connection.prepareStatement("""
					INSERT INTO person (
					    id, organization_id, first_name, last_name, email, phone, status,
					    created_at, updated_at, version
					)
					VALUES (?, ?, 'Pending', 'Member', ?, ?, 'ACTIVE', now(), now(), 0)
					""")) {
				statement.setObject(1, personId);
				statement.setObject(2, organizationId);
				statement.setString(3, "pending-" + personId + "@example.com");
				statement.setString(4, "+52" + Math.abs(personId.hashCode()));
				statement.executeUpdate();
			}
			try (var statement = connection.prepareStatement("""
					INSERT INTO client (
					    id, organization_id, person_id, status, joined_at,
					    created_at, updated_at, version, home_branch_id
					)
					VALUES (?, ?, ?, 'ACTIVE', now(), now(), now(), 0, ?)
					""")) {
				statement.setObject(1, clientId);
				statement.setObject(2, organizationId);
				statement.setObject(3, personId);
				statement.setObject(4, branchId);
				statement.executeUpdate();
			}
			try (var statement = connection.prepareStatement("""
					INSERT INTO access_provider_connection (
					    id, organization_id, branch_id, provider_key, display_name, connector_key,
					    enabled, sync_status, created_at, updated_at, version
					)
					VALUES (?, ?, ?, 'HIKCENTRAL', 'HikCentral', ?, TRUE,
					        'AWAITING_CONNECTOR', now(), now(), 0)
					""")) {
				statement.setObject(1, connectionId);
				statement.setObject(2, organizationId);
				statement.setObject(3, branchId);
				statement.setString(4, "upgrade-connector-" + connectionId);
				statement.executeUpdate();
			}
			try (var statement = connection.prepareStatement("""
					INSERT INTO access_provider_client_link (
					    id, organization_id, branch_id, connection_id, client_id,
					    external_person_ref, linked_at, updated_at, version
					)
					VALUES (?, ?, ?, ?, ?, 'person-upgrade', now(), now(), 0)
					""")) {
				statement.setObject(1, linkId);
				statement.setObject(2, organizationId);
				statement.setObject(3, branchId);
				statement.setObject(4, connectionId);
				statement.setObject(5, clientId);
				statement.executeUpdate();
			}
			try (var statement = connection.prepareStatement("""
					INSERT INTO access_provider_sync_state (
					    id, organization_id, branch_id, connection_id, client_id,
					    desired_access_level_refs, action, status, requested_at,
					    created_at, updated_at, version
					)
					VALUES (?, ?, ?, ?, ?, '["LEVEL-UPGRADE"]'::jsonb,
					        'UPSERT', 'PENDING', now(), now(), now(), 0)
					""")) {
				statement.setObject(1, syncStateId);
				statement.setObject(2, organizationId);
				statement.setObject(3, branchId);
				statement.setObject(4, connectionId);
				statement.setObject(5, clientId);
				statement.executeUpdate();
			}
		}

		Flyway.configure()
				.dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
				.locations("classpath:db/migration")
				.schemas(schema)
				.defaultSchema(schema)
				.load()
				.migrate();

		try (var connection = DriverManager.getConnection(
				postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
			assertThat(tableExists(connection, schema, "access_provider_sync_job")).isTrue();
			assertThat(tableExists(connection, schema, "access_connector_credential")).isTrue();
			assertThat(tableExists(connection, schema, "platform_customer_account")).isTrue();
			try (var statement = connection.createStatement()) {
				statement.execute("SET search_path TO " + schema);
			}
			try (var statement = connection.prepareStatement("""
					SELECT job.status, job.external_person_ref, state.latest_job_id,
					       job.id AS job_id
					FROM access_provider_sync_job job
					JOIN access_provider_sync_state state ON state.id = job.sync_state_id
					WHERE state.id = ?
					""")) {
				statement.setObject(1, syncStateId);
				try (var resultSet = statement.executeQuery()) {
					assertThat(resultSet.next()).isTrue();
					assertThat(resultSet.getString("status")).isEqualTo("PENDING");
					assertThat(resultSet.getString("external_person_ref")).isEqualTo("person-upgrade");
					assertThat(resultSet.getObject("latest_job_id", UUID.class))
							.isEqualTo(resultSet.getObject("job_id", UUID.class));
					assertThat(resultSet.next()).isFalse();
				}
			}
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
	void platformCommercialWritesAreIdempotentAndVersionedOnPostgresql() throws Exception {
		UUID organizationId = UUID.randomUUID();
		UUID branchId = UUID.randomUUID();
		UUID actorId = UUID.randomUUID();
		OffsetDateTime now = OffsetDateTime.parse("2026-08-24T10:00:00-06:00");
		var dataSource = new DriverManagerDataSource(
				postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
		var billingRepository = new PlatformBillingRepository(JdbcClient.create(dataSource));
		var customerRepository = new PlatformCustomerAccountRepository(JdbcClient.create(dataSource));

		try (var connection = DriverManager.getConnection(
				postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
			insertOrganization(connection, organizationId);
			insertBranch(connection, organizationId, branchId);
			insertUser(connection, organizationId, actorId);
		}

		BillingUpdateRecord billing = new BillingUpdateRecord(
				branchId, "ACTIVE", 15, LocalDate.of(2026, 9, 15), "GYMFORT_500", null,
				new BigDecimal("3500.00"), false, "Contrato inicial", 0, actorId, now);
		assertThat(billingRepository.upsert(billing)).isEqualTo(1);
		assertThat(billingRepository.upsert(billing)).isZero();
		assertThat(billingRepository.findByBranchId(branchId).orElseThrow().version()).isEqualTo(1);

		BillingPaymentWrite payment = new BillingPaymentWrite(
				UUID.randomUUID(), branchId, organizationId, "SUBSCRIPTION", "TRANSFER",
				new BigDecimal("1590.00"), LocalDate.of(2026, 8, 24), "SPEI-01", null,
				"payment-2026-08", actorId, now);
		assertThat(billingRepository.insertPayment(payment)).isEqualTo(1);
		assertThat(billingRepository.insertPayment(payment)).isZero();

		CustomerAccountWrite account = new CustomerAccountWrite(
				organizationId, "ACTIVE", "Daniela", "cobranza@gym.mx", "+52 55 1000 1000",
				LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1), 15,
				LocalDate.of(2026, 8, 31), "Seguimiento mensual", 0, actorId, now);
		assertThat(customerRepository.upsert(account)).isEqualTo(1);
		assertThat(customerRepository.upsert(account)).isZero();
		assertThat(customerRepository.find(organizationId).orElseThrow().version()).isEqualTo(1);
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
		return columnExists(connection, "public", table, column);
	}

	private boolean columnExists(
			java.sql.Connection connection, String schema, String table, String column
	) throws Exception {
		try (var statement = connection.prepareStatement("""
				SELECT 1
				FROM information_schema.columns
				WHERE table_schema = ?
				  AND table_name = ?
				  AND column_name = ?
				""")) {
			statement.setString(1, schema);
			statement.setString(2, table);
			statement.setString(3, column);
			try (var resultSet = statement.executeQuery()) {
				return resultSet.next();
			}
		}
	}

	private String migrationSchema(String prefix) {
		return prefix + "_" + UUID.randomUUID().toString().replace("-", "");
	}

	private void migrateSchema(String schema, String target) {
		var configuration = Flyway.configure()
				.dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
				.locations("classpath:db/migration")
				.schemas(schema)
				.defaultSchema(schema);
		if (target != null) configuration.target(target);
		configuration.load().migrate();
	}

	private java.sql.Connection schemaConnection(String schema) throws Exception {
		var connection = DriverManager.getConnection(
				postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
		try (var statement = connection.createStatement()) {
			statement.execute("SET search_path TO " + schema);
		}
		return connection;
	}

	private boolean rowExists(java.sql.Connection connection, String table, UUID id) throws Exception {
		try (var statement = connection.prepareStatement("SELECT 1 FROM " + table + " WHERE id = ?")) {
			statement.setObject(1, id);
			try (var resultSet = statement.executeQuery()) {
				return resultSet.next();
			}
		}
	}

	private boolean tableExists(java.sql.Connection connection, String table) throws Exception {
		return tableExists(connection, "public", table);
	}

	private boolean tableExists(java.sql.Connection connection, String schema, String table) throws Exception {
		try (var statement = connection.prepareStatement("""
				SELECT 1
				FROM information_schema.tables
				WHERE table_schema = ?
				  AND table_name = ?
				""")) {
			statement.setString(1, schema);
			statement.setString(2, table);
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

	private UUID roleId(java.sql.Connection connection, String roleName) throws Exception {
		try (var statement = connection.prepareStatement("SELECT id FROM role WHERE name = ? LIMIT 1")) {
			statement.setString(1, roleName);
			try (var resultSet = statement.executeQuery()) {
				assertThat(resultSet.next()).isTrue();
				return resultSet.getObject("id", UUID.class);
			}
		}
	}

	private UUID permissionId(java.sql.Connection connection, String permissionCode) throws Exception {
		try (var statement = connection.prepareStatement("SELECT id FROM permission WHERE code = ? LIMIT 1")) {
			statement.setString(1, permissionCode);
			try (var resultSet = statement.executeQuery()) {
				assertThat(resultSet.next()).isTrue();
				return resultSet.getObject("id", UUID.class);
			}
		}
	}

	private void insertRolePermission(
			java.sql.Connection connection,
			UUID roleId,
			UUID permissionId
	) throws SQLException {
		try (var statement = connection.prepareStatement(
				"INSERT INTO role_permission (role_id, permission_id) VALUES (?, ?)")) {
			statement.setObject(1, roleId);
			statement.setObject(2, permissionId);
			statement.executeUpdate();
		}
	}

	private void insertUserRole(java.sql.Connection connection, UUID userId, UUID roleId) throws SQLException {
		try (var statement = connection.prepareStatement(
				"INSERT INTO user_role (user_id, role_id) VALUES (?, ?)")) {
			statement.setObject(1, userId);
			statement.setObject(2, roleId);
			statement.executeUpdate();
		}
	}

	private void insertUserBranchAccess(
			java.sql.Connection connection,
			UUID userId,
			UUID branchId
	) throws SQLException {
		try (var statement = connection.prepareStatement("""
				INSERT INTO user_branch_access (user_id, branch_id, access_scope)
				VALUES (?, ?, 'ADMIN')
				""")) {
			statement.setObject(1, userId);
			statement.setObject(2, branchId);
			statement.executeUpdate();
		}
	}

	private void insertPlatformPersonWithOrganization(
			java.sql.Connection connection,
			UUID personId
	) throws SQLException {
		try (var statement = connection.prepareStatement("""
				INSERT INTO person (
				    id, account_scope, organization_id, first_name, last_name, status,
				    created_at, updated_at, version
				) VALUES (?, 'PLATFORM', '11111111-1111-1111-1111-111111111111',
				          'Invalid', 'Platform', 'ACTIVE', now(), now(), 0)
				""")) {
			statement.setObject(1, personId);
			statement.executeUpdate();
		}
	}
}
