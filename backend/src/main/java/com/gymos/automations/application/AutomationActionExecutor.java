package com.gymos.automations.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymos.automations.infrastructure.AutomationRuleSnapshotRepository.AutomationRuleSnapshot;
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
			AutomationRuleSnapshot snapshot,
			UUID actorUserId,
			String correlationId
	) {
		if (!"CREATE_NOTIFICATION".equals(snapshot.actionType())) {
			throw new BusinessException(
					"AUTOMATION_ACTION_UNSUPPORTED",
					"La accion configurada no esta soportada."
			);
		}
		try {
			JsonNode config = objectMapper.readTree(snapshot.actionConfigJson());
			return notificationService.createSystem(
					snapshot.organizationId(),
					snapshot.branchId(),
					actorUserId,
					new CreateNotificationCommand(
							text(config, "channel", "IN_APP"),
							text(config, "recipient", null),
							text(config, "subject", snapshot.ruleName()),
							renderBody(text(config, "body", "Automatizacion ejecutada: " + snapshot.ruleName()), config),
							snapshot.triggerType(),
							"AUTOMATION_RULE",
							snapshot.ruleId(),
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

	private String renderBody(String template, JsonNode config) {
		String body = template;
		JsonNode variables = config.path("variables");
		if (variables.isObject()) {
			var fields = variables.fields();
			while (fields.hasNext()) {
				var entry = fields.next();
				body = body.replace("{{" + entry.getKey() + "}}", entry.getValue().asText(""));
			}
		}
		return body;
	}

	private String text(JsonNode node, String field, String fallback) {
		JsonNode value = node.get(field);
		return value == null || value.isNull() ? fallback : value.asText();
	}
}
