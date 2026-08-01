package com.gymos.automations.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymos.audit.api.AuditService;
import com.gymos.automations.infrastructure.AutomationRepository;
import com.gymos.automations.infrastructure.AutomationRepository.AutomationRuleRecord;
import com.gymos.automations.infrastructure.AutomationRepository.CreateRuleData;
import com.gymos.common.api.BusinessException;
import com.gymos.common.security.GymOsPrincipal;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DomainAutomationRuleService {
    private static final Set<String> IMPLEMENTED_TRIGGERS = Set.of(
            "MEMBERSHIP_EXPIRING",
            "MEMBERSHIP_EXPIRED",
            "MEMBERSHIP_RENEWED",
            "MEMBERSHIP_CANCELLED",
            "PAYMENT_COMPLETED",
            "PAYMENT_FAILED",
            "BOOKING_CREATED",
            "BOOKING_CANCELLED"
    );

    private final AutomationRepository automationRepository;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public DomainAutomationRuleService(
            AutomationRepository automationRepository,
            AuditService auditService,
            ObjectMapper objectMapper
    ) {
        this.automationRepository = automationRepository;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public AutomationRuleRecord create(GymOsPrincipal principal, CreateDomainRule command, String correlationId) {
        requirePermission(principal);
        String triggerType = required(command.triggerType(), "AUTOMATION_TRIGGER_REQUIRED").toUpperCase(Locale.ROOT);
        if (!IMPLEMENTED_TRIGGERS.contains(triggerType)) {
            throw new BusinessException(
                    "AUTOMATION_TRIGGER_UNSUPPORTED",
                    "El disparador de dominio no tiene un consumidor implementado."
            );
        }
        String conditionJson = validJson(command.conditionJson());
        String actionConfigJson = validJson(command.actionConfigJson());
        validate(triggerType, conditionJson, actionConfigJson);
        UUID id = automationRepository.createRule(
                principal.organizationId(),
                principal.branchId(),
                principal.userId(),
                new CreateRuleData(
                        required(command.name(), "AUTOMATION_NAME_REQUIRED"),
                        triggerType,
                        conditionJson,
                        "CREATE_NOTIFICATION",
                        actionConfigJson,
                        Math.max(0, Math.min(command.retryLimit(), 10)),
                        null,
                        null
                )
        );
        auditService.record(principal, "AUTOMATION_RULE_CREATED", "AUTOMATION_RULE", id,
                "BACKOFFICE", "domain-trigger:" + triggerType, correlationId);
        return automationRepository.rule(principal.organizationId(), principal.branchId(), id).orElseThrow();
    }

    private void validate(String triggerType, String conditionJson, String actionConfigJson) {
        try {
            JsonNode condition = objectMapper.readTree(conditionJson);
            JsonNode action = objectMapper.readTree(actionConfigJson);
            if (action.path("body").asText("").isBlank()) {
                throw new BusinessException("AUTOMATION_NOTIFICATION_BODY_REQUIRED", "La notificacion necesita un mensaje.");
            }
            if ("MEMBERSHIP_EXPIRING".equals(triggerType)) {
                int days = condition.path("daysBeforeExpiry").asInt(0);
                if (days < 1 || days > 90) {
                    throw new BusinessException(
                            "AUTOMATION_DAYS_BEFORE_INVALID",
                            "Los dias de anticipacion deben estar entre 1 y 90."
                    );
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

    private String required(String value, String code) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(code, "Falta un dato requerido de la automatizacion.");
        }
        return value.trim();
    }

    private void requirePermission(GymOsPrincipal principal) {
        if (principal == null) {
            throw new BusinessException("AUTHENTICATION_REQUIRED", "Debes iniciar sesion para continuar.");
        }
        if (principal.branchId() == null) {
            throw new BusinessException("BRANCH_REQUIRED", "Selecciona una sucursal para operar.");
        }
        if (!principal.permissions().contains("automation.manage")
                && !principal.permissions().contains("platform.admin")) {
            throw new BusinessException("FORBIDDEN", "No tienes permiso para crear automatizaciones.");
        }
    }

    public record CreateDomainRule(
            String name,
            String triggerType,
            String conditionJson,
            String actionConfigJson,
            int retryLimit
    ) {}
}
