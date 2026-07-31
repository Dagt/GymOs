package com.gymos.automations.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymos.automations.infrastructure.AutomationRepository.AutomationRuleRecord;
import com.gymos.common.api.BusinessException;
import com.gymos.notifications.application.NotificationService;
import com.gymos.notifications.application.NotificationService.CreateNotificationCommand;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AutomationActionExecutor {
	private final NotificationService notificationService;
	private final ObjectMapper objectMapper;

	public AutomationActionExecutor(NotificationService notificationService, ObjectMapper objectMapper) {
		this.notificationService = notificationService;
		this.objectMapper = objectMapper;
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public UUID execute(
			AutomationRuleRecord rule,
			UUID actorUserId,
			String correlationId
	) {
		if (!"CREATE_NOTIFICATION".equals(rule.actionType())) {
			throw new BusinessException(
					"AUTOMATION_ACTION_UNSUPPORTED",
					"La accion configurada no esta soportada."
			);
		}
		try {
			JsonNode config = objectMapper.readTree(rule.actionConfigJson());
			return notificationService.createSystem(
					rule.organizationId(),
					rule.branchId(),
					actorUserId,
					new CreateNotificationCommand(
							text(config, "channel", "IN_APP"),
							text(config, "recipient", null),
							text(config, "subject", rule.name()),
							text(config, "body", "Automatizacion ejecutada: " + rule.name()),
							rule.triggerType(),
							"AUTOMATION_RULE",
							rule.id(),
							null
					),
					correlationId
			);
		} catch (JsonProcessingException exception) {
			throw new BusinessException(
					"AUTOMATION_JSON_INVALID",
					"La configuracion JSON de la automatizacion es invalida."
			);
		}
	}

	private String text(JsonNode node, String field, String fallback) {
		JsonNode value = node.get(field);
		return value == null || value.isNull() ? fallback : value.asText();
	}
}
