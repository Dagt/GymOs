package com.gymos.automations.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymos.audit.api.AuditService;
import com.gymos.automations.infrastructure.AutomationRepository;
import com.gymos.automations.infrastructure.AutomationRepository.AutomationExecutionRecord;
import com.gymos.automations.infrastructure.AutomationRepository.AutomationRuleRecord;
import com.gymos.notifications.application.NotificationService;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AutomationRetryTests {
	private final AutomationRepository repository = mock(AutomationRepository.class);
	private final NotificationService notificationService = mock(NotificationService.class);
	private final AuditService auditService = mock(AuditService.class);
	private final AutomationService service = new AutomationService(
			repository,
			notificationService,
			auditService,
			new ObjectMapper()
	);

	@Test
	void schedulesRetryForSameTriggerWhileRetryBudgetRemains() {
		OffsetDateTime dueAt = OffsetDateTime.now().minusMinutes(1);
		AutomationRuleRecord rule = rule(dueAt, 2, null);
		UUID executionId = UUID.randomUUID();
		String triggerReference = "scheduled:" + rule.id() + ":" + dueAt;
		when(repository.dueScheduledRules(any(), eq(25))).thenReturn(List.of(rule));
		when(repository.claimScheduledRule(eq(rule.id()), any(), any(), eq(triggerReference))).thenReturn(true);
		when(notificationService.createSystemIdempotent(any(), any(), any(), any(), anyString(), anyString()))
				.thenThrow(new IllegalStateException("provider unavailable"));
		when(repository.startExecution(any(), any(), eq(rule.id()), anyLong(), eq(triggerReference), eq(triggerReference), anyString()))
				.thenReturn(executionId);
		when(repository.executions(rule.organizationId(), rule.branchId(), rule.id()))
				.thenReturn(List.of(execution(executionId, rule, triggerReference, 1)));

		assertThat(service.runDueScheduledRules()).isEqualTo(1);

		verify(repository).updateExecutionStatus(executionId, "RETRY_SCHEDULED");
		verify(repository).finishExecution(executionId, "FAILED", "provider unavailable");
		verify(repository).rescheduleRule(eq(rule.id()), any(), eq(false));
	}

	@Test
	void deadLettersTriggerAfterConfiguredRetriesAreExhausted() {
		OffsetDateTime dueAt = OffsetDateTime.now().minusMinutes(1);
		String triggerReference = "scheduled:fixed-trigger";
		AutomationRuleRecord rule = rule(dueAt, 2, triggerReference);
		UUID executionId = UUID.randomUUID();
		when(repository.dueScheduledRules(any(), eq(25))).thenReturn(List.of(rule));
		when(repository.claimScheduledRule(eq(rule.id()), any(), any(), eq(triggerReference))).thenReturn(true);
		when(notificationService.createSystemIdempotent(any(), any(), any(), any(), anyString(), anyString()))
				.thenThrow(new IllegalStateException("provider unavailable"));
		when(repository.startExecution(any(), any(), eq(rule.id()), anyLong(), eq(triggerReference), eq(triggerReference), anyString()))
				.thenReturn(executionId);
		when(repository.executions(rule.organizationId(), rule.branchId(), rule.id()))
				.thenReturn(List.of(execution(executionId, rule, triggerReference, 3)));

		assertThat(service.runDueScheduledRules()).isEqualTo(1);

		verify(repository).updateExecutionStatus(executionId, "DEAD_LETTER");
		verify(repository).finishExecution(executionId, "FAILED", "provider unavailable");
		verify(repository).rescheduleRule(eq(rule.id()), any(), eq(true));
	}

	private AutomationRuleRecord rule(OffsetDateTime dueAt, int retryLimit, String triggerReference) {
		return new AutomationRuleRecord(
				UUID.randomUUID(),
				UUID.randomUUID(),
				UUID.randomUUID(),
				"Recordatorio",
				"SCHEDULED",
				"{\"intervalMinutes\":60}",
				"CREATE_NOTIFICATION",
				"{\"body\":\"Hola\"}",
				"ENABLED",
				retryLimit,
				"interval:60",
				dueAt,
				null,
				triggerReference,
				OffsetDateTime.now().minusDays(1),
				OffsetDateTime.now(),
				1L
		);
	}

	private AutomationExecutionRecord execution(
			UUID executionId,
			AutomationRuleRecord rule,
			String triggerReference,
			int attempt
	) {
		OffsetDateTime now = OffsetDateTime.now();
		return new AutomationExecutionRecord(
				executionId, rule.id(), "FAILED", triggerReference, "provider unavailable",
				"automation:test", attempt, rule.version(), now, now, now
		);
	}
}
