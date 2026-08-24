package com.gymos.common.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gymos.common.api.BusinessException;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class InMemoryRateLimiterTests {
	private final InMemoryRateLimiter rateLimiter = new InMemoryRateLimiter();

	@Test
	void rejectsRequestsBeyondLimitInsideWindow() {
		assertThatCode(() -> {
			if (rateLimiter.isLimited("kiosk:test", 2, Duration.ofMinutes(1))) {
				throw new AssertionError("Fresh window must not be limited");
			}
		}).doesNotThrowAnyException();
		assertThatCode(() -> rateLimiter.check("kiosk:test", 2, Duration.ofMinutes(1))).doesNotThrowAnyException();
		assertThatCode(() -> rateLimiter.check("kiosk:test", 2, Duration.ofMinutes(1))).doesNotThrowAnyException();
		org.assertj.core.api.Assertions.assertThat(
				rateLimiter.isLimited("kiosk:test", 2, Duration.ofMinutes(1))).isTrue();

		assertThatThrownBy(() -> rateLimiter.check("kiosk:test", 2, Duration.ofMinutes(1)))
				.isInstanceOf(BusinessException.class)
				.hasMessageContaining("Demasiados intentos");
	}
}
