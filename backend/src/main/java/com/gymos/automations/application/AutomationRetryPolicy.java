package com.gymos.automations.application;

import java.time.OffsetDateTime;
import org.springframework.stereotype.Component;

@Component
public class AutomationRetryPolicy {
	public boolean exhausted(int attemptCount, int maxAttempts) {
		return attemptCount >= Math.max(1, maxAttempts);
	}

	public OffsetDateTime nextAttemptAt(int attemptCount, OffsetDateTime now) {
		int normalizedAttempt = Math.max(1, attemptCount);
		long delayMinutes = Math.min(60L, 1L << Math.min(10, normalizedAttempt - 1));
		return now.plusMinutes(delayMinutes);
	}
}
