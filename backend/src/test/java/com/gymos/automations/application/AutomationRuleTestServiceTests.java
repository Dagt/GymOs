package com.gymos.automations.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymos.automations.infrastructure.AutomationRepository;
import com.gymos.automations.infrastructure.AutomationRepository.AutomationRuleRecord;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.notifications.application.NotificationService;
import com.gymos.notifications.application.NotificationService.CreateNotificationCommand;
import com.gymos.notifications.infrastructure.NotificationRepository.NotificationRecord;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AutomationRuleTestServiceTests {
	@Test
	void emailTestUsesSelectedClientAsRecipientContext() {
		AutomationRepository repository = mock(AutomationRepository.class);
		NotificationService notifications = mock(NotificationService.class);
		AutomationRuleTestService service = new AutomationRuleTestService(repository, notifications, new ObjectMapper());
		UUID organizationId = UUID.randomUUID();
		UUID branchId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		UUID ruleId = UUID.randomUUID();
		UUID clientId = UUID.randomUUID();
		GymOsPrincipal principal = new GymOsPrincipal(
				userId, organizationId, branchId, "manager", "Manager", Set.of("automation.manage")
		);
		AutomationRuleRecord rule = new AutomationRuleRecord(
				organizationId, branchId, ruleId, "Membresia por vencer", "MEMBERSHIP_EXPIRING", "{}",
				"CREATE_NOTIFICATION",
				"{\"channel\":\"EMAIL\",\"category\":\"TRANSACTIONAL\",\"subject\":\"Aviso\",\"body\":\"Hola {{recipientName}}\"}",
				"ENABLED", 3, null, null, null, null, OffsetDateTime.now(), OffsetDateTime.now(), 1
		);
		when(repository.rule(organizationId, branchId, ruleId)).thenReturn(Optional.of(rule));
		when(notifications.create(eq(principal), any(), eq("corr-1"))).thenReturn(mock(NotificationRecord.class));

		service.testEmail(principal, ruleId, clientId, "corr-1");

		ArgumentCaptor<CreateNotificationCommand> command = ArgumentCaptor.forClass(CreateNotificationCommand.class);
		verify(notifications).create(eq(principal), command.capture(), eq("corr-1"));
		assertThat(command.getValue().channel()).isEqualTo("EMAIL");
		assertThat(command.getValue().recipientType()).isEqualTo("CLIENT");
		assertThat(command.getValue().recipientId()).isEqualTo(clientId);
		assertThat(command.getValue().triggerType()).isEqualTo("MANUAL_TEST");
	}
}
