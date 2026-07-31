package com.gymos.automations.application;

import com.gymos.automations.infrastructure.AutomationLeaseRecoveryRepository;
import java.time.OffsetDateTime;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ScheduledAutomationRunner {
	private final AutomationService automationService;
	private final AutomationLeaseRecoveryRepository leaseRecoveryRepository;

	public ScheduledAutomationRunner(
			AutomationService automationService,
			AutomationLeaseRecoveryRepository leaseRecoveryRepository
	) {
		this.automationService = automationService;
		this.leaseRecoveryRepository = leaseRecoveryRepository;
	}

	@Scheduled(fixedDelayString = "${gymos.automation.scheduler-delay-ms:60000}")
	public void runDueRules() {
		leaseRecoveryRepository.recoverExpiredLeases(OffsetDateTime.now());
		automationService.runDueScheduledRules();
	}
}
