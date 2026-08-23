package com.gymos.automations.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymos.automations.infrastructure.AutomationRepository;
import com.gymos.automations.infrastructure.AutomationRepository.AutomationRuleRecord;
import com.gymos.common.api.BusinessException;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.notifications.application.NotificationService;
import com.gymos.notifications.application.NotificationService.CreateNotificationCommand;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class AutomationRuleTestService {
	private final AutomationRepository automationRepository;
	private final NotificationService notificationService;
	private final ObjectMapper objectMapper;

	public AutomationRuleTestService(
			AutomationRepository automationRepository,
			NotificationService notificationService,
			ObjectMapper objectMapper
	) {
		this.automationRepository = automationRepository;
		this.notificationService = notificationService;
		this.objectMapper = objectMapper;
	}

	public AutomationEmailTestResult testEmail(
			GymOsPrincipal principal,
			UUID ruleId,
			UUID clientId,
			String correlationId
	) {
		if (principal == null || principal.branchId() == null) {
			throw new BusinessException("BRANCH_REQUIRED", "Selecciona una sucursal para probar la automatizacion.");
		}
		AutomationRuleRecord rule = automationRepository.rule(
				principal.organizationId(), principal.branchId(), ruleId
		).orElseThrow(() -> new BusinessException("AUTOMATION_RULE_NOT_FOUND", "La automatizacion no existe."));
		if (!"ENABLED".equals(rule.status())) {
			throw new BusinessException("AUTOMATION_RULE_DISABLED", "La automatizacion no esta habilitada.");
		}
		if (!"CREATE_NOTIFICATION".equals(rule.actionType())) {
			throw new BusinessException("AUTOMATION_TEST_UNSUPPORTED", "Esta automatizacion no admite una prueba de correo.");
		}

		JsonNode config = actionConfig(rule.actionConfigJson());
		String channel = config.path("channel").asText("IN_APP").toUpperCase();
		if (!"EMAIL".equals(channel)) {
			throw new BusinessException("AUTOMATION_TEST_NOT_EMAIL", "Esta automatizacion no envia correo al socio.");
		}
		String subject = text(config, "subject", rule.name());
		String body = text(config, "body", "Prueba de automatizacion: " + rule.name());
		String category = text(config, "category", "TRANSACTIONAL").toUpperCase();

		NotificationService.NotificationView notification = notificationService.queue(
				principal,
				new CreateNotificationCommand(
						"EMAIL", null, "CLIENT", clientId, subject, body, category,
						"MANUAL_TEST", "AUTOMATION_RULE", rule.id(), null
				),
				correlationId
		);
		return new AutomationEmailTestResult(
				notification.id(), notification.channel(), notification.recipient(), notification.subject(),
				notification.status(), notification.createdAt()
		);
	}

	private JsonNode actionConfig(String json) {
		try {
			return objectMapper.readTree(json == null || json.isBlank() ? "{}" : json);
		} catch (JsonProcessingException exception) {
			throw new BusinessException("AUTOMATION_ACTION_CONFIG_INVALID", "La configuracion de la automatizacion no es valida.");
		}
	}

	private String text(JsonNode node, String field, String fallback) {
		JsonNode value = node.get(field);
		if (value == null || value.isNull() || !value.isValueNode() || value.asText().isBlank()) return fallback;
		return value.asText();
	}

	public record AutomationEmailTestResult(
			UUID notificationId,
			String channel,
			String recipient,
			String subject,
			String status,
			java.time.OffsetDateTime createdAt
	) {
	}
}
