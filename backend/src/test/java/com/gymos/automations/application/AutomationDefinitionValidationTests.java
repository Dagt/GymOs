package com.gymos.automations.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymos.audit.api.AuditService;
import com.gymos.automations.application.command.CreateAutomationRuleCommand;
import com.gymos.automations.infrastructure.AutomationRepository;
import com.gymos.common.api.BusinessException;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.notifications.application.NotificationService;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AutomationDefinitionValidationTests {
	private final AutomationRepository repository = mock(AutomationRepository.class);
	private final AutomationService service = new AutomationService(
			repository,
			mock(NotificationService.class),
			mock(AuditService.class),
			new ObjectMapper()
	);
	private final GymOsPrincipal principal = new GymOsPrincipal(
			UUID.randomUUID(),
			UUID.randomUUID(),
			UUID.randomUUID(),
			"owner@gymos.local",
			"Owner",
			Set.of("automation.manage")
	);

	@Test
	void rejectsEventTriggerUntilARealConsumerExists() {
		assertThatThrownBy(() -> service.createRule(
				principal,
				new CreateAutomationRuleCommand(
						"Aviso de vencimiento",
						"MEMBERSHIP_EXPIRING",
						"{\"daysBefore\":3}",
						"CREATE_NOTIFICATION",
						"{\"body\":\"Revisar vencimiento\"}",
						3
				),
				"correlation"
		))
				.isInstanceOfSatisfying(
						BusinessException.class,
						exception -> assertThat(exception.code())
								.isEqualTo("AUTOMATION_TRIGGER_UNSUPPORTED")
				);

		verifyNoInteractions(repository);
	}

	@Test
	void notificationActionRequiresMessage() {
		assertThatThrownBy(() -> service.createRule(
				principal,
				new CreateAutomationRuleCommand(
						"Regla manual",
						"MANUAL",
						"{}",
						"CREATE_NOTIFICATION",
						"{\"subject\":\"Sin cuerpo\"}",
						0
				),
				"correlation"
		))
				.isInstanceOfSatisfying(
						BusinessException.class,
						exception -> assertThat(exception.code())
								.isEqualTo("AUTOMATION_NOTIFICATION_BODY_REQUIRED")
				);

		verifyNoInteractions(repository);
	}
}
