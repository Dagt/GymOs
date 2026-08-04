package com.gymos.automations.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymos.audit.api.AuditService;
import com.gymos.automations.application.AutomationService.AutomationRecoveryResult;
import com.gymos.automations.infrastructure.AutomationRepository;
import com.gymos.automations.infrastructure.AutomationRepository.AutomationExecutionRecord;
import com.gymos.automations.infrastructure.AutomationRepository.AutomationRuleRecord;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.notifications.application.NotificationService;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AutomationDeadLetterRecoveryTests {
	private final AutomationRepository repository = mock(AutomationRepository.class);
	private final AuditService auditService = mock(AuditService.class);
	private final AutomationService service = new AutomationService(
			repository, mock(NotificationService.class), auditService, new ObjectMapper()
	);
	private final UUID organizationId = UUID.randomUUID();
	private final UUID branchId = UUID.randomUUID();
	private final GymOsPrincipal principal = new GymOsPrincipal(
			UUID.randomUUID(), organizationId, branchId, "owner@gymos.local", "Owner",
			Set.of("automation.manage")
	);

	@Test
	void retryingDomainExecutionRequeuesItsDurableOccurrence() {
		UUID executionId = UUID.randomUUID();
		UUID ruleId = UUID.randomUUID();
		String reference = "payment-recorded:123";
		OffsetDateTime now = OffsetDateTime.now();
		AutomationExecutionRecord execution = new AutomationExecutionRecord(
				executionId, ruleId, "DEAD_LETTER", reference, "Proveedor no disponible",
				"automation:key", 4, 2L, now, now, now
		);
		AutomationRuleRecord rule = rule(ruleId, "PAYMENT_COMPLETED", "ENABLED", now);
		when(repository.execution(organizationId, branchId, executionId)).thenReturn(Optional.of(execution));
		when(repository.rule(organizationId, branchId, ruleId)).thenReturn(Optional.of(rule));
		when(repository.requeueTriggerEvent(
				organizationId, branchId, "PAYMENT_COMPLETED", reference
		)).thenReturn(true);

		AutomationRecoveryResult result = service.retryDeadLetter(
				principal, executionId, "execution", "Se restablecio el proveedor", "correlation"
		);

		assertThat(result.status()).isEqualTo("RETRY_SCHEDULED");
		verify(repository).updateExecutionStatus(executionId, "RETRY_SCHEDULED");
		verify(auditService).record(
				principal, "AUTOMATION_DEAD_LETTER_RETRIED", "AUTOMATION_EXECUTION",
				executionId, "BACKOFFICE", "Se restablecio el proveedor", "correlation"
		);
	}

	@Test
	void discardingInfrastructureEventRequiresAndPersistsAnOperatorReason() {
		UUID eventId = UUID.randomUUID();
		when(repository.discardTriggerEvent(
				organizationId, branchId, eventId, "Evento invalido confirmado"
		)).thenReturn(true);

		AutomationRecoveryResult result = service.discardDeadLetter(
				principal, eventId, "event", "Evento invalido confirmado", "correlation"
		);

		assertThat(result.status()).isEqualTo("DISCARDED");
		verify(auditService).record(
				principal, "AUTOMATION_DEAD_LETTER_DISCARDED", "AUTOMATION_TRIGGER_EVENT",
				eventId, "BACKOFFICE", "Evento invalido confirmado", "correlation"
		);
	}

	private AutomationRuleRecord rule(UUID id, String triggerType, String status, OffsetDateTime now) {
		return new AutomationRuleRecord(
				organizationId, branchId, id, "Regla", triggerType, "{}", "CREATE_NOTIFICATION",
				"{\"body\":\"Revisar\"}", status, 3, null, null, null, null, now, now, 2L
		);
	}
}
