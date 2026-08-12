package com.gymos.automations.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
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
class AutomationPostgresConcurrencyIntegrationTests {
	@Container
	static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");

	private JdbcClient jdbcClient;
	private AutomationRepository repository;
	private TenantRule tenant;

	@BeforeEach
	void setUp() {
		var dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
		Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").cleanDisabled(false).load().clean();
		Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
		jdbcClient = JdbcClient.create(dataSource);
		repository = new AutomationRepository(jdbcClient);
		tenant = seedTenant("PRIMARY");
	}

	@Test
	void scheduledRuleLeaseHasSingleWinnerAndCanRecoverAfterExpiration() throws Exception {
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		CountDownLatch start = new CountDownLatch(1);
		try (var executor = Executors.newFixedThreadPool(8)) {
			List<Future<Boolean>> claims = new ArrayList<>();
			for (int index = 0; index < 8; index++) {
				claims.add(executor.submit(() -> {
					start.await();
					return repository.claimScheduledRule(
							tenant.ruleId(), now, now.plusMinutes(5), "scheduled:one"
					);
				}));
			}
			start.countDown();
			assertThat(trueCount(claims)).isEqualTo(1);
		}
		assertThat(repository.claimScheduledRule(
				tenant.ruleId(), now.plusMinutes(6), now.plusMinutes(11), "scheduled:one"
		)).isTrue();
	}

	@Test
	void duplicateDomainEventIsStoredOnceAndLeasePreventsDoubleProcessing() throws Exception {
		String reference = "payment:" + UUID.randomUUID();
		CountDownLatch start = new CountDownLatch(1);
		try (var executor = Executors.newFixedThreadPool(8)) {
			List<Future<?>> enqueues = new ArrayList<>();
			for (int index = 0; index < 8; index++) {
				enqueues.add(executor.submit(() -> {
					start.await();
					repository.enqueueTriggerEvent(
							tenant.organizationId(), tenant.branchId(), "PAYMENT_COMPLETED", reference, "{}"
					);
					return null;
				}));
			}
			start.countDown();
			for (Future<?> enqueue : enqueues) enqueue.get();
		}
		assertThat(jdbcClient.sql("SELECT COUNT(*) FROM automation_trigger_event WHERE trigger_reference = :reference")
				.param("reference", reference).query(Long.class).single()).isEqualTo(1);

		var event = repository.dueTriggerEvents(OffsetDateTime.now(ZoneOffset.UTC).plusSeconds(1), 10).getFirst();
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC).plusSeconds(1);
		CountDownLatch claimStart = new CountDownLatch(1);
		try (var executor = Executors.newFixedThreadPool(8)) {
			List<Future<Boolean>> claims = new ArrayList<>();
			for (int index = 0; index < 8; index++) {
				claims.add(executor.submit(() -> {
					claimStart.await();
					return repository.claimTriggerEvent(event.id(), now, now.plusMinutes(5));
				}));
			}
			claimStart.countDown();
			assertThat(trueCount(claims)).isEqualTo(1);
		}
		assertThat(repository.claimTriggerEvent(event.id(), now.plusMinutes(6), now.plusMinutes(11))).isTrue();
	}

	@Test
	void executionPaginationIsStableAndTenantScoped() {
		TenantRule other = seedTenant("OTHER");
		for (int index = 0; index < 5; index++) {
			UUID executionId = repository.startExecution(
					tenant.organizationId(), tenant.branchId(), tenant.ruleId(), 0,
					"primary:" + index, "primary:" + index, "primary-key:" + index
			);
			repository.finishExecution(executionId, "SUCCEEDED", null);
		}
		repository.startExecution(
				other.organizationId(), other.branchId(), other.ruleId(), 0,
				"other", "other", "other-key"
		);

		var first = repository.executionHistory(tenant.organizationId(), tenant.branchId(), tenant.ruleId(), 0, 2);
		var second = repository.executionHistory(tenant.organizationId(), tenant.branchId(), tenant.ruleId(), 1, 2);
		var third = repository.executionHistory(tenant.organizationId(), tenant.branchId(), tenant.ruleId(), 2, 2);
		assertThat(first.totalElements()).isEqualTo(5);
		assertThat(first.totalPages()).isEqualTo(3);
		assertThat(first.items()).hasSize(2);
		assertThat(second.items()).hasSize(2);
		assertThat(third.items()).hasSize(1);
		var ids = new HashSet<UUID>();
		first.items().forEach(item -> ids.add(item.id()));
		second.items().forEach(item -> ids.add(item.id()));
		third.items().forEach(item -> ids.add(item.id()));
		assertThat(ids).hasSize(5);
	}

	@Test
	void schedulerQueuesInterleaveTenantsInsteadOfAllowingOneTenantToStarveOthers() {
		TenantRule other = seedTenant("FAIR");
		for (int index = 0; index < 4; index++) {
			UUID ruleId = UUID.randomUUID();
			jdbcClient.sql("""
					INSERT INTO automation_rule (
					 id, organization_id, branch_id, name, trigger_type, condition_json,
					 action_type, action_config_json, status, retry_limit, schedule_cron,
					 next_run_at, created_at, updated_at, version)
					VALUES (:id, :organizationId, :branchId, :name, 'SCHEDULED', '{"intervalMinutes":60}',
					 'CREATE_NOTIFICATION', '{"body":"Test"}', 'ENABLED', 3, 'interval:60',
					 :nextRunAt, now(), now(), 0)
					""")
					.param("id", ruleId).param("organizationId", tenant.organizationId())
					.param("branchId", tenant.branchId()).param("name", "Primary " + index)
					.param("nextRunAt", OffsetDateTime.now(ZoneOffset.UTC).minusHours(2).plusSeconds(index))
					.update();
		}

		var rules = repository.dueScheduledRules(OffsetDateTime.now(ZoneOffset.UTC), 2);
		assertThat(rules).extracting(AutomationRepository.AutomationRuleRecord::organizationId)
				.containsExactlyInAnyOrder(tenant.organizationId(), other.organizationId());

		for (int index = 0; index < 3; index++) {
			repository.enqueueTriggerEvent(
					tenant.organizationId(), tenant.branchId(), "PAYMENT_COMPLETED", "primary-event:" + index, "{}"
			);
		}
		repository.enqueueTriggerEvent(
				other.organizationId(), other.branchId(), "PAYMENT_COMPLETED", "other-event", "{}"
		);
		var events = repository.dueTriggerEvents(OffsetDateTime.now(ZoneOffset.UTC).plusSeconds(1), 2);
		assertThat(events).extracting(AutomationRepository.AutomationTriggerEventRecord::organizationId)
				.containsExactlyInAnyOrder(tenant.organizationId(), other.organizationId());
	}

	@Test
	void pausedTenantIsExcludedWithoutStoppingOtherTenants() {
		TenantRule other = seedTenant("RUNNING");
		assertThat(repository.schedulerStatus(tenant.organizationId()).paused()).isFalse();
		jdbcClient.sql("""
				INSERT INTO automation_scheduler_control (organization_id, paused, reason, updated_at, version)
				VALUES (:organizationId, true, 'maintenance', now(), 0)
				""").param("organizationId", tenant.organizationId()).update();
		var status = repository.schedulerStatus(tenant.organizationId());
		assertThat(status.paused()).isTrue();
		assertThat(status.reason()).isEqualTo("maintenance");

		assertThat(repository.dueScheduledRules(OffsetDateTime.now(ZoneOffset.UTC), 10))
				.extracting(AutomationRepository.AutomationRuleRecord::organizationId)
				.contains(other.organizationId())
				.doesNotContain(tenant.organizationId());
	}

	@Test
	void bookingReminderBecomesDueInsideItsWindowAndIsDeduplicatedByRuleOccurrence() {
		UUID personId = UUID.randomUUID();
		UUID clientId = UUID.randomUUID();
		UUID resourceId = UUID.randomUUID();
		UUID bookingId = UUID.randomUUID();
		UUID reminderRuleId = UUID.randomUUID();
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		OffsetDateTime startsAt = now.plusHours(12);
		jdbcClient.sql("""
				INSERT INTO person (id, organization_id, first_name, last_name, birth_date, status, created_at, updated_at, version)
				VALUES (:id, :organizationId, 'Socio', 'Recordatorio', '1990-01-01', 'ACTIVE', now(), now(), 0)
				""").param("id", personId).param("organizationId", tenant.organizationId()).update();
		jdbcClient.sql("""
				INSERT INTO client (id, organization_id, person_id, home_branch_id, status, joined_at, created_at, updated_at, version)
				VALUES (:id, :organizationId, :personId, :branchId, 'ACTIVE', now(), now(), now(), 0)
				""").param("id", clientId).param("organizationId", tenant.organizationId())
				.param("personId", personId).param("branchId", tenant.branchId()).update();
		jdbcClient.sql("""
				INSERT INTO resource (id, organization_id, branch_id, name, resource_type, capacity, slot_minutes,
				 status, opens_at, closes_at, created_at, updated_at, version)
				VALUES (:id, :organizationId, :branchId, 'Cancha recordatorio', 'COURT', 1, 60,
				 'ACTIVE', '06:00', '22:00', now(), now(), 0)
				""").param("id", resourceId).param("organizationId", tenant.organizationId())
				.param("branchId", tenant.branchId()).update();
		jdbcClient.sql("""
				INSERT INTO booking (id, organization_id, branch_id, resource_id, client_id, status,
				 starts_at, ends_at, created_at, updated_at, version)
				VALUES (:id, :organizationId, :branchId, :resourceId, :clientId, 'CONFIRMED',
				 :startsAt, :endsAt, now(), now(), 0)
				""").param("id", bookingId).param("organizationId", tenant.organizationId())
				.param("branchId", tenant.branchId()).param("resourceId", resourceId)
				.param("clientId", clientId).param("startsAt", startsAt).param("endsAt", startsAt.plusHours(1)).update();
		jdbcClient.sql("""
				INSERT INTO automation_rule (
				 id, organization_id, branch_id, name, trigger_type, condition_json,
				 action_type, action_config_json, status, retry_limit, created_at, updated_at, version)
				VALUES (:id, :organizationId, :branchId, 'Recordar reserva', 'BOOKING_REMINDER',
				 '{"minutesBefore":1440}', 'CREATE_NOTIFICATION', '{"channel":"EMAIL","body":"Te esperamos"}',
				 'ENABLED', 3, now(), now(), 0)
				""").param("id", reminderRuleId).param("organizationId", tenant.organizationId())
				.param("branchId", tenant.branchId()).update();

		var reminders = repository.dueBookingReminders(now, 10);
		assertThat(reminders).singleElement().satisfies(reminder -> {
			assertThat(reminder.bookingId()).isEqualTo(bookingId);
			assertThat(reminder.ruleId()).isEqualTo(reminderRuleId);
			assertThat(reminder.minutesBefore()).isEqualTo(1440);
		});

		String reference = "booking-reminder:" + bookingId + ":" + reminderRuleId + ":v0";
		repository.enqueueTriggerEvent(
				tenant.organizationId(), tenant.branchId(), "BOOKING_REMINDER", reference, "{}"
		);
		assertThat(repository.dueBookingReminders(now, 10)).isEmpty();
	}

	private TenantRule seedTenant(String code) {
		UUID organizationId = UUID.randomUUID();
		UUID branchId = UUID.randomUUID();
		UUID ruleId = UUID.randomUUID();
		jdbcClient.sql("""
				INSERT INTO organization (id, legal_name, trade_name, status, timezone, default_currency, created_at, updated_at, version)
				VALUES (:id, :name, :name, 'ACTIVE', 'America/Mexico_City', 'MXN', now(), now(), 0)
				""").param("id", organizationId).param("name", "Gym " + code).update();
		jdbcClient.sql("""
				INSERT INTO branch (id, organization_id, name, code, timezone, status, created_at, updated_at, version)
				VALUES (:id, :organizationId, :name, :code, 'America/Mexico_City', 'ACTIVE', now(), now(), 0)
				""").param("id", branchId).param("organizationId", organizationId)
				.param("name", "Branch " + code).param("code", code).update();
		jdbcClient.sql("""
				INSERT INTO automation_rule (
				 id, organization_id, branch_id, name, trigger_type, condition_json,
				 action_type, action_config_json, status, retry_limit, schedule_cron,
				 next_run_at, created_at, updated_at, version)
				VALUES (:id, :organizationId, :branchId, :name, 'SCHEDULED', '{"intervalMinutes":60}',
				 'CREATE_NOTIFICATION', '{"body":"Test"}', 'ENABLED', 3, 'interval:60',
				 :nextRunAt, now(), now(), 0)
				""").param("id", ruleId).param("organizationId", organizationId)
				.param("branchId", branchId).param("name", "Rule " + code)
				.param("nextRunAt", OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1)).update();
		return new TenantRule(organizationId, branchId, ruleId);
	}

	private int trueCount(List<Future<Boolean>> futures) throws Exception {
		int total = 0;
		for (Future<Boolean> future : futures) if (future.get()) total++;
		return total;
	}

	private record TenantRule(UUID organizationId, UUID branchId, UUID ruleId) {}
}
