package com.gymos.automations.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class DurableAutomationRepositoryPostgresTests {
	@Container
	private static final PostgreSQLContainer<?> POSTGRES =
			new PostgreSQLContainer<>("postgres:16-alpine");

	private JdbcClient jdbc;
	private DurableAutomationRepository repository;
	private AutomationLeaseRecoveryRepository recoveryRepository;
	private UUID ruleId;
	private UUID organizationId;
	private UUID branchId;

	@BeforeEach
	void setUp() {
		var dataSource = new DriverManagerDataSource(
				POSTGRES.getJdbcUrl(),
				POSTGRES.getUsername(),
				POSTGRES.getPassword()
		);
		jdbc = JdbcClient.create(dataSource);
		repository = new DurableAutomationRepository(jdbc);
		recoveryRepository = new AutomationLeaseRecoveryRepository(jdbc);
		resetSchema();
		ruleId = UUID.randomUUID();
		organizationId = UUID.randomUUID();
		branchId = UUID.randomUUID();
		jdbc.sql("INSERT INTO automation_rule (id) VALUES (:id)")
				.param("id", ruleId)
				.update();
	}

	@Test
	void enqueueIsIdempotentWithinOrganization() {
		OffsetDateTime scheduledAt = OffsetDateTime.now().minusSeconds(1);

		var first = repository.enqueue(
				organizationId, branchId, ruleId, "same-occurrence", "trigger-1", 3, scheduledAt
		);
		var second = repository.enqueue(
				organizationId, branchId, ruleId, "same-occurrence", "trigger-1", 3, scheduledAt
		);

		assertThat(second.id()).isEqualTo(first.id());
		assertThat(countWorkItems()).isEqualTo(1);
	}

	@Test
	void onlyOneWorkerCanClaimTheSameOccurrence() {
		OffsetDateTime now = OffsetDateTime.now();
		var item = repository.enqueue(
				organizationId, branchId, ruleId, "single-claim", null, 3, now.minusSeconds(1)
		);

		boolean firstClaim = repository.claim(item.id(), now, now.plusMinutes(5));
		boolean secondClaim = repository.claim(item.id(), now, now.plusMinutes(5));

		assertThat(firstClaim).isTrue();
		assertThat(secondClaim).isFalse();
		var claimed = repository.workItem(item.id()).orElseThrow();
		assertThat(claimed.status()).isEqualTo("RUNNING");
		assertThat(claimed.attemptCount()).isEqualTo(1);
	}

	@Test
	void expiredLeaseReturnsToRetryWithoutDoubleCountingTheAttempt() {
		OffsetDateTime now = OffsetDateTime.now().truncatedTo(ChronoUnit.MICROS);
		var item = repository.enqueue(
				organizationId, branchId, ruleId, "recoverable", null, 3, now.minusMinutes(20)
		);
		assertThat(repository.claim(item.id(), now.minusMinutes(10), now.minusMinutes(5))).isTrue();

		int recovered = recoveryRepository.recoverExpiredLeases(now);

		assertThat(recovered).isEqualTo(1);
		var retry = repository.workItem(item.id()).orElseThrow();
		assertThat(retry.status()).isEqualTo("RETRY_SCHEDULED");
		assertThat(retry.attemptCount()).isEqualTo(1);
		assertThat(retry.lockedUntil()).isNull();
		assertThat(retry.nextAttemptAt()).isEqualTo(now);

		assertThat(repository.claim(item.id(), now.plusSeconds(1), now.plusMinutes(5))).isTrue();
		assertThat(repository.workItem(item.id()).orElseThrow().attemptCount()).isEqualTo(2);
	}

	@Test
	void expiredLeaseMovesToDeadLetterWhenAttemptsAreExhausted() {
		OffsetDateTime now = OffsetDateTime.now().truncatedTo(ChronoUnit.MICROS);
		var item = repository.enqueue(
				organizationId, branchId, ruleId, "exhausted", null, 1, now.minusMinutes(20)
		);
		assertThat(repository.claim(item.id(), now.minusMinutes(10), now.minusMinutes(5))).isTrue();

		recoveryRepository.recoverExpiredLeases(now);

		var deadLetter = repository.workItem(item.id()).orElseThrow();
		assertThat(deadLetter.status()).isEqualTo("DEAD_LETTER");
		assertThat(deadLetter.attemptCount()).isEqualTo(1);
		assertThat(deadLetter.finishedAt()).isNotNull();
		assertThat(deadLetter.lockedUntil()).isNull();
	}

	private int countWorkItems() {
		return jdbc.sql("SELECT COUNT(*) FROM automation_work_item")
				.query(Integer.class)
				.single();
	}

	private void resetSchema() {
		jdbc.sql("DROP TABLE IF EXISTS automation_work_item").update();
		jdbc.sql("DROP TABLE IF EXISTS automation_rule").update();
		jdbc.sql("CREATE TABLE automation_rule (id UUID PRIMARY KEY)").update();
		jdbc.sql("""
				CREATE TABLE automation_work_item (
				    id UUID PRIMARY KEY,
				    organization_id UUID NOT NULL,
				    branch_id UUID,
				    rule_id UUID NOT NULL REFERENCES automation_rule(id),
				    idempotency_key VARCHAR(240) NOT NULL,
				    trigger_reference VARCHAR(500),
				    status VARCHAR(40) NOT NULL,
				    attempt_count INTEGER NOT NULL DEFAULT 0,
				    max_attempts INTEGER NOT NULL DEFAULT 1,
				    scheduled_at TIMESTAMPTZ NOT NULL,
				    next_attempt_at TIMESTAMPTZ NOT NULL,
				    locked_until TIMESTAMPTZ,
				    started_at TIMESTAMPTZ,
				    finished_at TIMESTAMPTZ,
				    last_error VARCHAR(2000),
				    effect_reference_id UUID,
				    created_at TIMESTAMPTZ NOT NULL,
				    updated_at TIMESTAMPTZ NOT NULL,
				    version BIGINT NOT NULL DEFAULT 0,
				    UNIQUE (organization_id, idempotency_key)
				)
				""").update();
	}
}
