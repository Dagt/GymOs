package com.gymos.automations.application;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ScheduledAutomationRunner {
	private final AutomationService automationService;

	public ScheduledAutomationRunner(AutomationService automationService) {
		this.automationService = automationService;
	}

	@Scheduled(fixedDelayString = "${gymos.automation.scheduler-delay-ms:60000}")
	public void runDueRules() {
		automationService.runDueScheduledRules();
		automationService.runDueTriggerEvents();
	}
}
