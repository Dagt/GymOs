package com.gymos.automations.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

import com.gymos.automations.infrastructure.AutomationLeaseRecoveryRepository;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class ScheduledAutomationRunnerTests {
	@Test
	void recoversAndMaterializesDomainEventsBeforeProcessingDueRules() {
		AutomationService service = mock(AutomationService.class);
		AutomationLeaseRecoveryRepository recovery = mock(AutomationLeaseRecoveryRepository.class);
		MembershipExpiryTriggerScanner scanner = mock(MembershipExpiryTriggerScanner.class);
		ScheduledAutomationRunner runner = new ScheduledAutomationRunner(service, recovery, scanner);

		runner.runDueRules();

		InOrder order = inOrder(recovery, scanner, service);
		order.verify(recovery).recoverExpiredLeases(any());
		order.verify(scanner).materialize(any());
		order.verify(service).runDueScheduledRules();
	}
}
