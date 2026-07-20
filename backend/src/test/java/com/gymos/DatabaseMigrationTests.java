package com.gymos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
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
