package com.gymos.common.security;

import com.gymos.common.api.BusinessException;
import java.time.Duration;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
@Profile("!test & !local")
public class RedisRateLimiter implements RateLimiter {
	private static final DefaultRedisScript<Long> SCRIPT = new DefaultRedisScript<>("""
			local current = redis.call('INCR', KEYS[1])
			if current == 1 then
			  redis.call('PEXPIRE', KEYS[1], ARGV[1])
			end
			return current
			""", Long.class);

	private final StringRedisTemplate redisTemplate;

	public RedisRateLimiter(StringRedisTemplate redisTemplate) {
		this.redisTemplate = redisTemplate;
	}

	@Override
	public void check(String key, int maxRequests, Duration windowDuration) {
		String namespacedKey = "gymos:rate-limit:" + key;
		Long current = redisTemplate.execute(SCRIPT, List.of(namespacedKey), String.valueOf(windowDuration.toMillis()));
		if (current != null && current > maxRequests) {
			throw new BusinessException("RATE_LIMIT_EXCEEDED", "Demasiados intentos. Espera un momento y vuelve a intentar.");
		}
	}

	@Override
	public boolean isLimited(String key, int maxRequests, Duration windowDuration) {
		String current = redisTemplate.opsForValue().get("gymos:rate-limit:" + key);
		if (current == null) {
			return false;
		}
		try {
			return Long.parseLong(current) >= maxRequests;
		} catch (NumberFormatException exception) {
			return false;
		}
	}
}
