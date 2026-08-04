package com.gymos.notifications.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.gymos.notifications.infrastructure.NotificationRepository.CreateNotificationData;
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
class NotificationPostgresIntegrationTests {
	@Container
	static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");

	private NotificationRepository repository;
	private JdbcClient jdbcClient;

	@BeforeEach
	void setUp() {
		var dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
		Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").cleanDisabled(false).load().clean();
		Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
		jdbcClient = JdbcClient.create(dataSource);
		repository = new NotificationRepository(jdbcClient);
	}

	@Test
	void deliveryQueueIsFairAcrossTenantsAndLeaseHasSingleWinner() throws Exception {
		Tenant first = tenant("EMAIL-A");
		Tenant second = tenant("EMAIL-B");
		for (int index = 0; index < 4; index++) queue(first, "first:" + index);
		UUID secondNotification = queue(second, "second:0");

		assertThat(repository.dueEmailIds(OffsetDateTime.now(ZoneOffset.UTC).plusSeconds(1), 2))
				.containsExactlyInAnyOrder(notification(first, "first:0"), secondNotification);

		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC).plusSeconds(1);
		CountDownLatch start = new CountDownLatch(1);
		List<Future<Boolean>> claims = new ArrayList<>();
		try (var executor = Executors.newFixedThreadPool(8)) {
			for (int index = 0; index < 8; index++) {
				claims.add(executor.submit(() -> {
					start.await();
					return repository.claimEmail(secondNotification, now, now.plusMinutes(2));
				}));
			}
			start.countDown();
			int winners = 0;
			for (Future<Boolean> claim : claims) if (claim.get()) winners++;
			assertThat(winners).isEqualTo(1);
		}
	}

	@Test
	void expiredProcessingLeaseCanBeRecoveredWithoutDuplicatingNotification() {
		Tenant tenant = tenant("EMAIL-RECOVERY");
		UUID notificationId = queue(tenant, "recovery");
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC).plusSeconds(1);
		assertThat(repository.claimEmail(notificationId, now, now.plusMinutes(2))).isTrue();
		assertThat(repository.claimEmail(notificationId, now.plusMinutes(1), now.plusMinutes(3))).isFalse();
		assertThat(repository.claimEmail(notificationId, now.plusMinutes(3), now.plusMinutes(5))).isTrue();
		assertThat(repository.notification(tenant.organizationId(), notificationId).attemptCount()).isEqualTo(2);
	}

	@Test
	void configurationAndPreferencesAreStrictlyTenantScoped() {
		Tenant first = tenant("EMAIL-CFG-A");
		Tenant second = tenant("EMAIL-CFG-B");
		repository.setEmailPreference(first.organizationId(), "a".repeat(64), false, false, null);

		assertThat(repository.emailAllowed(first.organizationId(), "a".repeat(64), "TRANSACTIONAL")).isFalse();
		assertThat(repository.emailAllowed(second.organizationId(), "a".repeat(64), "TRANSACTIONAL")).isTrue();
		assertThat(repository.emailConfiguration(first.organizationId()).fromEmail()).isEqualTo("envios@gymos.test");
		assertThat(repository.emailConfiguration(second.organizationId()).fromEmail()).isEqualTo("envios@gymos.test");
	}

	private Tenant tenant(String code) {
		UUID organizationId = UUID.randomUUID();
		UUID branchId = UUID.randomUUID();
		jdbcClient.sql("""
				INSERT INTO organization (
				 id, legal_name, trade_name, status, timezone, default_currency,
				 created_at, updated_at, version
				) VALUES (:id, :name, :name, 'ACTIVE', 'America/Mexico_City', 'MXN', now(), now(), 0)
				""").param("id", organizationId).param("name", "Gym " + code).update();
		jdbcClient.sql("""
				INSERT INTO branch (
				 id, organization_id, name, code, timezone, status, created_at, updated_at, version
				) VALUES (:id, :organizationId, :name, :code, 'America/Mexico_City', 'ACTIVE', now(), now(), 0)
				""").param("id", branchId).param("organizationId", organizationId)
				.param("name", "Branch " + code).param("code", code).update();
		repository.upsertEmailConfiguration(
				organizationId, null, true, "SMTP", "envios@gymos.test", "Gym " + code, null
		);
		return new Tenant(organizationId, branchId);
	}

	private UUID queue(Tenant tenant, String key) {
		return repository.create(
				tenant.organizationId(), tenant.branchId(), null,
				new CreateNotificationData(
						"EMAIL", "socio@example.com", "Asunto", "Mensaje", null,
						"QUEUED", "TRANSACTIONAL", "DIRECT", null, "TEST", null,
						null, null, "email:" + key
				)
		);
	}

	private UUID notification(Tenant tenant, String key) {
		return jdbcClient.sql("""
				SELECT id FROM notification_log
				WHERE organization_id = :organizationId AND idempotency_key = :key
				""").param("organizationId", tenant.organizationId()).param("key", "email:" + key)
				.query(UUID.class).single();
	}

	private record Tenant(UUID organizationId, UUID branchId) {
	}
}
