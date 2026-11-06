package com.gymos.common.security;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
public class SessionRegistry {
	private static final String PREFIX = "gymos:session:v3:";
	private static final DefaultRedisScript<Long> CREATE = new DefaultRedisScript<>("""
			redis.call('HSET', KEYS[1], 'generation', 0, 'revision', 0, 'context', ARGV[1])
			return redis.call('PEXPIRE', KEYS[1], ARGV[2])
			""", Long.class);
	private static final DefaultRedisScript<String> ROTATE = new DefaultRedisScript<>("""
			local current = redis.call('HMGET', KEYS[1], 'generation', 'revision', 'operation', 'retryUntil')
			if not current[1] then return 'INVALID' end
			local generation = tonumber(current[1])
			local presented = tonumber(ARGV[1])
			if presented > generation then return 'INVALID' end
			local time = redis.call('TIME')
			local now = tonumber(time[1]) * 1000 + math.floor(tonumber(time[2]) / 1000)
			local retry = current[4] and now <= tonumber(current[4])
			if presented < generation then
			    if ARGV[3] == '!' then return 'CONFLICT' end
			    if presented == generation - 1 and retry then
			        if ARGV[3] ~= '!' and current[3] == ARGV[3] then return 'NEXT' end
			        return 'CONFLICT'
			    end
			    redis.call('DEL', KEYS[1])
			    return 'REUSED'
			end
			if current[2] ~= ARGV[2] then return 'CONFLICT' end
			if retry and ARGV[3] ~= '!' and current[3] == ARGV[3] then return 'CURRENT' end
			redis.call('HSET', KEYS[1], 'generation', generation + 1, 'revision', ARGV[4],
			    'context', ARGV[5], 'operation', ARGV[3], 'retryUntil', now + 30000)
			return 'NEXT'
			""", String.class);
	private static final DefaultRedisScript<Long> REVOKE = new DefaultRedisScript<>("""
			local value = redis.call('HGET', KEYS[1], ARGV[1])
			if value and (value == ARGV[2] or
			    (ARGV[1] == 'generation' and tonumber(value) > tonumber(ARGV[2]))) then
			    return redis.call('DEL', KEYS[1])
			end
			return 0
			""", Long.class);

	private final StringRedisTemplate redis;

	public SessionRegistry(StringRedisTemplate redis) {
		this.redis = redis;
	}

	public UUID create(String context, Instant expiresAt) {
		UUID id = UUID.randomUUID();
		long ttl = Duration.between(Instant.now(), expiresAt).toMillis();
		redis.execute(CREATE, List.of(key(id)), context, Long.toString(ttl));
		return id;
	}

	public Optional<State> find(UUID id) {
		var fields = redis.opsForHash().multiGet(key(id), List.of("generation", "revision", "context"));
		if (fields.get(0) == null) return Optional.empty();
		return Optional.of(new State(Long.parseLong(fields.get(0).toString()),
				Long.parseLong(fields.get(1).toString()), fields.get(2).toString()));
	}

	public boolean isCurrent(SessionBinding binding) {
		return Long.toString(binding.revision()).equals(redis.opsForHash().get(key(binding.id()), "revision"));
	}

	public Rotation rotate(UUID id, long presented, long revision, String operation, long nextRevision, String context) {
		String result = redis.execute(ROTATE, List.of(key(id)), Long.toString(presented),
				Long.toString(revision), operation, Long.toString(nextRevision), context);
		return Rotation.valueOf(result);
	}

	public boolean revokeFamily(UUID id, long generation) {
		return Long.valueOf(1).equals(redis.execute(REVOKE, List.of(key(id)), "generation", Long.toString(generation)));
	}

	public void revokeContext(UUID id, long revision) {
		redis.execute(REVOKE, List.of(key(id)), "revision", Long.toString(revision));
	}

	private String key(UUID id) {
		return PREFIX + id;
	}

	public record State(long generation, long revision, String context) {
	}

	public enum Rotation { CURRENT, NEXT, CONFLICT, INVALID, REUSED }
}
