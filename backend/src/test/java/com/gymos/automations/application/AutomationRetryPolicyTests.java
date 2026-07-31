package com.gymos.automations.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class AutomationRetryPolicyTests {
	private final AutomationRetryPolicy policy = new AutomationRetryPolicy();
	private final OffsetDateTime now = OffsetDateTime.parse("2026-07-31T20:00:00Z");

	@Test
	void schedulesExponentialBackoffWithOneHourCap() {
		assertThat(policy.nextAttemptAt(1, now)).isEqualTo(now.plusMinutes(1));
		assertThat(policy.nextAttemptAt(2, now)).isEqualTo(now.plusMinutes(2));
		assertThat(policy.nextAttemptAt(3, now)).isEqualTo(now.plusMinutes(4));
		assertThat(policy.nextAttemptAt(7, now)).isEqualTo(now.plusMinutes(60));
		assertThat(policy.nextAttemptAt(20, now)).isEqualTo(now.plusMinutes(60));
	}

	@Test
	void movesToDeadLetterWhenMaximumAttemptsAreConsumed() {
		assertThat(policy.exhausted(2, 3)).isFalse();
		assertThat(policy.exhausted(3, 3)).isTrue();
		assertThat(policy.exhausted(4, 3)).isTrue();
	}

	@Test
	void alwaysAllowsAtLeastOneAttempt() {
		assertThat(policy.exhausted(0, 0)).isFalse();
		assertThat(policy.exhausted(1, 0)).isTrue();
	}
}
