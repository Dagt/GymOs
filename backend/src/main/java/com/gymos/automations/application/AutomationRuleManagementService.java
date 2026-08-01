package com.gymos.automations.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymos.audit.api.AuditService;
import com.gymos.automations.infrastructure.AutomationRepository;
import com.gymos.automations.infrastructure.AutomationRepository.AutomationRuleRecord;
import com.gymos.automations.infrastructure.AutomationRuleManagementRepository;
import com.gymos.automations.infrastructure.AutomationRuleManagementRepository.ArchivedRule;
import com.gymos.automations.infrastructure.AutomationRuleManagementRepository.RuleRevision;
import com.gymos.automations.infrastructure.ExpiringMembershipRepository;
import com.gymos.common.api.BusinessException;
import com.gymos.common.security.GymOsPrincipal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AutomationRuleManagementService {
    private static final Set<String> SUPPORTED_TRIGGERS = Set.of(
            "MANUAL",
            "SCHEDULED",
            "MEMBERSHIP_EXPIRING",
            "MEMBERSHIP_EXPIRED",
            "MEMBERSHIP_RENEWED",
            "MEMBERSHIP_CANCELLED",
            "PAYMENT_COMPLETED",
            "PAYMENT_FAILED",
            "BOOKING_CREATED",
            "BOOKING_CANCELLED"
    );
    private static final Set<String> SUPPORTED_ACTIONS = Set.of("CREATE_NOTIFICATION");

    private final AutomationRepository automationRepository;
    private final AutomationRuleManagementRepository managementRepository;
    private final ExpiringMembershipRepository expiringMembershipRepository;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public AutomationRuleManagementService(
            AutomationRepository automationRepository,
            AutomationRuleManagementRepository managementRepository,
            ExpiringMembershipRepository expiringMembershipRepository,
            AuditService auditService,
            ObjectMapper objectMapper
    ) {
        this.automationRepository = automationRepository;
        this.managementRepository = managementRepository;
        this.expiringMembershipRepository = expiringMembershipRepository;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public AutomationRuleRecord update(
            GymOsPrincipal principal,
            UUID ruleId,
            UpdateDefinition command,
            String correlationId
    ) {
        requirePermission(principal, "automation.manage");
        AutomationRuleRecord current = requireRule(principal, ruleId);
        String triggerType = required(command.triggerType(), "AUTOMATION_TRIGGER_REQUIRED").toUpperCase(Locale.ROOT);
        String actionType = required(command.actionType(), "AUTOMATION_ACTION_REQUIRED").toUpperCase(Locale.ROOT);
        String conditionJson = validJson(command.conditionJson());
        String actionConfigJson = validJson(command.actionConfigJson());
        validateDefinition(triggerType, conditionJson, actionType, actionConfigJson);
        int intervalMinutes = intervalMinutes(conditionJson);
        String scheduleCron = "SCHEDULED".equals(triggerType) ? "interval:" + intervalMinutes : null;
        OffsetDateTime nextRunAt = "SCHEDULED".equals(triggerType)
                ? OffsetDateTime.now().plusMinutes(intervalMinutes)
                : null;
        boolean updated = managementRepository.updateDefinition(
                principal.organizationId(), principal.branchId(), ruleId, principal.userId(),
                required(command.name(), "AUTOMATION_NAME_REQUIRED"), triggerType, conditionJson,
                actionType, actionConfigJson, Math.max(0, Math.min(command.retryLimit(), 10)),
                scheduleCron, nextRunAt
        );
        if (!updated) {
            throw new BusinessException("AUTOMATION_RULE_NOT_EDITABLE", "La regla no existe, esta archivada o no puede editarse.");
        }
        auditService.record(
                principal, "AUTOMATION_RULE_UPDATED", "AUTOMATION_RULE", ruleId,
                "BACKOFFICE", "definition-version:" + (current.version() + 1), correlationId
        );
        return requireRule(principal, ruleId);
    }

    @Transactional
    public AutomationRuleRecord duplicate(GymOsPrincipal principal, UUID ruleId, String correlationId) {
        requirePermission(principal, "automation.manage");
        requireRule(principal, ruleId);
        UUID duplicatedId = managementRepository.duplicate(
                principal.organizationId(), principal.branchId(), ruleId, principal.userId()
        );
        auditService.record(principal, "AUTOMATION_RULE_DUPLICATED", "AUTOMATION_RULE", duplicatedId,
                "BACKOFFICE", "source:" + ruleId, correlationId);
        return requireRule(principal, duplicatedId);
    }

    @Transactional
    public void archive(GymOsPrincipal principal, UUID ruleId, String correlationId) {
        requirePermission(principal, "automation.manage");
        requireRule(principal, ruleId);
        if (!managementRepository.archive(principal.organizationId(), principal.branchId(), ruleId, principal.userId())) {
            throw new BusinessException("AUTOMATION_RULE_NOT_ARCHIVABLE", "La regla no puede archivarse.");
        }
        auditService.record(principal, "AUTOMATION_RULE_ARCHIVED", "AUTOMATION_RULE", ruleId,
                "BACKOFFICE", null, correlationId);
    }

    @Transactional
    public ArchivedRule restore(GymOsPrincipal principal, UUID ruleId, String correlationId) {
        requirePermission(principal, "automation.manage");
        ArchivedRule archived = managementRepository.archivedRule(
                principal.organizationId(), principal.branchId(), ruleId
        ).orElseThrow(() -> new BusinessException(
                "AUTOMATION_RULE_ARCHIVED_NOT_FOUND", "La regla archivada no existe o no esta disponible."
        ));
        if (!managementRepository.restore(principal.organizationId(), principal.branchId(), ruleId, principal.userId())) {
            throw new BusinessException("AUTOMATION_RULE_RESTORE_CONFLICT", "La regla cambio antes de restaurarse.");
        }
        auditService.record(principal, "AUTOMATION_RULE_RESTORED", "AUTOMATION_RULE", ruleId,
                "BACKOFFICE", null, correlationId);
        return archived;
    }

    @Transactional(readOnly = true)
    public List<RuleRevision> revisions(GymOsPrincipal principal, UUID ruleId) {
        requirePermission(principal, "automation.read");
        requireRule(principal, ruleId);
        return managementRepository.revisions(principal.organizationId(), principal.branchId(), ruleId);
    }

    @Transactional(readOnly = true)
    public PreviewResult preview(GymOsPrincipal principal, UUID ruleId, int limit) {
        requirePermission(principal, "automation.read");
        AutomationRuleRecord rule = requireRule(principal, ruleId);
        int safeLimit = Math.max(1, Math.min(limit, 100));
        if ("MEMBERSHIP_EXPIRING".equals(rule.triggerType())) {
            int days = daysBeforeExpiry(rule.conditionJson());
            var matches = expiringMembershipRepository.find(
                    principal.organizationId(), principal.branchId(), LocalDate.now().plusDays(days), safeLimit
            );
            List<PreviewMatch> previewMatches = matches.stream()
                    .map(match -> new PreviewMatch(
                            "membership:" + match.id(),
                            "Membresia " + match.id() + " vence el " + match.endsOn(),
                            match.clientId(),
                            match.endsOn().toString()
                    ))
                    .toList();
            return new PreviewResult(rule.id(), rule.version(), rule.triggerType(), previewMatches.size(), previewMatches);
        }
        return new PreviewResult(rule.id(), rule.version(), rule.triggerType(), 1,
                List.of(new PreviewMatch("test:" + rule.id(), "Ejecucion de prueba", null, null)));
    }

    private AutomationRuleRecord requireRule(GymOsPrincipal principal, UUID ruleId) {
        requireBranch(principal);
        return automationRepository.rule(principal.organizationId(), principal.branchId(), ruleId)
                .orElseThrow(() -> new BusinessException(
                        "AUTOMATION_RULE_NOT_FOUND", "La automatizacion no existe o no esta disponible."
                ));
    }

    private void validateDefinition(String triggerType, String conditionJson, String actionType, String actionConfigJson) {
        if (!SUPPORTED_TRIGGERS.contains(triggerType)) {
            throw new BusinessException("AUTOMATION_TRIGGER_UNSUPPORTED", "El disparador aun no esta implementado.");
        }
        if (!SUPPORTED_ACTIONS.contains(actionType)) {
            throw new BusinessException("AUTOMATION_ACTION_UNSUPPORTED", "La accion aun no esta implementada.");
        }
        try {
            JsonNode action = objectMapper.readTree(actionConfigJson);
            if (action.path("body").asText("").isBlank()) {
                throw new BusinessException("AUTOMATION_NOTIFICATION_BODY_REQUIRED", "La notificacion necesita un mensaje.");
            }
            if ("MEMBERSHIP_EXPIRING".equals(triggerType)) {
                int days = objectMapper.readTree(conditionJson).path("daysBeforeExpiry").asInt(0);
                if (days < 1 || days > 90) {
                    throw new BusinessException("AUTOMATION_DAYS_BEFORE_INVALID", "Los dias de anticipacion deben estar entre 1 y 90.");
                }
            }
        } catch (JsonProcessingException exception) {
            throw new BusinessException("AUTOMATION_JSON_INVALID", "La configuracion JSON es invalida.");
        }
    }

    private String validJson(String value) {
        String resolved = value == null || value.isBlank() ? "{}" : value.trim();
        try {
            objectMapper.readTree(resolved);
            return resolved;
        } catch (JsonProcessingException exception) {
            throw new BusinessException("AUTOMATION_JSON_INVALID", "La configuracion JSON es invalida.");
        }
    }

    private int intervalMinutes(String conditionJson) {
        try {
            return Math.max(1, Math.min(objectMapper.readTree(conditionJson).path("intervalMinutes").asInt(1440), 10080));
        } catch (JsonProcessingException exception) {
            return 1440;
        }
    }

    private int daysBeforeExpiry(String conditionJson) {
        try {
            return Math.max(1, Math.min(objectMapper.readTree(conditionJson).path("daysBeforeExpiry").asInt(7), 90));
        } catch (JsonProcessingException exception) {
            return 7;
        }
    }

    private String required(String value, String code) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(code, "Falta un dato requerido de la automatizacion.");
        }
        return value.trim();
    }

    private void requirePermission(GymOsPrincipal principal, String permission) {
        requireBranch(principal);
        if (!principal.permissions().contains(permission)
                && !principal.permissions().contains("automation.manage")
                && !principal.permissions().contains("platform.admin")) {
            throw new BusinessException("FORBIDDEN", "No tienes permiso para realizar esta operacion.");
        }
    }

    private void requireBranch(GymOsPrincipal principal) {
        if (principal == null) {
            throw new BusinessException("AUTHENTICATION_REQUIRED", "Debes iniciar sesion para continuar.");
        }
        if (principal.branchId() == null) {
            throw new BusinessException("BRANCH_REQUIRED", "Selecciona una sucursal para operar.");
        }
    }

    public record UpdateDefinition(
            String name,
            String triggerType,
            String conditionJson,
            String actionType,
            String actionConfigJson,
            int retryLimit
    ) {}

    public record PreviewMatch(
            String reference,
            String summary,
            UUID clientId,
            String effectiveDate
    ) {}

    public record PreviewResult(
            UUID ruleId,
            long definitionVersion,
            String triggerType,
            int matchCount,
            List<PreviewMatch> matches
    ) {}
}
