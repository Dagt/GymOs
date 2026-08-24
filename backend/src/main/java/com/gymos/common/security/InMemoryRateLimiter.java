package com.gymos.common.security;

import com.gymos.common.api.BusinessException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile({"test", "local"})
public class InMemoryRateLimiter implements RateLimiter {
	private final Map<String, Window> windows = new ConcurrentHashMap<>();

	@Override
	public void check(String key, int maxRequests, Duration windowDuration) {
		Instant now = Instant.now();
		Window window = windows.compute(key, (ignored, current) -> {
			if (current == null || now.isAfter(current.startedAt().plus(windowDuration))) {
				return new Window(now, 1);
			}
			return new Window(current.startedAt(), current.count() + 1);
		});
		if (window.count() > maxRequests) {
			throw new BusinessException("RATE_LIMIT_EXCEEDED", "Demasiados intentos. Espera un momento y vuelve a intentar.");
		}
		if (windows.size() > 10_000) {
			windows.entrySet().removeIf(entry -> now.isAfter(entry.getValue().startedAt().plus(windowDuration.multipliedBy(2))));
		}
	}

	@Override
	public boolean isLimited(String key, int maxRequests, Duration windowDuration) {
		Instant now = Instant.now();
		Window window = windows.get(key);
		if (window == null) {
			return false;
		}
		if (now.isAfter(window.startedAt().plus(windowDuration))) {
			windows.remove(key, window);
			return false;
		}
		return window.count() >= maxRequests;
	}

	private record Window(Instant startedAt, int count) {
	}
}
