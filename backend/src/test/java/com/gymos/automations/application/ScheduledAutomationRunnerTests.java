package com.gymos.automations.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

import com.gymos.automations.infrastructure.AutomationLeaseRecoveryRepository;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class ScheduledAutomationRunnerTests {
	@Test
	void recoversExpiredLeasesBeforeProcessingDueRules() {
		AutomationService service = mock(AutomationService.class);
		AutomationLeaseRecoveryRepository recovery = mock(AutomationLeaseRecoveryRepository.class);
		ScheduledAutomationRunner runner = new ScheduledAutomationRunner(service, recovery);

		runner.runDueRules();

		InOrder order = inOrder(recovery, service);
		order.verify(recovery).recoverExpiredLeases(any());
		order.verify(service).runDueScheduledRules();
	}
}
