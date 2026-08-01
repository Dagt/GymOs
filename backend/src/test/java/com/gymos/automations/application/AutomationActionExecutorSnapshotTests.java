package com.gymos.automations.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymos.automations.infrastructure.AutomationRuleSnapshotRepository.AutomationRuleSnapshot;
import com.gymos.notifications.application.NotificationService;
import com.gymos.notifications.application.NotificationService.CreateNotificationCommand;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AutomationActionExecutorSnapshotTests {
	@Test
	void usesCapturedConfigurationAndOccurrenceVariables() {
		NotificationService notifications = mock(NotificationService.class);
		AutomationActionExecutor executor = new AutomationActionExecutor(notifications, new ObjectMapper());
		UUID notificationId = UUID.randomUUID();
		when(notifications.createSystem(any(), any(), any(), any(), any())).thenReturn(notificationId);
		UUID ruleId = UUID.randomUUID();
		AutomationRuleSnapshot snapshot = new AutomationRuleSnapshot(
				UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), ruleId, 4L,
				"Aviso capturado", "MEMBERSHIP_EXPIRING", "{\"daysBeforeExpiry\":5}",
				"CREATE_NOTIFICATION",
				"{\"subject\":\"Vencimiento\",\"body\":\"Vence {{endsOn}}\",\"variables\":{\"endsOn\":\"2026-08-06\"}}",
				3, OffsetDateTime.now()
		);

		UUID result = executor.execute(snapshot, null, "correlation");

		assertThat(result).isEqualTo(notificationId);
		ArgumentCaptor<CreateNotificationCommand> command = ArgumentCaptor.forClass(CreateNotificationCommand.class);
		verify(notifications).createSystem(
				eq(snapshot.organizationId()), eq(snapshot.branchId()), isNull(),
				command.capture(), eq("correlation")
		);
		assertThat(command.getValue().subject()).isEqualTo("Vencimiento");
		assertThat(command.getValue().body()).isEqualTo("Vence 2026-08-06");
		assertThat(command.getValue().referenceId()).isEqualTo(ruleId);
	}
}
