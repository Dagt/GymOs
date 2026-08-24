package com.gymos.common.security;

import java.time.Duration;

public interface RateLimiter {
	void check(String key, int maxRequests, Duration windowDuration);

	/** Returns whether the current fixed window has already exhausted its allowance. */
	boolean isLimited(String key, int maxRequests, Duration windowDuration);
}
