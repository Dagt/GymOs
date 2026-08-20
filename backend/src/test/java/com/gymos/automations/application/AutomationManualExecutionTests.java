package com.gymos.automations.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymos.audit.api.AuditService;
import com.gymos.automations.infrastructure.AutomationRepository;
import com.gymos.automations.infrastructure.AutomationRepository.AutomationExecutionRecord;
import com.gymos.automations.infrastructure.AutomationRepository.AutomationRuleRecord;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.notifications.application.NotificationService;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AutomationManualExecutionTests {
	@Test
	void callerReferenceIsBothVisibleAndIdempotentForTheManualOccurrence() {
		AutomationRepository repository = mock(AutomationRepository.class);
		NotificationService notifications = mock(NotificationService.class);
		AutomationService service = new AutomationService(
				repository, notifications, mock(AuditService.class), new ObjectMapper()
		);
		UUID organizationId = UUID.randomUUID();
		UUID branchId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		UUID ruleId = UUID.randomUUID();
		UUID executionId = UUID.randomUUID();
		OffsetDateTime now = OffsetDateTime.now();
		AutomationRuleRecord rule = new AutomationRuleRecord(
				organizationId, branchId, ruleId, "Prueba manual", "MANUAL", "{}",
				"CREATE_NOTIFICATION", "{\"body\":\"Revisar prueba\"}", "ENABLED", 2,
				null, null, null, null, now, now, 5L
		);
		GymOsPrincipal principal = new GymOsPrincipal(
				userId, organizationId, branchId, "owner", "Owner", Set.of("automation.execute")
		);
		String reference = "manual-ui:request-123";
		String key = "automation:" + ruleId + ":v5:manual:" + reference;
		AutomationExecutionRecord execution = new AutomationExecutionRecord(
				executionId, ruleId, "SUCCEEDED", reference, null, key, 1, 5L, now, now, now
		);
		when(repository.rule(organizationId, branchId, ruleId)).thenReturn(Optional.of(rule));
		when(repository.startExecution(
				organizationId, branchId, ruleId, 5L, reference, "manual:" + reference, key
		))
				.thenReturn(executionId);
		when(repository.executions(organizationId, branchId, ruleId)).thenReturn(List.of(execution));

		AutomationExecutionRecord result = service.execute(
				principal, ruleId, reference, "correlation-123"
		);

		assertThat(result.id()).isEqualTo(executionId);
		verify(notifications).createSystemIdempotent(
				eq(organizationId), eq(branchId), eq(userId), any(), eq("correlation-123"), eq(key)
		);
		verify(repository).finishExecution(executionId, "SUCCEEDED", null);
	}
}
