package com.gymos.common.security;

import java.time.Duration;

public interface RateLimiter {
	void check(String key, int maxRequests, Duration windowDuration);
}
