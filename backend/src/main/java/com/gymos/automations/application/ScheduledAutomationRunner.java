package com.gymos.automations.application;

import com.gymos.automations.infrastructure.AutomationLeaseRecoveryRepository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ScheduledAutomationRunner {
	private final AutomationService automationService;
	private final AutomationLeaseRecoveryRepository leaseRecoveryRepository;
	private final MembershipExpiryTriggerScanner membershipExpiryScanner;
	private final MembershipExpiredTriggerScanner membershipExpiredScanner;
	private final AutomationDomainEventService domainEventService;
	private final AutomationMetrics metrics;

	public ScheduledAutomationRunner(
			AutomationService automationService,
			AutomationLeaseRecoveryRepository leaseRecoveryRepository,
			MembershipExpiryTriggerScanner membershipExpiryScanner,
			MembershipExpiredTriggerScanner membershipExpiredScanner,
			AutomationDomainEventService domainEventService,
			AutomationMetrics metrics
	) {
		this.automationService = automationService;
		this.leaseRecoveryRepository = leaseRecoveryRepository;
		this.membershipExpiryScanner = membershipExpiryScanner;
		this.membershipExpiredScanner = membershipExpiredScanner;
		this.domainEventService = domainEventService;
		this.metrics = metrics;
	}

	@Scheduled(fixedDelayString = "${gymos.automation.scheduler-delay-ms:60000}")
	public void runDueRules() {
		metrics.measure("lease_recovery", () -> leaseRecoveryRepository.recoverExpiredLeases(OffsetDateTime.now()));
		LocalDate today = LocalDate.now();
		metrics.measure("membership_expiring", () -> membershipExpiryScanner.materialize(today));
		metrics.measure("membership_expired", () -> membershipExpiredScanner.materialize(today));
		metrics.measure("domain_events", () -> domainEventService.processPending(250));
		metrics.measure("work_queue", automationService::runDueScheduledRules);
	}
}
