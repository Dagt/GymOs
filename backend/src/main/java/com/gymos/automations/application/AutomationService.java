package com.gymos.automations.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymos.audit.api.AuditService;
import com.gymos.automations.application.command.CreateAutomationRuleCommand;
import com.gymos.automations.infrastructure.AutomationRepository;
import com.gymos.automations.infrastructure.AutomationRepository.AutomationExecutionRecord;
import com.gymos.automations.infrastructure.AutomationRepository.AutomationRuleRecord;
import com.gymos.automations.infrastructure.AutomationRepository.CreateRuleData;
import com.gymos.common.api.BusinessException;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.notifications.application.NotificationService;
import com.gymos.notifications.application.NotificationService.CreateNotificationCommand;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AutomationService {
	private final AutomationRepository automationRepository;
	private final NotificationService notificationService;
	private final AuditService auditService;
	private final ObjectMapper objectMapper;

	public AutomationService(
			AutomationRepository automationRepository,
			NotificationService notificationService,
			AuditService auditService,
			ObjectMapper objectMapper
	) {
		this.automationRepository = automationRepository;
		this.notificationService = notificationService;
		this.auditService = auditService;
		this.objectMapper = objectMapper;
	}

	@Transactional(readOnly = true)
	public List<AutomationRuleRecord> rules(GymOsPrincipal principal) {
		requireBranch(principal);
		return automationRepository.rules(principal.organizationId(), principal.branchId());
	}

	@Transactional
	public AutomationRuleRecord createRule(GymOsPrincipal principal, CreateAutomationRuleCommand command, String correlationId) {
		requireBranch(principal);
		String conditionJson = validJsonOrDefault(command.conditionJson());
		String actionConfigJson = validJsonOrDefault(command.actionConfigJson());
		UUID ruleId = automationRepository.createRule(
				principal.organizationId(),
				principal.branchId(),
				principal.userId(),
				new CreateRuleData(
						required(command.name(), "AUTOMATION_NAME_REQUIRED", "La automatizacion necesita nombre."),
						required(command.triggerType(), "AUTOMATION_TRIGGER_REQUIRED", "La automatizacion necesita evento.").toUpperCase(),
						conditionJson,
						required(command.actionType(), "AUTOMATION_ACTION_REQUIRED", "La automatizacion necesita accion.").toUpperCase(),
						actionConfigJson,
						Math.max(0, Math.min(command.retryLimit(), 10))
				)
		);
		auditService.record(principal, "AUTOMATION_RULE_CREATED", "AUTOMATION_RULE", ruleId, "BACKOFFICE", null, correlationId);
		return requireRule(principal, ruleId);
	}

	@Transactional
	public AutomationRuleRecord enable(GymOsPrincipal principal, UUID ruleId, String correlationId) {
		requireRule(principal, ruleId);
		automationRepository.updateStatus(principal.organizationId(), principal.branchId(), ruleId, "ENABLED", principal.userId());
		auditService.record(principal, "AUTOMATION_RULE_ENABLED", "AUTOMATION_RULE", ruleId, "BACKOFFICE", null, correlationId);
		return requireRule(principal, ruleId);
	}

	@Transactional
	public AutomationRuleRecord disable(GymOsPrincipal principal, UUID ruleId, String correlationId) {
		requireRule(principal, ruleId);
		automationRepository.updateStatus(principal.organizationId(), principal.branchId(), ruleId, "DISABLED", principal.userId());
		auditService.record(principal, "AUTOMATION_RULE_DISABLED", "AUTOMATION_RULE", ruleId, "BACKOFFICE", null, correlationId);
		return requireRule(principal, ruleId);
	}

	@Transactional
	public AutomationExecutionRecord execute(GymOsPrincipal principal, UUID ruleId, String triggerReference, String correlationId) {
		AutomationRuleRecord rule = requireRule(principal, ruleId);
		if (!"ENABLED".equals(rule.status())) {
			throw new BusinessException("AUTOMATION_RULE_DISABLED", "La automatizacion no esta habilitada.");
		}
		String status = "COMPLETED";
		String error = null;
		try {
			if ("CREATE_NOTIFICATION".equals(rule.actionType())) {
				JsonNode config = objectMapper.readTree(rule.actionConfigJson());
				notificationService.createSystem(
						principal.organizationId(),
						principal.branchId(),
						principal.userId(),
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
			}
		} catch (RuntimeException | JsonProcessingException exception) {
			status = "FAILED";
			error = exception.getMessage();
		}
		UUID executionId = automationRepository.createExecution(principal.organizationId(), principal.branchId(), rule.id(), status, blankToNull(triggerReference), error);
		auditService.record(principal, "AUTOMATION_EXECUTED", "AUTOMATION_EXECUTION", executionId, "BACKOFFICE", status, correlationId);
		return automationRepository.executions(principal.organizationId(), principal.branchId(), rule.id()).stream()
				.filter(execution -> execution.id().equals(executionId))
				.findFirst()
				.orElseThrow();
	}

	@Transactional(readOnly = true)
	public List<AutomationExecutionRecord> executions(GymOsPrincipal principal, UUID ruleId) {
		requireRule(principal, ruleId);
		return automationRepository.executions(principal.organizationId(), principal.branchId(), ruleId);
	}

	private AutomationRuleRecord requireRule(GymOsPrincipal principal, UUID ruleId) {
		requireBranch(principal);
		return automationRepository.rule(principal.organizationId(), principal.branchId(), ruleId)
				.orElseThrow(() -> new BusinessException("AUTOMATION_RULE_NOT_FOUND", "La automatizacion no existe o no esta disponible."));
	}

	private String validJsonOrDefault(String value) {
		String resolved = value == null || value.isBlank() ? "{}" : value.trim();
		try {
			objectMapper.readTree(resolved);
			return resolved;
		} catch (JsonProcessingException exception) {
			throw new BusinessException("AUTOMATION_JSON_INVALID", "La configuracion JSON de la automatizacion es invalida.");
		}
	}

	private String text(JsonNode node, String field, String fallback) {
		JsonNode value = node.get(field);
		return value == null || value.isNull() ? fallback : value.asText();
	}

	private String required(String value, String code, String message) {
		if (value == null || value.isBlank()) {
			throw new BusinessException(code, message);
		}
		return value.trim();
	}

	private String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

	private void requireBranch(GymOsPrincipal principal) {
		if (principal == null) {
			throw new BusinessException("AUTHENTICATION_REQUIRED", "Debes iniciar sesion para continuar.");
		}
		if (principal.branchId() == null) {
			throw new BusinessException("BRANCH_REQUIRED", "Selecciona una sucursal para operar.");
		}
	}
}
