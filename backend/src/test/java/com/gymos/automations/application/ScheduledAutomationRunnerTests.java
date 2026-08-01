package com.gymos.automations.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

import com.gymos.automations.infrastructure.AutomationLeaseRecoveryRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class ScheduledAutomationRunnerTests {
	@Test
	void recoversAndMaterializesDomainEventsBeforeProcessingDueRules() {
		AutomationService service = mock(AutomationService.class);
		AutomationLeaseRecoveryRepository recovery = mock(AutomationLeaseRecoveryRepository.class);
		MembershipExpiryTriggerScanner expiring = mock(MembershipExpiryTriggerScanner.class);
		MembershipExpiredTriggerScanner expired = mock(MembershipExpiredTriggerScanner.class);
		AutomationDomainEventService domainEvents = mock(AutomationDomainEventService.class);
		AutomationMetrics metrics = new AutomationMetrics(new SimpleMeterRegistry());
		ScheduledAutomationRunner runner = new ScheduledAutomationRunner(
				service, recovery, expiring, expired, domainEvents, metrics
		);

		runner.runDueRules();

		InOrder order = inOrder(recovery, expiring, expired, domainEvents, service);
		order.verify(recovery).recoverExpiredLeases(any());
		order.verify(expiring).materialize(any());
		order.verify(expired).materialize(any());
		order.verify(domainEvents).processPending(eq(250));
		order.verify(service).runDueScheduledRules();
	}
}
