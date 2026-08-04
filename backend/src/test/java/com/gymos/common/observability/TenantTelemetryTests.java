package com.gymos.common.observability;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TenantTelemetryTests {
	@Test
	void keepsCountersAndTimersIsolatedByOrganization() {
		SimpleMeterRegistry registry = new SimpleMeterRegistry();
		TenantTelemetry telemetry = new TenantTelemetry(registry);
		UUID firstOrganization = UUID.randomUUID();
		UUID secondOrganization = UUID.randomUUID();

		telemetry.increment("gymos.payments.webhooks", firstOrganization, "provider", "STRIPE", "outcome", "processed");
		telemetry.increment("gymos.payments.webhooks", firstOrganization, "provider", "STRIPE", "outcome", "processed");
		telemetry.increment("gymos.payments.webhooks", secondOrganization, "provider", "STRIPE", "outcome", "processed");
		var sample = telemetry.start();
		telemetry.stop(sample, "gymos.automation.execution.duration", firstOrganization, "outcome", "SUCCEEDED");

		assertThat(registry.get("gymos.payments.webhooks")
				.tag("organization_id", firstOrganization.toString()).counter().count()).isEqualTo(2);
		assertThat(registry.get("gymos.payments.webhooks")
				.tag("organization_id", secondOrganization.toString()).counter().count()).isEqualTo(1);
		assertThat(registry.get("gymos.automation.execution.duration")
				.tag("organization_id", firstOrganization.toString()).timer().count()).isEqualTo(1);
	}
}
