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

	public ScheduledAutomationRunner(
			AutomationService automationService,
			AutomationLeaseRecoveryRepository leaseRecoveryRepository,
			MembershipExpiryTriggerScanner membershipExpiryScanner,
			MembershipExpiredTriggerScanner membershipExpiredScanner,
			AutomationDomainEventService domainEventService
	) {
		this.automationService = automationService;
		this.leaseRecoveryRepository = leaseRecoveryRepository;
		this.membershipExpiryScanner = membershipExpiryScanner;
		this.membershipExpiredScanner = membershipExpiredScanner;
		this.domainEventService = domainEventService;
	}

	@Scheduled(fixedDelayString = "${gymos.automation.scheduler-delay-ms:60000}")
	public void runDueRules() {
		leaseRecoveryRepository.recoverExpiredLeases(OffsetDateTime.now());
		LocalDate today = LocalDate.now();
		membershipExpiryScanner.materialize(today);
		membershipExpiredScanner.materialize(today);
		domainEventService.processPending(250);
		automationService.runDueScheduledRules();
	}
}
