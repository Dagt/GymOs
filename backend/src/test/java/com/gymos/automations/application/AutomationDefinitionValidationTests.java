package com.gymos.automations.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymos.audit.api.AuditService;
import com.gymos.automations.application.command.CreateAutomationRuleCommand;
import com.gymos.automations.infrastructure.AutomationRepository;
import com.gymos.automations.infrastructure.AutomationRuleSnapshotRepository;
import com.gymos.automations.infrastructure.DurableAutomationRepository;
import com.gymos.common.api.BusinessException;
import com.gymos.common.security.GymOsPrincipal;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AutomationDefinitionValidationTests {
	private final AutomationRepository repository = mock(AutomationRepository.class);
	private final DurableAutomationRepository durableRepository = mock(DurableAutomationRepository.class);
	private final AutomationRuleSnapshotRepository snapshotRepository = mock(AutomationRuleSnapshotRepository.class);
	private final AutomationActionExecutor actionExecutor = mock(AutomationActionExecutor.class);
	private final AutomationService service = new AutomationService(
			repository,
			durableRepository,
			snapshotRepository,
			actionExecutor,
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
	void rejectsUnsupportedEventTrigger() {
		assertThatThrownBy(() -> service.createRule(
				principal,
				new CreateAutomationRuleCommand(
						"Pago confirmado",
						"PAYMENT_COMPLETED",
						"{}",
						"CREATE_NOTIFICATION",
						"{\"body\":\"Revisar pago\"}",
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
	void membershipExpiryRequiresSupportedDayRange() {
		assertThatThrownBy(() -> service.createRule(
				principal,
				new CreateAutomationRuleCommand(
						"Aviso de vencimiento",
						"MEMBERSHIP_EXPIRING",
						"{\"daysBeforeExpiry\":120}",
						"CREATE_NOTIFICATION",
						"{\"body\":\"La membresia vence pronto\"}",
						3
				),
				"correlation"
		))
				.isInstanceOfSatisfying(
						BusinessException.class,
						exception -> assertThat(exception.code())
								.isEqualTo("AUTOMATION_EXPIRY_DAYS_INVALID")
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
