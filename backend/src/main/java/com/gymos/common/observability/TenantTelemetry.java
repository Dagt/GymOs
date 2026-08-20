package com.gymos.common.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

@Component
public class TenantTelemetry {
	private static final int MAX_TAG_LENGTH = 80;
	private final MeterRegistry registry;
	private final ConcurrentMap<String, AtomicLong> gauges = new ConcurrentHashMap<>();

	public TenantTelemetry(MeterRegistry registry) {
		this.registry = registry;
	}

	public static TenantTelemetry noOp() {
		return new TenantTelemetry(new SimpleMeterRegistry());
	}

	public Timer.Sample start() {
		return Timer.start(registry);
	}

	public void increment(String metric, UUID organizationId, String... tags) {
		if (organizationId == null) return;
		Counter.builder(metric)
				.tags(tags(organizationId, tags))
				.register(registry)
				.increment();
	}

	public void stop(Timer.Sample sample, String metric, UUID organizationId, String... tags) {
		if (sample == null || organizationId == null) return;
		sample.stop(Timer.builder(metric).tags(tags(organizationId, tags)).register(registry));
	}

	public void gauge(String metric, UUID organizationId, long value, String... tags) {
		if (organizationId == null) return;
		List<Tag> meterTags = tags(organizationId, tags);
		String key = metric + "|" + meterTags;
		AtomicLong state = gauges.computeIfAbsent(key, ignored -> {
			AtomicLong created = new AtomicLong();
			Gauge.builder(metric, created, AtomicLong::doubleValue)
					.tags(meterTags)
					.register(registry);
			return created;
		});
		state.set(value);
	}

	private List<Tag> tags(UUID organizationId, String... values) {
		List<Tag> result = new ArrayList<>();
		result.add(Tag.of("organization_id", organizationId.toString()));
		if (values == null) return result;
		if (values.length % 2 != 0) throw new IllegalArgumentException("Telemetry tags must be key/value pairs.");
		for (int index = 0; index < values.length; index += 2) {
			result.add(Tag.of(normalize(values[index]), normalize(values[index + 1])));
		}
		return result;
	}

	private String normalize(String value) {
		if (value == null || value.isBlank()) return "unknown";
		String normalized = value.trim().toLowerCase(Locale.ROOT)
				.replaceAll("[^a-z0-9_.-]", "_");
		return normalized.substring(0, Math.min(MAX_TAG_LENGTH, normalized.length()));
	}
}
