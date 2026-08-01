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

	public ScheduledAutomationRunner(
			AutomationService automationService,
			AutomationLeaseRecoveryRepository leaseRecoveryRepository,
			MembershipExpiryTriggerScanner membershipExpiryScanner
	) {
		this.automationService = automationService;
		this.leaseRecoveryRepository = leaseRecoveryRepository;
		this.membershipExpiryScanner = membershipExpiryScanner;
	}

	@Scheduled(fixedDelayString = "${gymos.automation.scheduler-delay-ms:60000}")
	public void runDueRules() {
		leaseRecoveryRepository.recoverExpiredLeases(OffsetDateTime.now());
		membershipExpiryScanner.materialize(LocalDate.now());
		automationService.runDueScheduledRules();
	}
}
