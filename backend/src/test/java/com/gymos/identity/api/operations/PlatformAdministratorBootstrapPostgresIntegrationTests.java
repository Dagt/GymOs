package com.gymos.identity.api.operations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import com.gymos.audit.api.AuditService;
import com.gymos.common.api.BusinessException;
import com.gymos.common.security.AccountScope;
import com.gymos.common.security.StrongPasswordPolicy;
import com.gymos.identity.api.operations.PlatformAdministratorBootstrap.BootstrapCommand;
import com.gymos.identity.infrastructure.IdentityProvisioningRepository;
import com.gymos.identity.infrastructure.IdentityProvisioningRepository.PlatformAdministratorState;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Tag("postgres-integration")
@Testcontainers(disabledWithoutDocker = true)
class PlatformAdministratorBootstrapPostgresIntegrationTests {
	@Container
	static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");

	private DriverManagerDataSource dataSource;
	private JdbcClient jdbcClient;
	private IdentityProvisioningRepository repository;
	private TransactionTemplate transaction;

	@BeforeEach
	void resetDatabase() {
		dataSource = new DriverManagerDataSource(
				postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
		Flyway.configure().dataSource(dataSource).locations("classpath:db/migration")
				.cleanDisabled(false).load().clean();
		Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
		jdbcClient = JdbcClient.create(dataSource);
		repository = new IdentityProvisioningRepository(jdbcClient);
		transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
	}

	@Test
	void concurrentBootstrapCreatesOnlyOneInitialAdministrator() throws Exception {
		var service = service(new AuditService(jdbcClient));
		var ready = new CountDownLatch(2);
		var start = new CountDownLatch(1);
		try (var executor = Executors.newFixedThreadPool(2)) {
			var results = List.of(
					executor.submit(() -> runConcurrentBootstrap(
							service, ready, start, "first-admin@gymfort.mx", "First Admin")),
					executor.submit(() -> runConcurrentBootstrap(
							service, ready, start, "second-admin@gymfort.mx", "Second Admin")));
			assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
			start.countDown();

			assertThat(results.get(0).get(30, TimeUnit.SECONDS))
					.isNotEqualTo(results.get(1).get(30, TimeUnit.SECONDS));
		}

		assertThat(repository.platformAdministratorState()).isEqualTo(PlatformAdministratorState.ACTIVE);
		assertThat(effectiveAdministratorCount()).isEqualTo(1);
		assertThat(Boolean.TRUE.equals(transaction.execute(status -> service.bootstrap(command(
				"later-admin@gymfort.mx", "Later Admin"))))).isFalse();
		assertThat(effectiveAdministratorCount()).isEqualTo(1);
	}

	@Test
	void failedCreatorReleasesTransactionLockAndRollsBackIdentity() {
		AuditService failingAudit = mock(AuditService.class);
		doThrow(new IllegalStateException("simulated audit failure"))
				.when(failingAudit).recordSystem(any(), any(), any(), any(), any(), any(), any(), any(), any());

		assertThatThrownBy(() -> transaction.execute(status -> service(failingAudit).bootstrap(command(
				"failed-admin@gymfort.mx", "Failed Admin"))))
				.isInstanceOf(IllegalStateException.class)
				.hasMessage("simulated audit failure");

		assertThat(effectiveAdministratorCount()).isZero();
		assertThat(Boolean.TRUE.equals(transaction.execute(status -> service(new AuditService(jdbcClient)).bootstrap(command(
				"recovery-admin@gymfort.mx", "Recovery Admin"))))).isTrue();
		assertThat(effectiveAdministratorCount()).isEqualTo(1);
	}

	@Test
	void supportAndTenantLookalikeRolesDoNotBlockInitialAdministrator() {
		createPlatformSupportWithoutAdministratorPermission();
		createTenantRoleLookalike();

		assertThat(repository.platformAdministratorState()).isEqualTo(PlatformAdministratorState.NONE);
		assertThat(Boolean.TRUE.equals(transaction.execute(status -> service(new AuditService(jdbcClient)).bootstrap(command(
				"platform-admin@gymfort.mx", "Platform Admin"))))).isTrue();
		assertThat(repository.platformAdministratorState()).isEqualTo(PlatformAdministratorState.ACTIVE);
	}

	@Test
	void disabledAdministratorRequiresRecoveryInsteadOfCreatingAnotherPrivilegedAccount() {
		var service = service(new AuditService(jdbcClient));
		assertThat(Boolean.TRUE.equals(transaction.execute(status -> service.bootstrap(command(
				"disabled-admin@gymfort.mx", "Disabled Admin"))))).isTrue();
		jdbcClient.sql("UPDATE user_account SET status = 'DISABLED' WHERE username = :username")
				.param("username", "disabled-admin@gymfort.mx")
				.update();

		assertThat(repository.platformAdministratorState()).isEqualTo(PlatformAdministratorState.INACTIVE);
		assertThatThrownBy(() -> transaction.execute(status -> service.bootstrap(command(
				"replacement-admin@gymfort.mx", "Replacement Admin"))))
				.isInstanceOfSatisfying(BusinessException.class,
						exception -> assertThat(exception.code())
								.isEqualTo("PLATFORM_BOOTSTRAP_ADMIN_INACTIVE"));
		assertThat(effectiveAdministratorCount()).isEqualTo(1);
	}

	@Test
	void deletedAdministratorDoesNotBlockAReplacementBootstrap() {
		var service = service(new AuditService(jdbcClient));
		assertThat(Boolean.TRUE.equals(transaction.execute(status -> service.bootstrap(command(
				"deleted-admin@gymfort.mx", "Deleted Admin"))))).isTrue();
		jdbcClient.sql("UPDATE user_account SET deleted_at = now() WHERE username = :username")
				.param("username", "deleted-admin@gymfort.mx")
				.update();

		assertThat(repository.platformAdministratorState()).isEqualTo(PlatformAdministratorState.NONE);
		assertThat(Boolean.TRUE.equals(transaction.execute(status -> service.bootstrap(command(
				"replacement-admin@gymfort.mx", "Replacement Admin"))))).isTrue();
		assertThat(effectiveAdministratorCount()).isEqualTo(1);
	}

	private boolean runConcurrentBootstrap(
			PlatformAdministratorBootstrap service,
			CountDownLatch ready,
			CountDownLatch start,
			String email,
			String name
	) throws InterruptedException {
		ready.countDown();
		if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("bootstrap start timeout");
		return Boolean.TRUE.equals(transaction.execute(status -> service.bootstrap(command(email, name))));
	}

	private PlatformAdministratorBootstrap service(AuditService auditService) {
		return new PlatformAdministratorBootstrapAdapter(
				repository, new BCryptPasswordEncoder(), new StrongPasswordPolicy(), auditService);
	}

	private BootstrapCommand command(String email, String name) {
		return new BootstrapCommand(true, email, "TemporaryBootstrap1!", name);
	}

	private int effectiveAdministratorCount() {
		return jdbcClient.sql("""
				SELECT COUNT(DISTINCT ua.id)
				FROM user_account ua
				JOIN user_role ur ON ur.user_id = ua.id
				JOIN role r ON r.id = ur.role_id
				JOIN role_permission rp ON rp.role_id = r.id
				JOIN permission p ON p.id = rp.permission_id
				WHERE ua.account_scope = 'PLATFORM'
				  AND ua.deleted_at IS NULL
				  AND r.account_scope = 'PLATFORM'
				  AND r.deleted_at IS NULL
				  AND p.code = 'platform.admin'
				  AND p.deleted_at IS NULL
				""").query(Integer.class).single();
	}

	private void createPlatformSupportWithoutAdministratorPermission() {
		OffsetDateTime now = OffsetDateTime.now();
		UUID personId = repository.createPerson(
				AccountScope.PLATFORM, null, "Support", "Agent", "support@gymfort.mx", null, null, now);
		UUID userId = repository.createUser(
				AccountScope.PLATFORM, null, personId, "support@gymfort.mx", "encoded", "ACTIVE", null, now);
		UUID roleId = UUID.randomUUID();
		jdbcClient.sql("""
				INSERT INTO role (
				    id, account_scope, organization_id, name, description, system_role,
				    created_at, updated_at, version
				) VALUES (:id, 'PLATFORM', NULL, 'Soporte GymFort', 'Soporte sin administracion',
				          TRUE, now(), now(), 0)
				""").param("id", roleId).update();
		repository.assignRole(userId, roleId);
	}

	private void createTenantRoleLookalike() {
		UUID organizationId = UUID.randomUUID();
		jdbcClient.sql("""
				INSERT INTO organization (
				    id, legal_name, trade_name, tax_id, status, timezone, default_currency,
				    data_origin, created_at, updated_at, version
				) VALUES (:id, 'Tenant Admin SA', 'Administrador GymOS Tenant', :taxId,
				          'ACTIVE', 'America/Mexico_City', 'MXN', 'PRODUCTIVE', now(), now(), 0)
				""")
				.param("id", organizationId)
				.param("taxId", "TENANT-" + organizationId)
				.update();
		OffsetDateTime now = OffsetDateTime.now();
		UUID personId = repository.createPerson(
				AccountScope.TENANT, organizationId, "Tenant", "Admin", "tenant-admin@example.mx", null, null, now);
		UUID userId = repository.createUser(
				AccountScope.TENANT, organizationId, personId, "tenant-admin@example.mx", "encoded", "ACTIVE", null, now);
		UUID roleId = UUID.randomUUID();
		jdbcClient.sql("""
				INSERT INTO role (
				    id, account_scope, organization_id, name, description, system_role,
				    created_at, updated_at, version
				) VALUES (:id, 'TENANT', :organizationId, 'Administrador GymOS',
				          'Nombre parecido sin permiso de plataforma', FALSE, now(), now(), 0)
				""")
				.param("id", roleId)
				.param("organizationId", organizationId)
				.update();
		repository.assignRole(userId, roleId);
	}
}
