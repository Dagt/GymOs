package com.gymos.automations.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymos.audit.api.AuditService;
import com.gymos.automations.application.command.CreateAutomationRuleCommand;
import com.gymos.automations.infrastructure.AutomationRepository;
import com.gymos.automations.infrastructure.AutomationRepository.AutomationExecutionRecord;
import com.gymos.automations.infrastructure.AutomationRepository.AutomationDeadLetterRecord;
import com.gymos.automations.infrastructure.AutomationRepository.AutomationRuleRecord;
import com.gymos.automations.infrastructure.AutomationRepository.AutomationTriggerEventRecord;
import com.gymos.automations.infrastructure.AutomationRepository.AutomationOperationsRecord;
import com.gymos.automations.infrastructure.AutomationRepository.SchedulerStatusRecord;
import com.gymos.automations.infrastructure.AutomationRepository.CreateRuleData;
import com.gymos.common.api.BusinessException;
import com.gymos.common.api.PageResponse;
import com.gymos.common.observability.TenantTelemetry;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.notifications.application.NotificationService;
import com.gymos.notifications.application.NotificationService.CreateNotificationCommand;
import java.time.OffsetDateTime;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AutomationService {
	private static final Set<String> SUPPORTED_TRIGGERS = Set.of(
			"MANUAL", "SCHEDULED", "PAYMENT_COMPLETED", "PAYMENT_PENDING_VALIDATION",
			"BOOKING_CREATED", "BOOKING_CONFIRMED", "BOOKING_CANCELLED",
			"MEMBERSHIP_EXPIRING", "MEMBERSHIP_EXPIRED",
			"RECURRING_CHARGE_DUE", "RECURRING_CHARGE_SUCCEEDED", "RECURRING_CHARGE_FAILED"
	);
	private static final Set<String> DOMAIN_EVENT_TRIGGERS = Set.of(
			"PAYMENT_COMPLETED", "PAYMENT_PENDING_VALIDATION",
			"BOOKING_CREATED", "BOOKING_CONFIRMED", "BOOKING_CANCELLED",
			"MEMBERSHIP_EXPIRING", "MEMBERSHIP_EXPIRED",
			"RECURRING_CHARGE_DUE", "RECURRING_CHARGE_SUCCEEDED", "RECURRING_CHARGE_FAILED"
	);
	private static final Set<String> CLIENT_EMAIL_TRIGGERS = Set.of(
			"PAYMENT_COMPLETED", "BOOKING_CREATED", "BOOKING_CONFIRMED", "BOOKING_CANCELLED",
			"MEMBERSHIP_EXPIRING", "MEMBERSHIP_EXPIRED", "RECURRING_CHARGE_DUE",
			"RECURRING_CHARGE_SUCCEEDED", "RECURRING_CHARGE_FAILED"
	);
	private static final Set<String> SUPPORTED_ACTIONS = Set.of("CREATE_NOTIFICATION");
	private static final int EVENT_INFRASTRUCTURE_RETRY_LIMIT = 10;
	private static final Pattern SECRET_PATTERN = Pattern.compile(
			"(?i)(authorization|access[_-]?token|refresh[_-]?token|client[_-]?secret|api[_-]?key|password|secret)(\\s*[:=]\\s*)([^\\s,;]+)"
	);
	private static final Pattern BEARER_PATTERN = Pattern.compile("(?i)Bearer\\s+[A-Za-z0-9._~+/-]+={0,2}");
	private static final Pattern EMAIL_PATTERN = Pattern.compile(
			"(?i)\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b"
	);
	private static final Pattern CARD_PATTERN = Pattern.compile("\\b(?:\\d[ -]?){13,19}\\b");

	private final AutomationRepository automationRepository;
	private final NotificationService notificationService;
	private final AuditService auditService;
	private final ObjectMapper objectMapper;
	private final TenantTelemetry telemetry;

	@Autowired
	public AutomationService(
			AutomationRepository automationRepository,
			NotificationService notificationService,
			AuditService auditService,
			ObjectMapper objectMapper,
			TenantTelemetry telemetry
	) {
		this.automationRepository = automationRepository;
		this.notificationService = notificationService;
		this.auditService = auditService;
		this.objectMapper = objectMapper;
		this.telemetry = telemetry;
	}

	AutomationService(
			AutomationRepository automationRepository,
			NotificationService notificationService,
			AuditService auditService,
			ObjectMapper objectMapper
	) {
		this(automationRepository, notificationService, auditService, objectMapper, TenantTelemetry.noOp());
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
		validateConditionConfig(triggerType, conditionJson);
		validateActionConfig(triggerType, actionType, actionConfigJson);
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
		AutomationRuleRecord rule = requireRule(principal, ruleId);
		requireSupportedDefinition(rule.triggerType(), rule.actionType());
		validateConditionConfig(rule.triggerType(), rule.conditionJson());
		validateActionConfig(rule.triggerType(), rule.actionType(), rule.actionConfigJson());
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
		String executionReference = blankToNull(triggerReference);
		if (executionReference != null && executionReference.length() > 200) {
			throw new BusinessException(
					"AUTOMATION_TRIGGER_REFERENCE_INVALID",
					"La referencia de ejecucion es demasiado larga."
			);
		}
		if (executionReference == null) {
			executionReference = "manual:" + correlationId;
		}
		String occurrence = "manual:" + executionReference;
		return executeRule(
				rule, principal.userId(), executionReference, occurrence, correlationId, "BACKOFFICE",
				executionKey(rule, occurrence), null
		);
	}

	public int runDueScheduledRules() {
		OffsetDateTime now = OffsetDateTime.now();
		int executed = 0;
		for (AutomationRuleRecord rule : automationRepository.dueScheduledRules(now, 25)) {
			String triggerReference = rule.currentTriggerReference() == null
					? "scheduled:" + rule.id() + ":" + rule.nextRunAt()
					: rule.currentTriggerReference();
			if (!automationRepository.claimScheduledRule(rule.id(), now, now.plusMinutes(5), triggerReference)) {
				continue;
			}
			try {
				AutomationExecutionRecord previous = automationRepository.executionForTrigger(
						rule.organizationId(), rule.id(), triggerReference
				).orElse(null);
				if (previous != null && Set.of("SUCCEEDED", "DEAD_LETTER").contains(previous.status())) {
					automationRepository.rescheduleRule(
							rule.id(),
							OffsetDateTime.now().plusMinutes(intervalMinutes(rule.conditionJson())),
							true
					);
					executed++;
					continue;
				}
				if (previous != null
						&& "FAILED".equals(previous.status())
						&& previous.attemptNumber() > rule.retryLimit()) {
					automationRepository.updateExecutionStatus(previous.id(), "DEAD_LETTER");
					automationRepository.rescheduleRule(
							rule.id(),
							OffsetDateTime.now().plusMinutes(intervalMinutes(rule.conditionJson())),
							true
					);
					executed++;
					continue;
				}
				String idempotencyKey = previous == null
						? executionKey(rule, triggerReference)
						: previous.idempotencyKey();
				AutomationExecutionRecord execution = executeRule(
						rule,
						null,
						triggerReference,
						triggerReference,
						"automation-scheduler",
						"SCHEDULER",
						idempotencyKey,
						null
				);
				if ("FAILED".equals(execution.status()) && execution.attemptNumber() <= rule.retryLimit()) {
					automationRepository.updateExecutionStatus(execution.id(), "RETRY_SCHEDULED");
					automationRepository.rescheduleRule(
							rule.id(),
							OffsetDateTime.now().plusMinutes(retryBackoffMinutes(execution.attemptNumber())),
							false
					);
				} else {
					if ("FAILED".equals(execution.status())) {
						automationRepository.updateExecutionStatus(execution.id(), "DEAD_LETTER");
					}
					automationRepository.rescheduleRule(
							rule.id(),
							OffsetDateTime.now().plusMinutes(intervalMinutes(rule.conditionJson())),
							true
					);
				}
				executed++;
			} catch (RuntimeException exception) {
				// Preserve the trigger identity so a transient infrastructure failure can
				// be retried without duplicating the configured action.
				automationRepository.rescheduleRule(rule.id(), OffsetDateTime.now().plusMinutes(1), false);
			}
		}
		return executed;
	}

	@Transactional
	public void enqueueDomainEvent(
			UUID organizationId,
			UUID branchId,
			String triggerType,
			String triggerReference
	) {
		enqueueDomainEvent(organizationId, branchId, triggerType, triggerReference, Map.of());
	}

	@Transactional
	public void enqueueDomainEvent(
			UUID organizationId,
			UUID branchId,
			String triggerType,
			String triggerReference,
			Map<String, ?> payload
	) {
		String normalizedType = required(triggerType, "AUTOMATION_TRIGGER_REQUIRED", "El evento necesita tipo.").toUpperCase();
		if (!DOMAIN_EVENT_TRIGGERS.contains(normalizedType)) {
			throw new BusinessException("AUTOMATION_TRIGGER_UNSUPPORTED", "El evento no tiene un consumidor de automatizacion.");
		}
		if (organizationId == null || branchId == null) {
			throw new BusinessException("AUTOMATION_EVENT_CONTEXT_INVALID", "El evento necesita gimnasio y sucursal.");
		}
		String reference = required(triggerReference, "AUTOMATION_EVENT_REFERENCE_REQUIRED", "El evento necesita referencia.");
		if (reference.length() > 200) {
			throw new BusinessException("AUTOMATION_EVENT_REFERENCE_INVALID", "La referencia del evento es demasiado larga.");
		}
		try {
			automationRepository.enqueueTriggerEvent(
					organizationId, branchId, normalizedType, reference,
					objectMapper.writeValueAsString(payload == null ? Map.of() : payload)
			);
		} catch (JsonProcessingException exception) {
			throw new BusinessException("AUTOMATION_EVENT_PAYLOAD_INVALID", "No fue posible serializar el evento.");
		}
	}

	public int runDueTriggerEvents() {
		OffsetDateTime now = OffsetDateTime.now();
		int processed = 0;
		for (AutomationTriggerEventRecord event : automationRepository.dueTriggerEvents(now, 50)) {
			if (!automationRepository.claimTriggerEvent(event.id(), now, now.plusMinutes(5))) {
				continue;
			}
			try {
				processTriggerEvent(event);
				processed++;
			} catch (RuntimeException exception) {
				int attempt = event.attemptNumber() + 1;
				boolean exhausted = attempt >= EVENT_INFRASTRUCTURE_RETRY_LIMIT;
				automationRepository.finishTriggerEvent(
						event.id(), exhausted ? "DEAD_LETTER" : "RETRY_SCHEDULED",
						exhausted
								? OffsetDateTime.now()
								: OffsetDateTime.now().plusMinutes(retryBackoffMinutes(attempt)),
						safeError(exception)
				);
			}
		}
		return processed;
	}

	private void processTriggerEvent(AutomationTriggerEventRecord event) {
		List<AutomationRuleRecord> rules = automationRepository.enabledRulesForTrigger(
				event.organizationId(), event.branchId(), event.triggerType()
		);
		boolean retryScheduled = false;
		boolean deadLetter = false;
		int highestAttempt = 1;
		List<String> errors = new ArrayList<>();
		JsonNode payload = eventPayload(event);
		for (AutomationRuleRecord rule : rules) {
			if (!matchesCondition(rule, payload)) {
				continue;
			}
			String occurrenceReference = text(payload, "occurrenceKey", event.triggerReference());
			String idempotencyKey = executionKey(rule, occurrenceReference);
			AutomationExecutionRecord previous = automationRepository.executionForOccurrence(
					rule.organizationId(), rule.id(), occurrenceReference
			).orElse(null);
			if (previous != null && "SUCCEEDED".equals(previous.status())) {
				continue;
			}
			if (previous != null && "DISCARDED".equals(previous.status())) {
				continue;
			}
			if (previous != null && "DEAD_LETTER".equals(previous.status())) {
				deadLetter = true;
				if (previous.errorMessage() != null && !previous.errorMessage().isBlank()) {
					errors.add(rule.name() + ": " + previous.errorMessage());
				}
				continue;
			}
			if (previous != null
					&& "FAILED".equals(previous.status())
					&& previous.attemptNumber() > rule.retryLimit()) {
				automationRepository.updateExecutionStatus(previous.id(), "DEAD_LETTER");
				deadLetter = true;
				if (previous.errorMessage() != null && !previous.errorMessage().isBlank()) {
					errors.add(rule.name() + ": " + previous.errorMessage());
				}
				continue;
			}
			if (previous != null) {
				idempotencyKey = previous.idempotencyKey();
			}
			AutomationExecutionRecord execution = executeRule(
					rule, null, event.triggerReference(), occurrenceReference,
					event.triggerReference(), "DOMAIN_EVENT", idempotencyKey, payload
			);
			highestAttempt = Math.max(highestAttempt, execution.attemptNumber());
			if (!"FAILED".equals(execution.status())) {
				continue;
			}
			if (execution.attemptNumber() <= rule.retryLimit()) {
				automationRepository.updateExecutionStatus(execution.id(), "RETRY_SCHEDULED");
				retryScheduled = true;
			} else {
				automationRepository.updateExecutionStatus(execution.id(), "DEAD_LETTER");
				deadLetter = true;
			}
			if (execution.errorMessage() != null && !execution.errorMessage().isBlank()) {
				errors.add(rule.name() + ": " + execution.errorMessage());
			}
		}
		if (retryScheduled) {
			automationRepository.finishTriggerEvent(
					event.id(), "RETRY_SCHEDULED",
					OffsetDateTime.now().plusMinutes(retryBackoffMinutes(highestAttempt)),
					trimError(String.join(" | ", errors))
			);
			return;
		}
		automationRepository.finishTriggerEvent(
				event.id(), deadLetter ? "DEAD_LETTER" : "COMPLETED", OffsetDateTime.now(),
				trimError(String.join(" | ", errors))
		);
	}

	private long retryBackoffMinutes(int attemptNumber) {
		int exponent = Math.min(Math.max(attemptNumber - 1, 0), 6);
		return Math.min(60L, 1L << exponent);
	}

	private AutomationExecutionRecord executeRule(
			AutomationRuleRecord rule,
			UUID actorUserId,
			String triggerReference,
			String occurrenceReference,
			String correlationId,
			String auditOrigin,
			String idempotencyKey,
			JsonNode payload
	) {
		var duration = telemetry.start();
		UUID executionId = automationRepository.startExecution(
				rule.organizationId(), rule.branchId(), rule.id(), rule.version(),
				blankToNull(triggerReference), blankToNull(occurrenceReference), idempotencyKey
		);
		String status = "SUCCEEDED";
		String error = null;
		try {
			if ("CREATE_NOTIFICATION".equals(rule.actionType())) {
				JsonNode config = objectMapper.readTree(rule.actionConfigJson());
				String channel = text(config, "channel", "IN_APP").toUpperCase();
				String directRecipient = text(config, "recipient", null);
				UUID clientId = payloadUuid(payload, "clientId");
				String recipientType = directRecipient == null && "EMAIL".equals(channel) ? "CLIENT" : "DIRECT";
				notificationService.createSystemIdempotent(
						rule.organizationId(),
						rule.branchId(),
						actorUserId,
						new CreateNotificationCommand(
								channel,
								directRecipient,
								recipientType,
								directRecipient == null ? clientId : null,
								renderTemplate(text(config, "subject", rule.name()), payload),
								renderTemplate(text(config, "body", "Automatizacion ejecutada: " + rule.name()), payload),
								text(config, "category", "TRANSACTIONAL"),
								rule.triggerType(),
								"AUTOMATION_RULE",
								rule.id(),
								null
						),
						correlationId,
						idempotencyKey
				);
			} else {
				throw new BusinessException(
						"AUTOMATION_ACTION_UNSUPPORTED",
						"La accion configurada no esta soportada."
				);
			}
		} catch (RuntimeException | JsonProcessingException exception) {
			status = "FAILED";
			error = exception.getMessage();
		}
		automationRepository.finishExecution(executionId, status, trimError(error));
		telemetry.increment(
				"gymos.automation.executions", rule.organizationId(),
				"trigger_type", rule.triggerType(), "outcome", status
		);
		telemetry.stop(
				duration, "gymos.automation.execution.duration", rule.organizationId(),
				"trigger_type", rule.triggerType(), "outcome", status
		);
		auditService.recordSystem(rule.organizationId(), rule.branchId(), actorUserId, "AUTOMATION_EXECUTED", "AUTOMATION_EXECUTION", executionId, auditOrigin, status, correlationId);
		return automationRepository.executions(rule.organizationId(), rule.branchId(), rule.id()).stream()
				.filter(execution -> execution.id().equals(executionId))
				.findFirst()
				.orElseThrow();
	}

	private String executionKey(AutomationRuleRecord rule, String triggerReference) {
		String prefix = "automation:" + rule.id() + ":v" + rule.version() + ":";
		String raw = prefix + triggerReference;
		if (raw.length() <= 190) return raw;
		try {
			String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
					.digest(triggerReference.getBytes(StandardCharsets.UTF_8)));
			return prefix + "sha256:" + hash;
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 no esta disponible.", exception);
		}
	}

	private String safeError(RuntimeException exception) {
		return trimError(exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage());
	}

	private String trimError(String value) {
		if (value == null || value.isBlank()) return null;
		String sanitized = SECRET_PATTERN.matcher(value).replaceAll("$1$2[REDACTED]");
		sanitized = BEARER_PATTERN.matcher(sanitized).replaceAll("Bearer [REDACTED]");
		sanitized = EMAIL_PATTERN.matcher(sanitized).replaceAll("[EMAIL]");
		sanitized = CARD_PATTERN.matcher(sanitized).replaceAll("[CARD]");
		return sanitized.substring(0, Math.min(2000, sanitized.length()));
	}

	@Transactional(readOnly = true)
	public PageResponse<AutomationExecutionRecord> executions(
			GymOsPrincipal principal, UUID ruleId, int page, int size
	) {
		requireRule(principal, ruleId);
		int boundedPage = Math.max(0, page);
		int boundedSize = Math.max(1, Math.min(size, 100));
		return automationRepository.executionHistory(
				principal.organizationId(), principal.branchId(), ruleId, boundedPage, boundedSize
		);
	}

	@Transactional(readOnly = true)
	public PageResponse<AutomationDeadLetterRecord> deadLetters(
			GymOsPrincipal principal, int page, int size
	) {
		requireBranch(principal);
		int boundedPage = Math.max(0, page);
		int boundedSize = Math.max(1, Math.min(size, 100));
		return automationRepository.deadLetters(
				principal.organizationId(), principal.branchId(), boundedPage, boundedSize
		);
	}

	@Transactional(readOnly = true)
	public SchedulerStatusRecord schedulerStatus(GymOsPrincipal principal) {
		requireBranch(principal);
		return automationRepository.schedulerStatus(principal.organizationId());
	}

	@Transactional
	public SchedulerStatusRecord setSchedulerPaused(
			GymOsPrincipal principal, boolean paused, String reason, String correlationId
	) {
		requireBranch(principal);
		String normalizedReason = paused
				? required(reason, "AUTOMATION_SCHEDULER_REASON_REQUIRED", "Indica por que se pausa el procesamiento.")
				: blankToNull(reason);
		if (normalizedReason != null && normalizedReason.length() > 500) {
			throw new BusinessException("AUTOMATION_SCHEDULER_REASON_INVALID", "El motivo no puede exceder 500 caracteres.");
		}
		SchedulerStatusRecord status = automationRepository.setSchedulerPaused(
				principal.organizationId(), principal.userId(), paused, normalizedReason
		);
		auditService.record(
				principal, paused ? "AUTOMATION_SCHEDULER_PAUSED" : "AUTOMATION_SCHEDULER_RESUMED",
				"ORGANIZATION", principal.organizationId(), "BACKOFFICE", normalizedReason, correlationId
		);
		telemetry.increment(
				"gymos.automation.scheduler.control", principal.organizationId(),
				"action", paused ? "paused" : "resumed"
		);
		return status;
	}

	@Transactional(readOnly = true)
	public AutomationOperationsView operations(GymOsPrincipal principal) {
		requireBranch(principal);
		AutomationOperationsRecord record = automationRepository.operations(
				principal.organizationId(), principal.branchId(), OffsetDateTime.now()
		);
		SchedulerStatusRecord scheduler = automationRepository.schedulerStatus(principal.organizationId());
		long deadLetters = record.deadLetterEvents() + record.deadLetterExecutions();
		String alertLevel = deadLetters > 0 || record.expiredLeases() > 0
				? "CRITICAL"
				: record.pendingEvents() + record.retryEvents() > 25 || record.dueRules() > 10
						? "WARNING" : "HEALTHY";
		telemetry.gauge("gymos.automation.backlog", principal.organizationId(), record.pendingEvents(), "status", "pending");
		telemetry.gauge("gymos.automation.backlog", principal.organizationId(), record.retryEvents(), "status", "retry_scheduled");
		telemetry.gauge("gymos.automation.backlog", principal.organizationId(), deadLetters, "status", "dead_letter");
		telemetry.gauge("gymos.automation.expired_leases", principal.organizationId(), record.expiredLeases());
		return new AutomationOperationsView(
				record.dueRules(), record.pendingEvents(), record.retryEvents(), record.expiredLeases(),
				record.deadLetterEvents(), record.deadLetterExecutions(), record.oldestPendingAt(),
				scheduler.paused(), alertLevel
		);
	}

	@Transactional
	public AutomationRecoveryResult retryDeadLetter(
			GymOsPrincipal principal,
			UUID deadLetterId,
			String source,
			String reason,
			String correlationId
	) {
		requireBranch(principal);
		String normalizedSource = recoverySource(source);
		String normalizedReason = required(reason, "AUTOMATION_RECOVERY_REASON_REQUIRED", "Indica por que se reintenta.");
		if ("EVENT".equals(normalizedSource)) {
			if (!automationRepository.requeueTriggerEvent(
					principal.organizationId(), principal.branchId(), deadLetterId
			)) {
				throw new BusinessException("AUTOMATION_DEAD_LETTER_NOT_FOUND", "El evento ya no esta disponible para reintento.");
			}
			auditService.record(
					principal, "AUTOMATION_DEAD_LETTER_RETRIED", "AUTOMATION_TRIGGER_EVENT",
					deadLetterId, "BACKOFFICE", normalizedReason, correlationId
			);
			return new AutomationRecoveryResult(deadLetterId, normalizedSource, "RETRY_SCHEDULED");
		}

		AutomationExecutionRecord execution = requireExecution(principal, deadLetterId);
		if (!"DEAD_LETTER".equals(execution.status())) {
			throw new BusinessException("AUTOMATION_DEAD_LETTER_NOT_FOUND", "La ejecucion ya no esta en fallo definitivo.");
		}
		AutomationRuleRecord rule = requireRule(principal, execution.ruleId());
		if (!"ENABLED".equals(rule.status())) {
			throw new BusinessException("AUTOMATION_RULE_DISABLED", "Habilita la regla antes de reintentar.");
		}
		boolean queued = switch (rule.triggerType()) {
			case "SCHEDULED" -> automationRepository.requeueScheduledRule(rule.id(), execution.triggerReference());
			case "PAYMENT_COMPLETED", "PAYMENT_PENDING_VALIDATION",
					"BOOKING_CREATED", "BOOKING_CONFIRMED", "BOOKING_CANCELLED",
					"MEMBERSHIP_EXPIRING", "MEMBERSHIP_EXPIRED",
					"RECURRING_CHARGE_DUE", "RECURRING_CHARGE_SUCCEEDED", "RECURRING_CHARGE_FAILED" -> automationRepository.requeueTriggerEvent(
						principal.organizationId(), principal.branchId(), rule.triggerType(), execution.triggerReference()
				);
			default -> false;
		};
		if (!queued) {
			throw new BusinessException("AUTOMATION_DEAD_LETTER_NOT_RETRYABLE", "No se encontro la ocurrencia durable para reintentar.");
		}
		automationRepository.updateExecutionStatus(execution.id(), "RETRY_SCHEDULED");
		auditService.record(
				principal, "AUTOMATION_DEAD_LETTER_RETRIED", "AUTOMATION_EXECUTION",
				execution.id(), "BACKOFFICE", normalizedReason, correlationId
		);
		return new AutomationRecoveryResult(execution.id(), normalizedSource, "RETRY_SCHEDULED");
	}

	@Transactional
	public AutomationRecoveryResult discardDeadLetter(
			GymOsPrincipal principal,
			UUID deadLetterId,
			String source,
			String reason,
			String correlationId
	) {
		requireBranch(principal);
		String normalizedSource = recoverySource(source);
		String normalizedReason = required(reason, "AUTOMATION_RECOVERY_REASON_REQUIRED", "Indica por que se descarta.");
		if ("EVENT".equals(normalizedSource)) {
			if (!automationRepository.discardTriggerEvent(
					principal.organizationId(), principal.branchId(), deadLetterId, trimError(normalizedReason)
			)) {
				throw new BusinessException("AUTOMATION_DEAD_LETTER_NOT_FOUND", "El evento ya no esta disponible para descarte.");
			}
			auditService.record(
					principal, "AUTOMATION_DEAD_LETTER_DISCARDED", "AUTOMATION_TRIGGER_EVENT",
					deadLetterId, "BACKOFFICE", normalizedReason, correlationId
			);
			return new AutomationRecoveryResult(deadLetterId, normalizedSource, "DISCARDED");
		}

		AutomationExecutionRecord execution = requireExecution(principal, deadLetterId);
		if (!"DEAD_LETTER".equals(execution.status())) {
			throw new BusinessException("AUTOMATION_DEAD_LETTER_NOT_FOUND", "La ejecucion ya no esta en fallo definitivo.");
		}
		AutomationRuleRecord rule = requireRule(principal, execution.ruleId());
		automationRepository.updateExecutionStatus(execution.id(), "DISCARDED");
		if (DOMAIN_EVENT_TRIGGERS.contains(rule.triggerType())) {
			automationRepository.requeueTriggerEvent(
					principal.organizationId(), principal.branchId(), rule.triggerType(), execution.triggerReference()
			);
		}
		auditService.record(
				principal, "AUTOMATION_DEAD_LETTER_DISCARDED", "AUTOMATION_EXECUTION",
				execution.id(), "BACKOFFICE", normalizedReason, correlationId
		);
		return new AutomationRecoveryResult(execution.id(), normalizedSource, "DISCARDED");
	}

	private AutomationExecutionRecord requireExecution(GymOsPrincipal principal, UUID executionId) {
		return automationRepository.execution(
				principal.organizationId(), principal.branchId(), executionId
		).orElseThrow(() -> new BusinessException(
				"AUTOMATION_DEAD_LETTER_NOT_FOUND", "La ejecucion no existe o no esta disponible."
		));
	}

	private String recoverySource(String source) {
		String normalized = required(source, "AUTOMATION_RECOVERY_SOURCE_REQUIRED", "Indica el origen del fallo.").toUpperCase();
		if (!Set.of("EXECUTION", "EVENT").contains(normalized)) {
			throw new BusinessException("AUTOMATION_RECOVERY_SOURCE_INVALID", "El origen del fallo no es valido.");
		}
		return normalized;
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
			throw new BusinessException(
					"AUTOMATION_TRIGGER_UNSUPPORTED",
					"Este disparador aun no tiene un consumidor implementado."
			);
		}
		if (!SUPPORTED_ACTIONS.contains(actionType)) {
			throw new BusinessException(
					"AUTOMATION_ACTION_UNSUPPORTED",
					"Esta accion aun no esta implementada."
			);
		}
	}

	private void validateActionConfig(String triggerType, String actionType, String actionConfigJson) {
		if (!"CREATE_NOTIFICATION".equals(actionType)) {
			return;
		}
		try {
			JsonNode config = objectMapper.readTree(actionConfigJson);
			String channel = text(config, "channel", "IN_APP").toUpperCase();
			if (!Set.of("IN_APP", "EMAIL").contains(channel)) {
				throw new BusinessException(
						"AUTOMATION_NOTIFICATION_CHANNEL_UNSUPPORTED",
						"El canal debe ser IN_APP o EMAIL."
				);
			}
			String body = text(config, "body", null);
			if (body == null || body.isBlank()) {
				throw new BusinessException(
						"AUTOMATION_NOTIFICATION_BODY_REQUIRED",
						"La notificacion necesita un mensaje."
				);
			}
			validateLength(body, 5000, "AUTOMATION_NOTIFICATION_BODY_INVALID", "El mensaje no puede exceder 5000 caracteres.");
			validateLength(text(config, "subject", null), 180, "AUTOMATION_NOTIFICATION_SUBJECT_INVALID", "El asunto no puede exceder 180 caracteres.");
			validateLength(text(config, "recipient", null), 180, "AUTOMATION_NOTIFICATION_RECIPIENT_INVALID", "El destinatario no puede exceder 180 caracteres.");
			validateLength(text(config, "channel", null), 40, "AUTOMATION_NOTIFICATION_CHANNEL_INVALID", "El canal no puede exceder 40 caracteres.");
			if ("EMAIL".equals(channel)) {
				if (text(config, "subject", null) == null) {
					throw new BusinessException("AUTOMATION_EMAIL_SUBJECT_REQUIRED", "El correo necesita asunto.");
				}
				if (text(config, "recipient", null) == null && !CLIENT_EMAIL_TRIGGERS.contains(triggerType)) {
					throw new BusinessException(
							"AUTOMATION_EMAIL_RECIPIENT_UNAVAILABLE",
							"Este evento no identifica a un socio; configura un destinatario directo."
					);
				}
				String category = text(config, "category", "TRANSACTIONAL").toUpperCase();
				if (!Set.of("TRANSACTIONAL", "MARKETING").contains(category)) {
					throw new BusinessException("AUTOMATION_EMAIL_CATEGORY_INVALID", "La categoria del correo no es valida.");
				}
			}
		} catch (JsonProcessingException exception) {
			throw new BusinessException(
					"AUTOMATION_JSON_INVALID",
					"La configuracion JSON de la automatizacion es invalida."
			);
		}
	}

	private UUID payloadUuid(JsonNode payload, String field) {
		String value = text(payload, field, null);
		if (value == null) return null;
		try {
			return UUID.fromString(value);
		} catch (IllegalArgumentException exception) {
			return null;
		}
	}

	private String renderTemplate(String template, JsonNode payload) {
		if (template == null || payload == null || !payload.isObject()) return template;
		String rendered = template;
		Iterator<Map.Entry<String, JsonNode>> fields = payload.properties().iterator();
		while (fields.hasNext()) {
			Map.Entry<String, JsonNode> field = fields.next();
			if (field.getValue().isValueNode()) {
				rendered = rendered.replace("{{" + field.getKey() + "}}", field.getValue().asText());
			}
		}
		return rendered;
	}

	private void validateConditionConfig(String triggerType, String conditionJson) {
		try {
			JsonNode condition = objectMapper.readTree(conditionJson);
			if (!condition.isObject()) {
				throw new BusinessException("AUTOMATION_CONDITION_INVALID", "La condicion debe ser un objeto.");
			}
			Set<String> allowedFields = switch (triggerType) {
				case "SCHEDULED" -> Set.of("intervalMinutes");
				case "MEMBERSHIP_EXPIRING" -> Set.of("daysBefore", "planId");
				case "MEMBERSHIP_EXPIRED" -> Set.of("planId");
				case "PAYMENT_PENDING_VALIDATION" -> Set.of("method", "minimumAmountCents");
				case "PAYMENT_COMPLETED" -> Set.of("businessContext");
				case "BOOKING_CREATED", "BOOKING_CONFIRMED", "BOOKING_CANCELLED" -> Set.of("resourceId");
				case "RECURRING_CHARGE_DUE" -> Set.of("planId");
				case "RECURRING_CHARGE_SUCCEEDED" -> Set.of("planId", "minimumAmountCents");
				case "RECURRING_CHARGE_FAILED" -> Set.of("planId", "exhausted");
				default -> Set.of();
			};
			Iterator<Map.Entry<String, JsonNode>> fields = condition.properties().iterator();
			while (fields.hasNext()) {
				String field = fields.next().getKey();
				if (!allowedFields.contains(field)) {
					throw new BusinessException(
							"AUTOMATION_CONDITION_FIELD_UNSUPPORTED",
							"La condicion contiene un campo no soportado: " + field
					);
				}
			}
			if ("SCHEDULED".equals(triggerType)) {
				integerCondition(
						condition, "intervalMinutes", 1440, 1, 10080,
						"AUTOMATION_INTERVAL_INVALID", "El intervalo debe estar entre 1 y 10080 minutos."
				);
			}
			if ("MEMBERSHIP_EXPIRING".equals(triggerType)) {
				integerCondition(
						condition, "daysBefore", 3, 1, 90,
						"AUTOMATION_DAYS_BEFORE_INVALID", "Los dias de anticipacion deben estar entre 1 y 90."
				);
			}
			validateOptionalUuid(condition, "planId");
			validateOptionalUuid(condition, "resourceId");
			if (condition.hasNonNull("minimumAmountCents")) {
				integerCondition(
						condition, "minimumAmountCents", 0, 0, Integer.MAX_VALUE,
						"AUTOMATION_MINIMUM_AMOUNT_INVALID", "El monto minimo debe ser un entero no negativo."
				);
			}
			validateOptionalText(condition, "method", 40, "AUTOMATION_PAYMENT_METHOD_INVALID", "El metodo de pago es invalido.");
			validateOptionalText(condition, "businessContext", 80, "AUTOMATION_BUSINESS_CONTEXT_INVALID", "El contexto comercial es invalido.");
			if (condition.hasNonNull("exhausted") && !condition.get("exhausted").isBoolean()) {
				throw new BusinessException("AUTOMATION_EXHAUSTED_INVALID", "El filtro de agotamiento debe ser booleano.");
			}
		} catch (JsonProcessingException exception) {
			throw new BusinessException("AUTOMATION_JSON_INVALID", "La configuracion JSON de la automatizacion es invalida.");
		}
	}

	private int integerCondition(
			JsonNode condition,
			String field,
			int fallback,
			int minimum,
			int maximum,
			String code,
			String message
	) {
		if (!condition.has(field) || condition.get(field).isNull()) return fallback;
		JsonNode value = condition.get(field);
		if (!value.isIntegralNumber() || !value.canConvertToInt()) {
			throw new BusinessException(code, message);
		}
		int resolved = value.intValue();
		if (resolved < minimum || resolved > maximum) {
			throw new BusinessException(code, message);
		}
		return resolved;
	}

	private void validateOptionalText(JsonNode condition, String field, int maximum, String code, String message) {
		if (!condition.has(field) || condition.get(field).isNull()) return;
		if (!condition.get(field).isTextual() || condition.get(field).asText().length() > maximum) {
			throw new BusinessException(code, message);
		}
	}

	private void validateOptionalUuid(JsonNode condition, String field) {
		if (condition.has(field) && !condition.get(field).isNull() && !condition.get(field).isTextual()) {
			throw new BusinessException("AUTOMATION_CONDITION_UUID_INVALID", "La condicion " + field + " necesita un UUID valido.");
		}
		String value = text(condition, field, null);
		if (value == null || value.isBlank()) return;
		try {
			UUID.fromString(value);
		} catch (IllegalArgumentException exception) {
			throw new BusinessException("AUTOMATION_CONDITION_UUID_INVALID", "La condicion " + field + " necesita un UUID valido.");
		}
	}

	private JsonNode eventPayload(AutomationTriggerEventRecord event) {
		try {
			return objectMapper.readTree(event.payloadJson() == null ? "{}" : event.payloadJson());
		} catch (JsonProcessingException exception) {
			throw new BusinessException("AUTOMATION_EVENT_PAYLOAD_INVALID", "El evento durable tiene un payload invalido.");
		}
	}

	private boolean matchesCondition(AutomationRuleRecord rule, JsonNode payload) {
		try {
			JsonNode condition = objectMapper.readTree(rule.conditionJson());
			return switch (rule.triggerType()) {
				case "MEMBERSHIP_EXPIRING" -> {
					int daysUntilEnd = payload.path("daysUntilEnd").asInt(Integer.MAX_VALUE);
					int daysBefore = condition.path("daysBefore").asInt(3);
					yield daysUntilEnd >= 0 && daysUntilEnd <= daysBefore
							&& matchesOptionalIdentifier(condition, payload, "planId");
				}
				case "MEMBERSHIP_EXPIRED" -> matchesOptionalIdentifier(condition, payload, "planId");
				case "PAYMENT_PENDING_VALIDATION" ->
						matchesOptionalText(condition, payload, "method")
								&& payload.path("amountCents").asLong(0) >= condition.path("minimumAmountCents").asLong(0);
				case "PAYMENT_COMPLETED" -> matchesOptionalText(condition, payload, "businessContext");
				case "BOOKING_CREATED", "BOOKING_CONFIRMED", "BOOKING_CANCELLED" ->
						matchesOptionalIdentifier(condition, payload, "resourceId");
				case "RECURRING_CHARGE_DUE" -> matchesOptionalIdentifier(condition, payload, "planId");
				case "RECURRING_CHARGE_SUCCEEDED" ->
						matchesOptionalIdentifier(condition, payload, "planId")
								&& payload.path("amountCents").asLong(0) >= condition.path("minimumAmountCents").asLong(0);
				case "RECURRING_CHARGE_FAILED" ->
						matchesOptionalIdentifier(condition, payload, "planId")
								&& (!condition.hasNonNull("exhausted")
								|| condition.path("exhausted").asBoolean() == payload.path("exhausted").asBoolean());
				default -> true;
			};
		} catch (JsonProcessingException exception) {
			throw new BusinessException("AUTOMATION_CONDITION_INVALID", "La condicion guardada no es valida.");
		}
	}

	private boolean matchesOptionalIdentifier(JsonNode condition, JsonNode payload, String field) {
		return matchesOptionalText(condition, payload, field);
	}

	private boolean matchesOptionalText(JsonNode condition, JsonNode payload, String field) {
		String expected = text(condition, field, null);
		return expected == null || expected.isBlank() || expected.equalsIgnoreCase(text(payload, field, ""));
	}

	private void validateLength(String value, int maximum, String code, String message) {
		if (value != null && value.length() > maximum) {
			throw new BusinessException(code, message);
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
		if (node == null) return fallback;
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

	public record AutomationRecoveryResult(UUID id, String source, String status) {}

	public record AutomationOperationsView(
			long dueRules, long pendingEvents, long retryEvents, long expiredLeases,
			long deadLetterEvents, long deadLetterExecutions, OffsetDateTime oldestPendingAt,
			boolean schedulerPaused, String alertLevel
	) {}
}
