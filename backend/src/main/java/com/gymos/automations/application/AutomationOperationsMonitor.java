package com.gymos.automations.application;

import com.gymos.automations.infrastructure.AutomationRepository;
import com.gymos.common.observability.TenantTelemetry;
import java.time.OffsetDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AutomationOperationsMonitor {
	private static final Logger LOGGER = LoggerFactory.getLogger(AutomationOperationsMonitor.class);
	private final AutomationRepository repository;
	private final TenantTelemetry telemetry;

	public AutomationOperationsMonitor(AutomationRepository repository, TenantTelemetry telemetry) {
		this.repository = repository;
		this.telemetry = telemetry;
	}

	@Scheduled(fixedDelayString = "${gymos.automation.operations-monitor-delay-ms:60000}")
	public void publish() {
		OffsetDateTime now = OffsetDateTime.now();
		for (var context : repository.operationContexts()) {
			var state = repository.operations(context.organizationId(), context.branchId(), now);
			String branch = context.branchId().toString();
			long deadLetters = state.deadLetterEvents() + state.deadLetterExecutions();
			telemetry.gauge("gymos.automation.backlog", context.organizationId(), state.pendingEvents(), "branch_id", branch, "status", "pending");
			telemetry.gauge("gymos.automation.backlog", context.organizationId(), state.retryEvents(), "branch_id", branch, "status", "retry_scheduled");
			telemetry.gauge("gymos.automation.backlog", context.organizationId(), deadLetters, "branch_id", branch, "status", "dead_letter");
			telemetry.gauge("gymos.automation.expired_leases", context.organizationId(), state.expiredLeases(), "branch_id", branch);
			if (deadLetters > 0 || state.expiredLeases() > 0) {
				LOGGER.warn(
						"Automation operational alert organizationId={} branchId={} deadLetters={} expiredLeases={}",
						context.organizationId(), context.branchId(), deadLetters, state.expiredLeases()
				);
			}
		}
	}
}
