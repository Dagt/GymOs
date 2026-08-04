package com.gymos.automations.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymos.audit.api.AuditService;
import com.gymos.automations.application.command.CreateAutomationRuleCommand;
import com.gymos.automations.infrastructure.AutomationRepository;
import com.gymos.automations.infrastructure.AutomationRepository.AutomationRuleRecord;
import com.gymos.common.api.BusinessException;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.notifications.application.NotificationService;
import java.time.OffsetDateTime;
import java.util.Optional;
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
	void recurringFailureTriggerRejectsInvalidExhaustedFilter() {
		assertThatThrownBy(() -> service.createRule(
				principal,
				new CreateAutomationRuleCommand(
						"Cargo recurrente",
						"RECURRING_CHARGE_FAILED",
						"{\"exhausted\":\"yes\"}",
						"CREATE_NOTIFICATION",
						"{\"body\":\"Revisar vencimiento\"}",
						3
				),
				"correlation"
		))
				.isInstanceOfSatisfying(
						BusinessException.class,
						exception -> assertThat(exception.code())
								.isEqualTo("AUTOMATION_EXHAUSTED_INVALID")
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

	@Test
	void notificationActionRejectsSubjectThatDoesNotFitThePersistedContract() {
		String config = "{\"subject\":\"" + "x".repeat(181) + "\",\"body\":\"Mensaje\"}";

		assertThatThrownBy(() -> service.createRule(
				principal,
				new CreateAutomationRuleCommand(
						"Regla manual", "MANUAL", "{}", "CREATE_NOTIFICATION", config, 0
				),
				"correlation"
		))
				.isInstanceOfSatisfying(
						BusinessException.class,
						exception -> assertThat(exception.code())
								.isEqualTo("AUTOMATION_NOTIFICATION_SUBJECT_INVALID")
				);

		verifyNoInteractions(repository);
	}

	@Test
	void emailRuleWithoutClientEventOrDirectRecipientIsRejected() {
		assertThatThrownBy(() -> service.createRule(
				principal,
				new CreateAutomationRuleCommand(
						"Correo programado", "SCHEDULED", "{\"intervalMinutes\":60}",
						"CREATE_NOTIFICATION",
						"{\"channel\":\"EMAIL\",\"subject\":\"Aviso\",\"body\":\"Mensaje\"}", 3
				), "correlation"
		)).isInstanceOfSatisfying(
				BusinessException.class,
				exception -> assertThat(exception.code()).isEqualTo("AUTOMATION_EMAIL_RECIPIENT_UNAVAILABLE")
		);
		verifyNoInteractions(repository);
	}

	@Test
	void legacyRecurringChargeRuleCanBeEnabledNowThatItsConsumerExists() {
		OffsetDateTime now = OffsetDateTime.now();
		UUID ruleId = UUID.randomUUID();
		AutomationRuleRecord legacyRule = new AutomationRuleRecord(
				principal.organizationId(), principal.branchId(), ruleId, "Cargo heredado",
				"RECURRING_CHARGE_DUE", "{}", "CREATE_NOTIFICATION",
				"{\"body\":\"Revisar vencimiento\"}", "DISABLED", 3, null, null, null,
				null, now.minusDays(1), now, 1L
		);
		when(repository.rule(principal.organizationId(), principal.branchId(), ruleId))
				.thenReturn(Optional.of(legacyRule));

		service.enable(principal, ruleId, "correlation");

		verify(repository).updateStatus(
				principal.organizationId(), principal.branchId(), ruleId, "ENABLED", principal.userId()
		);
	}

	@Test
	void membershipExpiringConditionRejectsAnInvalidDaysBefore() {
		assertThatThrownBy(() -> service.createRule(
				principal,
				new CreateAutomationRuleCommand(
						"Aviso de vencimiento", "MEMBERSHIP_EXPIRING", "{\"daysBefore\":91}",
						"CREATE_NOTIFICATION", "{\"body\":\"Renovar membresía\"}", 3
				),
				"correlation"
		))
				.isInstanceOfSatisfying(
						BusinessException.class,
						exception -> assertThat(exception.code()).isEqualTo("AUTOMATION_DAYS_BEFORE_INVALID")
				);

		verifyNoInteractions(repository);
	}

	@Test
	void typedConditionRejectsUnknownFields() {
		assertThatThrownBy(() -> service.createRule(
				principal,
				new CreateAutomationRuleCommand(
						"Aviso de vencimiento", "MEMBERSHIP_EXPIRED", "{\"unsupported\":true}",
						"CREATE_NOTIFICATION", "{\"body\":\"Membresía vencida\"}", 3
				),
				"correlation"
		))
				.isInstanceOfSatisfying(
						BusinessException.class,
						exception -> assertThat(exception.code())
								.isEqualTo("AUTOMATION_CONDITION_FIELD_UNSUPPORTED")
				);

		verifyNoInteractions(repository);
	}
}
