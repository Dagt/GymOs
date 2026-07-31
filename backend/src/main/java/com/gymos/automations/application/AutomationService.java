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
import com.gymos.automations.infrastructure.DurableAutomationRepository;
import com.gymos.automations.infrastructure.DurableAutomationRepository.WorkItemRecord;
import com.gymos.common.api.BusinessException;
import com.gymos.common.security.GymOsPrincipal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AutomationService {
	private static final Set<String> SUPPORTED_TRIGGERS = Set.of("MANUAL", "SCHEDULED");
	private static final Set<String> SUPPORTED_ACTIONS = Set.of("CREATE_NOTIFICATION");
	private static final Duration WORK_LEASE = Duration.ofMinutes(5);

	private final AutomationRepository automationRepository;
	private final DurableAutomationRepository durableRepository;
	private final AutomationActionExecutor actionExecutor;
	private final AuditService auditService;
	private final ObjectMapper objectMapper;

	public AutomationService(
			AutomationRepository automationRepository,
			DurableAutomationRepository durableRepository,
			AutomationActionExecutor actionExecutor,
			AuditService auditService,
			ObjectMapper objectMapper
	) {
		this.automationRepository = automationRepository;
		this.durableRepository = durableRepository;
		this.actionExecutor = actionExecutor;
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
		String triggerType = required(command.triggerType(), "AUTOMATION_TRIGGER_REQUIRED", "La automatizacion necesita evento.").toUpperCase();
		String actionType = required(command.actionType(), "AUTOMATION_ACTION_REQUIRED", "La automatizacion necesita accion.").toUpperCase();
		requireSupportedDefinition(triggerType, actionType);
		validateActionConfig(actionType, actionConfigJson);
		int intervalMinutes = intervalMinutes(conditionJson);
		UUID ruleId = automationRepository.createRule(
				principal.organizationId(),
				principal.branchId(),
				principal.userId(),
				new CreateRuleData(
						required(command.name(), "AUTOMATION_NAME_REQUIRED", "La automatizacion necesita nombre."),
						triggerType,
						conditionJson,
						actionType,
						actionConfigJson,
						Math.max(0, Math.min(command.retryLimit(), 10)),
						"SCHEDULED".equals(triggerType) ? "interval:" + intervalMinutes : null,
						"SCHEDULED".equals(triggerType) ? OffsetDateTime.now().plusMinutes(intervalMinutes) : null
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

	public AutomationExecutionRecord execute(GymOsPrincipal principal, UUID ruleId, String triggerReference, String correlationId) {
		AutomationRuleRecord rule = requireRule(principal, ruleId);
		if (!"ENABLED".equals(rule.status())) {
			throw new BusinessException("AUTOMATION_RULE_DISABLED", "La automatizacion no esta habilitada.");
		}
		String reference = blankToNull(triggerReference);
		String occurrence = reference == null ? UUID.randomUUID().toString() : reference;
		WorkItemRecord workItem = durableRepository.enqueue(
				rule.organizationId(),
				rule.branchId(),
				rule.id(),
				"manual:" + rule.id() + ":" + occurrence,
				reference,
				rule.retryLimit() + 1,
				OffsetDateTime.now()
		);
		return processWorkItem(workItem.id(), principal.userId(), correlationId, "BACKOFFICE");
	}

	public int runDueScheduledRules() {
		OffsetDateTime now = OffsetDateTime.now();
		for (AutomationRuleRecord rule : automationRepository.dueScheduledRules(now, 25)) {
			if (!automationRepository.claimScheduledRule(rule.id(), now, now.plus(WORK_LEASE))) {
				continue;
			}
			OffsetDateTime occurrenceAt = rule.nextRunAt() == null ? now : rule.nextRunAt();
			try {
				durableRepository.enqueue(
						rule.organizationId(),
						rule.branchId(),
						rule.id(),
						"scheduled:" + rule.id() + ":" + occurrenceAt,
						"scheduled:" + occurrenceAt,
						rule.retryLimit() + 1,
						occurrenceAt
				);
			} finally {
				automationRepository.rescheduleRule(
						rule.id(),
						OffsetDateTime.now().plusMinutes(intervalMinutes(rule.conditionJson()))
				);
			}
		}

		int processed = 0;
		for (WorkItemRecord workItem : durableRepository.due(OffsetDateTime.now(), 25)) {
			processWorkItem(workItem.id(), null, "automation-scheduler", "SCHEDULER");
			processed++;
		}
		return processed;
	}

	private AutomationExecutionRecord processWorkItem(
			UUID workItemId,
			UUID actorUserId,
			String correlationId,
			String auditOrigin
	) {
		WorkItemRecord current = durableRepository.workItem(workItemId)
				.orElseThrow(() -> new BusinessException("AUTOMATION_WORK_ITEM_NOT_FOUND", "La ocurrencia de automatizacion no existe."));
		if ("SUCCEEDED".equals(current.status()) || "DEAD_LETTER".equals(current.status())) {
			return latestExecution(current);
		}

		OffsetDateTime now = OffsetDateTime.now();
		if (!durableRepository.claim(current.id(), now, now.plus(WORK_LEASE))) {
			WorkItemRecord refreshed = durableRepository.workItem(current.id()).orElseThrow();
			return latestExecution(refreshed);
		}

		WorkItemRecord claimed = durableRepository.workItem(current.id()).orElseThrow();
		String executionStatus;
		String error = null;
		try {
			AutomationRuleRecord rule = automationRepository.rule(
					claimed.organizationId(),
					claimed.branchId(),
					claimed.ruleId()
			).orElseThrow(() -> new BusinessException("AUTOMATION_RULE_NOT_FOUND", "La automatizacion no existe o no esta disponible."));
			UUID effectReferenceId = claimed.effectReferenceId();
			if (effectReferenceId == null) {
				effectReferenceId = actionExecutor.execute(rule, actorUserId, correlationId);
			}
			durableRepository.markSucceeded(claimed.id(), effectReferenceId);
			executionStatus = "SUCCEEDED";
		} catch (RuntimeException exception) {
			error = exception.getMessage();
			if (claimed.attemptCount() >= claimed.maxAttempts()) {
				durableRepository.markDeadLetter(claimed.id(), error);
				executionStatus = "DEAD_LETTER";
			} else {
				durableRepository.markRetry(claimed.id(), nextAttemptAt(claimed.attemptCount()), error);
				executionStatus = "RETRY_SCHEDULED";
			}
		}

		UUID executionId = automationRepository.createExecution(
				claimed.organizationId(),
				claimed.branchId(),
				claimed.ruleId(),
				executionStatus,
				claimed.triggerReference(),
				error
		);
		auditService.recordSystem(
				claimed.organizationId(),
				claimed.branchId(),
				actorUserId,
				"AUTOMATION_EXECUTED",
				"AUTOMATION_EXECUTION",
				executionId,
				auditOrigin,
				executionStatus,
				correlationId
		);
		return automationRepository.executions(claimed.organizationId(), claimed.branchId(), claimed.ruleId()).stream()
				.filter(execution -> execution.id().equals(executionId))
				.findFirst()
				.orElseThrow();
	}

	private AutomationExecutionRecord latestExecution(WorkItemRecord workItem) {
		return automationRepository.executions(workItem.organizationId(), workItem.branchId(), workItem.ruleId()).stream()
				.filter(execution -> {
					if (workItem.triggerReference() == null) return execution.triggerReference() == null;
					return workItem.triggerReference().equals(execution.triggerReference());
				})
				.findFirst()
				.orElseThrow(() -> new BusinessException(
						"AUTOMATION_EXECUTION_NOT_FOUND",
						"No se encontro el resultado de la ocurrencia."
				));
	}

	private OffsetDateTime nextAttemptAt(int attemptCount) {
		long delayMinutes = Math.min(60L, 1L << Math.min(10, Math.max(0, attemptCount - 1)));
		return OffsetDateTime.now().plusMinutes(delayMinutes);
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

	private void requireSupportedDefinition(String triggerType, String actionType) {
		if (!SUPPORTED_TRIGGERS.contains(triggerType)) {
			throw new BusinessException("AUTOMATION_TRIGGER_UNSUPPORTED", "Este disparador aun no tiene un consumidor implementado.");
		}
		if (!SUPPORTED_ACTIONS.contains(actionType)) {
			throw new BusinessException("AUTOMATION_ACTION_UNSUPPORTED", "Esta accion aun no esta implementada.");
		}
	}

	private void validateActionConfig(String actionType, String actionConfigJson) {
		if (!"CREATE_NOTIFICATION".equals(actionType)) return;
		try {
			JsonNode config = objectMapper.readTree(actionConfigJson);
			if (text(config, "body", null) == null || text(config, "body", null).isBlank()) {
				throw new BusinessException("AUTOMATION_NOTIFICATION_BODY_REQUIRED", "La notificacion necesita un mensaje.");
			}
		} catch (JsonProcessingException exception) {
			throw new BusinessException("AUTOMATION_JSON_INVALID", "La configuracion JSON de la automatizacion es invalida.");
		}
	}

	private int intervalMinutes(String conditionJson) {
		try {
			JsonNode node = objectMapper.readTree(conditionJson == null || conditionJson.isBlank() ? "{}" : conditionJson);
			int value = node.path("intervalMinutes").asInt(1440);
			return Math.max(1, Math.min(value, 10080));
		} catch (JsonProcessingException exception) {
			return 1440;
		}
	}

	private String text(JsonNode node, String field, String fallback) {
		JsonNode value = node.get(field);
		return value == null || value.isNull() ? fallback : value.asText();
	}

	private String required(String value, String code, String message) {
		if (value == null || value.isBlank()) throw new BusinessException(code, message);
		return value.trim();
	}

	private String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

	private void requireBranch(GymOsPrincipal principal) {
		if (principal == null) throw new BusinessException("AUTHENTICATION_REQUIRED", "Debes iniciar sesion para continuar.");
		if (principal.branchId() == null) throw new BusinessException("BRANCH_REQUIRED", "Selecciona una sucursal para operar.");
	}
}
