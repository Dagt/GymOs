package com.gymos.automations.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymos.audit.api.AuditService;
import com.gymos.automations.infrastructure.AutomationRepository;
import com.gymos.automations.infrastructure.AutomationRepository.AutomationExecutionRecord;
import com.gymos.automations.infrastructure.AutomationRepository.AutomationRuleRecord;
import com.gymos.automations.infrastructure.AutomationRepository.AutomationTriggerEventRecord;
import com.gymos.notifications.application.NotificationService;
import com.gymos.notifications.application.NotificationService.CreateNotificationCommand;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AutomationDomainEventTests {
	private final AutomationRepository repository = mock(AutomationRepository.class);
	private final NotificationService notifications = mock(NotificationService.class);
	private final AuditService audit = mock(AuditService.class);
	private final AutomationService service = new AutomationService(repository, notifications, audit, new ObjectMapper());

	@Test
	void paymentEventExecutesEnabledRuleAndCompletesInboxOccurrence() {
		AutomationRuleRecord rule = rule("PAYMENT_COMPLETED", 7L);
		AutomationTriggerEventRecord event = event("PAYMENT_COMPLETED", "payment-completed:sale-1");
		String key = "automation:" + rule.id() + ":v7:payment-completed:sale-1";
		UUID executionId = UUID.randomUUID();
		OffsetDateTime now = OffsetDateTime.now();
		AutomationExecutionRecord execution = new AutomationExecutionRecord(
				executionId, rule.id(), "SUCCEEDED", event.triggerReference(), null, key,
				1, rule.version(), now, now, now
		);
		when(repository.dueTriggerEvents(any(), eq(50))).thenReturn(List.of(event));
		when(repository.claimTriggerEvent(eq(event.id()), any(), any())).thenReturn(true);
		when(repository.enabledRulesForTrigger(event.organizationId(), event.branchId(), event.triggerType()))
				.thenReturn(List.of(rule));
		when(repository.executionForOccurrence(rule.organizationId(), rule.id(), event.triggerReference()))
				.thenReturn(Optional.empty());
		when(repository.startExecution(
				eq(rule.organizationId()), eq(rule.branchId()), eq(rule.id()), eq(rule.version()),
				eq(event.triggerReference()), eq(event.triggerReference()), eq(key)
		)).thenReturn(executionId);
		when(repository.executions(rule.organizationId(), rule.branchId(), rule.id())).thenReturn(List.of(execution));

		assertThat(service.runDueTriggerEvents()).isEqualTo(1);

		verify(notifications).createSystemIdempotent(any(), any(), any(), any(), eq(event.triggerReference()), eq(key));
		verify(repository).finishExecution(executionId, "SUCCEEDED", null);
		verify(repository).finishTriggerEvent(eq(event.id()), eq("COMPLETED"), any(), eq(null));
	}

	@Test
	void completedRuleExecutionIsNotRepeatedWhenInboxLeaseExpires() {
		AutomationRuleRecord rule = rule("BOOKING_CONFIRMED", 3L);
		AutomationTriggerEventRecord event = event("BOOKING_CONFIRMED", "booking-status:1:CONFIRMED");
		String key = "automation:" + rule.id() + ":v3:booking-status:1:CONFIRMED";
		OffsetDateTime now = OffsetDateTime.now();
		AutomationExecutionRecord previous = new AutomationExecutionRecord(
				UUID.randomUUID(), rule.id(), "SUCCEEDED", event.triggerReference(), null, key,
				1, rule.version(), now, now, now
		);
		when(repository.dueTriggerEvents(any(), eq(50))).thenReturn(List.of(event));
		when(repository.claimTriggerEvent(eq(event.id()), any(), any())).thenReturn(true);
		when(repository.enabledRulesForTrigger(event.organizationId(), event.branchId(), event.triggerType()))
				.thenReturn(List.of(rule));
		when(repository.executionForOccurrence(rule.organizationId(), rule.id(), event.triggerReference()))
				.thenReturn(Optional.of(previous));

		assertThat(service.runDueTriggerEvents()).isEqualTo(1);

		verify(notifications, never()).createSystemIdempotent(any(), any(), any(), any(), anyString(), anyString());
		verify(repository).finishTriggerEvent(eq(event.id()), eq("COMPLETED"), any(), eq(null));
	}

	@Test
	void retryKeepsTheOriginalRuleVersionInItsIdempotencyKey() {
		AutomationRuleRecord currentRule = rule("PAYMENT_COMPLETED", 8L);
		AutomationTriggerEventRecord event = event("PAYMENT_COMPLETED", "payment-completed:sale-retry");
		String originalKey = "automation:" + currentRule.id() + ":v7:payment-completed:sale-retry";
		OffsetDateTime now = OffsetDateTime.now();
		AutomationExecutionRecord previous = new AutomationExecutionRecord(
				UUID.randomUUID(), currentRule.id(), "RETRY_SCHEDULED", event.triggerReference(),
				"provider unavailable", originalKey, 1, 7L, now, now, now
		);
		UUID executionId = previous.id();
		AutomationExecutionRecord succeeded = new AutomationExecutionRecord(
				executionId, currentRule.id(), "SUCCEEDED", event.triggerReference(), null,
				originalKey, 2, 7L, now, now, now
		);
		when(repository.dueTriggerEvents(any(), eq(50))).thenReturn(List.of(event));
		when(repository.claimTriggerEvent(eq(event.id()), any(), any())).thenReturn(true);
		when(repository.enabledRulesForTrigger(event.organizationId(), event.branchId(), event.triggerType()))
				.thenReturn(List.of(currentRule));
		when(repository.executionForOccurrence(currentRule.organizationId(), currentRule.id(), event.triggerReference()))
				.thenReturn(Optional.of(previous));
		when(repository.startExecution(
				eq(currentRule.organizationId()), eq(currentRule.branchId()), eq(currentRule.id()),
				eq(currentRule.version()), eq(event.triggerReference()), eq(event.triggerReference()), eq(originalKey)
		)).thenReturn(executionId);
		when(repository.executions(currentRule.organizationId(), currentRule.branchId(), currentRule.id()))
				.thenReturn(List.of(succeeded));

		assertThat(service.runDueTriggerEvents()).isEqualTo(1);

		verify(notifications).createSystemIdempotent(
				any(), any(), any(), any(), eq(event.triggerReference()), eq(originalKey)
		);
		verify(repository).finishExecution(executionId, "SUCCEEDED", null);
		verify(repository).finishTriggerEvent(eq(event.id()), eq("COMPLETED"), any(), eq(null));
	}

	@Test
	void aPreviouslyDeadLetteredRuleKeepsTheInboxOccurrenceFailed() {
		AutomationRuleRecord rule = rule("BOOKING_CANCELLED", 4L);
		AutomationTriggerEventRecord event = event("BOOKING_CANCELLED", "booking-status:1:CANCELLED");
		OffsetDateTime now = OffsetDateTime.now();
		AutomationExecutionRecord previous = new AutomationExecutionRecord(
				UUID.randomUUID(), rule.id(), "DEAD_LETTER", event.triggerReference(), "permanent failure",
				"automation:dead-letter", 4, rule.version(), now, now, now
		);
		when(repository.dueTriggerEvents(any(), eq(50))).thenReturn(List.of(event));
		when(repository.claimTriggerEvent(eq(event.id()), any(), any())).thenReturn(true);
		when(repository.enabledRulesForTrigger(event.organizationId(), event.branchId(), event.triggerType()))
				.thenReturn(List.of(rule));
		when(repository.executionForOccurrence(rule.organizationId(), rule.id(), event.triggerReference()))
				.thenReturn(Optional.of(previous));

		assertThat(service.runDueTriggerEvents()).isEqualTo(1);

		verify(notifications, never()).createSystemIdempotent(any(), any(), any(), any(), anyString(), anyString());
		verify(repository).finishTriggerEvent(
				eq(event.id()), eq("DEAD_LETTER"), any(), eq("Seguimiento: permanent failure")
		);
	}

	@Test
	void infrastructureFailureStopsAfterTheInboxRetryLimit() {
		OffsetDateTime now = OffsetDateTime.now();
		AutomationTriggerEventRecord event = new AutomationTriggerEventRecord(
				UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "PAYMENT_COMPLETED",
				"payment-completed:infrastructure", "{}", "RETRY_SCHEDULED", 9, now, null, null
		);
		when(repository.dueTriggerEvents(any(), eq(50))).thenReturn(List.of(event));
		when(repository.claimTriggerEvent(eq(event.id()), any(), any())).thenReturn(true);
		when(repository.enabledRulesForTrigger(event.organizationId(), event.branchId(), event.triggerType()))
				.thenThrow(new IllegalStateException(
						"authorization=secret-token owner@example.com 4111111111111111"
				));

		assertThat(service.runDueTriggerEvents()).isZero();

		verify(repository).finishTriggerEvent(
				eq(event.id()), eq("DEAD_LETTER"), any(),
				eq("authorization=[REDACTED] [EMAIL] [CARD]")
		);
	}

	@Test
	void expiringMembershipConditionUsesStableMembershipPeriodOccurrence() {
		OffsetDateTime now = OffsetDateTime.now();
		UUID membershipId = UUID.randomUUID();
		UUID clientId = UUID.randomUUID();
		String occurrence = "membership-expiring:" + membershipId + ":2026-08-06";
		AutomationRuleRecord rule = new AutomationRuleRecord(
				UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "Vence en tres dias",
				"MEMBERSHIP_EXPIRING", "{\"daysBefore\":3}", "CREATE_NOTIFICATION",
				"{\"channel\":\"EMAIL\",\"subject\":\"Vence {{endsOn}}\",\"body\":\"Hola {{recipientName}}\"}", "ENABLED", 3,
				null, null, null, null, now, now, 2L
		);
		AutomationTriggerEventRecord event = new AutomationTriggerEventRecord(
				UUID.randomUUID(), rule.organizationId(), rule.branchId(), "MEMBERSHIP_EXPIRING",
				"membership-expiring:" + membershipId + ":2026-08-04",
				"{\"occurrenceKey\":\"" + occurrence + "\",\"daysUntilEnd\":2,\"clientId\":\"" + clientId + "\",\"endsOn\":\"2026-08-06\"}",
				"PENDING", 0, now, null, null
		);
		String key = "automation:" + rule.id() + ":v2:" + occurrence;
		UUID executionId = UUID.randomUUID();
		AutomationExecutionRecord execution = new AutomationExecutionRecord(
				executionId, rule.id(), "SUCCEEDED", event.triggerReference(), null, key,
				1, rule.version(), now, now, now
		);
		when(repository.dueTriggerEvents(any(), eq(50))).thenReturn(List.of(event));
		when(repository.claimTriggerEvent(eq(event.id()), any(), any())).thenReturn(true);
		when(repository.enabledRulesForTrigger(event.organizationId(), event.branchId(), event.triggerType()))
				.thenReturn(List.of(rule));
		when(repository.executionForOccurrence(rule.organizationId(), rule.id(), occurrence))
				.thenReturn(Optional.empty());
		when(repository.startExecution(
				rule.organizationId(), rule.branchId(), rule.id(), rule.version(),
				event.triggerReference(), occurrence, key
		)).thenReturn(executionId);
		when(repository.executions(rule.organizationId(), rule.branchId(), rule.id()))
				.thenReturn(List.of(execution));

		assertThat(service.runDueTriggerEvents()).isEqualTo(1);

		ArgumentCaptor<CreateNotificationCommand> command = ArgumentCaptor.forClass(CreateNotificationCommand.class);
		verify(notifications).createSystemIdempotent(
				any(), any(), any(), command.capture(), eq(event.triggerReference()), eq(key)
		);
		assertThat(command.getValue().channel()).isEqualTo("EMAIL");
		assertThat(command.getValue().recipientType()).isEqualTo("CLIENT");
		assertThat(command.getValue().recipientId()).isEqualTo(clientId);
		assertThat(command.getValue().subject()).isEqualTo("Vence 2026-08-06");
	}

	@Test
	void paymentConditionCompletesTheEventWithoutExecutingNonMatchingRules() {
		OffsetDateTime now = OffsetDateTime.now();
		AutomationRuleRecord rule = new AutomationRuleRecord(
				UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "Transferencias relevantes",
				"PAYMENT_PENDING_VALIDATION", "{\"method\":\"TRANSFER\",\"minimumAmountCents\":10000}",
				"CREATE_NOTIFICATION", "{\"body\":\"Validar transferencia\"}", "ENABLED", 3,
				null, null, null, null, now, now, 1L
		);
		AutomationTriggerEventRecord event = new AutomationTriggerEventRecord(
				UUID.randomUUID(), rule.organizationId(), rule.branchId(), "PAYMENT_PENDING_VALIDATION",
				"payment-recorded:small-card", "{\"method\":\"CARD_MANUAL\",\"amountCents\":5000}",
				"PENDING", 0, now, null, null
		);
		when(repository.dueTriggerEvents(any(), eq(50))).thenReturn(List.of(event));
		when(repository.claimTriggerEvent(eq(event.id()), any(), any())).thenReturn(true);
		when(repository.enabledRulesForTrigger(event.organizationId(), event.branchId(), event.triggerType()))
				.thenReturn(List.of(rule));

		assertThat(service.runDueTriggerEvents()).isEqualTo(1);

		verify(repository, never()).startExecution(any(), any(), any(), anyLong(), any(), any(), any());
		verify(repository).finishTriggerEvent(eq(event.id()), eq("COMPLETED"), any(), eq(null));
	}

	private AutomationRuleRecord rule(String triggerType, long version) {
		OffsetDateTime now = OffsetDateTime.now();
		return new AutomationRuleRecord(
				UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "Seguimiento",
				triggerType, "{}", "CREATE_NOTIFICATION", "{\"body\":\"Revisar evento\"}",
				"ENABLED", 3, null, null, null, null, now.minusDays(1), now, version
		);
	}

	private AutomationTriggerEventRecord event(String triggerType, String reference) {
		OffsetDateTime now = OffsetDateTime.now();
		return new AutomationTriggerEventRecord(
				UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), triggerType, reference,
				"{}", "PENDING", 0, now, null, null
		);
	}
}
