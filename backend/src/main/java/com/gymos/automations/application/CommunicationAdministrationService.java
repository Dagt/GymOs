package com.gymos.automations.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymos.automations.infrastructure.CommunicationAdministrationRepository;
import com.gymos.automations.infrastructure.CommunicationAdministrationRepository.DeliverySummary;
import com.gymos.automations.infrastructure.CommunicationAdministrationRepository.PreferenceRecord;
import com.gymos.automations.infrastructure.CommunicationAdministrationRepository.PreferenceUpdate;
import com.gymos.automations.infrastructure.CommunicationAdministrationRepository.TemplateDraft;
import com.gymos.automations.infrastructure.CommunicationAdministrationRepository.TemplateRecord;
import com.gymos.clients.api.operations.ClientLookupOperations;
import com.gymos.common.api.BusinessException;
import com.gymos.common.security.GymOsPrincipal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommunicationAdministrationService {
    private static final Set<String> CHANNELS = Set.of("EMAIL", "WHATSAPP");
    private static final Set<String> STATUSES = Set.of("DRAFT", "ACTIVE", "ARCHIVED");

    private final CommunicationAdministrationRepository repository;
    private final ClientLookupOperations clients;
    private final ObjectMapper objectMapper;

    public CommunicationAdministrationService(
            CommunicationAdministrationRepository repository,
            ClientLookupOperations clients,
            ObjectMapper objectMapper
    ) {
        this.repository = repository;
        this.clients = clients;
        this.objectMapper = objectMapper;
    }

    public List<TemplateRecord> templates(GymOsPrincipal principal) {
        requireRead(principal);
        return repository.templates(principal.organizationId(), principal.branchId());
    }

    @Transactional
    public TemplateRecord createTemplate(GymOsPrincipal principal, CreateTemplate command) {
        requireManage(principal);
        String channel = normalized(command.channel());
        String status = normalized(command.status());
        if (!CHANNELS.contains(channel)) {
            throw new BusinessException("COMMUNICATION_CHANNEL_UNSUPPORTED", "El canal de la plantilla no esta soportado.");
        }
        if (!STATUSES.contains(status)) {
            throw new BusinessException("COMMUNICATION_TEMPLATE_STATUS_INVALID", "El estado de la plantilla no es valido.");
        }
        String variables = validateVariables(command.allowedVariablesJson());
        validateTemplateVariables(command.subject(), command.body(), variables);
        UUID id = repository.createTemplate(
                principal.organizationId(), principal.branchId(), principal.userId(),
                new TemplateDraft(
                        required(command.code(), "COMMUNICATION_TEMPLATE_CODE_REQUIRED").toUpperCase(Locale.ROOT),
                        channel,
                        command.locale() == null || command.locale().isBlank() ? "es-MX" : command.locale().trim(),
                        blankToNull(command.subject()),
                        required(command.body(), "COMMUNICATION_TEMPLATE_BODY_REQUIRED"),
                        variables,
                        status
                )
        );
        return repository.template(principal.organizationId(), principal.branchId(), id).orElseThrow();
    }

    @Transactional
    public void archiveTemplate(GymOsPrincipal principal, UUID templateId) {
        requireManage(principal);
        if (!repository.archiveTemplate(principal.organizationId(), principal.branchId(), templateId)) {
            throw new BusinessException("COMMUNICATION_TEMPLATE_NOT_FOUND", "La plantilla no existe o ya esta archivada.");
        }
    }

    public PreferenceRecord preference(GymOsPrincipal principal, UUID clientId) {
        requireRead(principal);
        requireClient(principal, clientId);
        return repository.preference(principal.organizationId(), clientId);
    }

    @Transactional
    public PreferenceRecord savePreference(GymOsPrincipal principal, UUID clientId, UpdatePreference command) {
        requireManage(principal);
        requireClient(principal, clientId);
        if ((command.emailMarketingEnabled() || command.whatsappMarketingEnabled())
                && (command.consentVersion() == null || command.consentVersion().isBlank() || !command.consented())) {
            throw new BusinessException(
                    "COMMUNICATION_MARKETING_CONSENT_REQUIRED",
                    "El marketing requiere consentimiento explicito y una version de consentimiento."
            );
        }
        return repository.savePreference(
                principal.organizationId(), clientId, principal.userId(),
                new PreferenceUpdate(
                        command.emailOperationalEnabled(),
                        command.whatsappOperationalEnabled(),
                        command.emailMarketingEnabled(),
                        command.whatsappMarketingEnabled(),
                        blankToNull(command.consentVersion()),
                        command.consented()
                )
        );
    }

    public List<DeliverySummary> deliverySummary(GymOsPrincipal principal, int days) {
        requireRead(principal);
        int safeDays = Math.max(1, Math.min(days, 365));
        return repository.deliverySummary(
                principal.organizationId(), principal.branchId(), OffsetDateTime.now().minusDays(safeDays)
        );
    }

    private void requireClient(GymOsPrincipal principal, UUID clientId) {
        if (clients.selfServiceProfile(principal.organizationId(), clientId).isEmpty()) {
            throw new BusinessException("CLIENT_NOT_FOUND", "El socio no existe en la organizacion activa.");
        }
    }

    private String validateVariables(String json) {
        String value = json == null || json.isBlank() ? "[]" : json.trim();
        try {
            JsonNode node = objectMapper.readTree(value);
            if (!node.isArray()) {
                throw new BusinessException("COMMUNICATION_TEMPLATE_VARIABLES_INVALID", "Las variables permitidas deben ser una lista.");
            }
            for (JsonNode item : node) {
                if (!item.isTextual() || !item.asText().matches("[A-Za-z][A-Za-z0-9_]*")) {
                    throw new BusinessException("COMMUNICATION_TEMPLATE_VARIABLES_INVALID", "Existe una variable de plantilla invalida.");
                }
            }
            return value;
        } catch (JsonProcessingException exception) {
            throw new BusinessException("COMMUNICATION_TEMPLATE_VARIABLES_INVALID", "Las variables permitidas no contienen JSON valido.");
        }
    }

    private void validateTemplateVariables(String subject, String body, String variablesJson) {
        try {
            Set<String> allowed = new java.util.HashSet<>();
            objectMapper.readTree(variablesJson).forEach(node -> allowed.add(node.asText()));
            String content = (subject == null ? "" : subject) + "\n" + (body == null ? "" : body);
            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\{\\{([A-Za-z][A-Za-z0-9_]*)}}")
                    .matcher(content);
            while (matcher.find()) {
                if (!allowed.contains(matcher.group(1))) {
                    throw new BusinessException(
                            "COMMUNICATION_TEMPLATE_VARIABLE_UNKNOWN",
                            "La plantilla usa una variable no declarada: " + matcher.group(1)
                    );
                }
            }
        } catch (JsonProcessingException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private void requireRead(GymOsPrincipal principal) {
        requirePrincipal(principal);
        if (!principal.permissions().contains("automation.read")
                && !principal.permissions().contains("automation.manage")
                && !principal.permissions().contains("platform.admin")) {
            throw new BusinessException("FORBIDDEN", "No tienes permiso para consultar comunicaciones.");
        }
    }

    private void requireManage(GymOsPrincipal principal) {
        requirePrincipal(principal);
        if (!principal.permissions().contains("automation.manage")
                && !principal.permissions().contains("platform.admin")) {
            throw new BusinessException("FORBIDDEN", "No tienes permiso para administrar comunicaciones.");
        }
    }

    private void requirePrincipal(GymOsPrincipal principal) {
        if (principal == null || principal.organizationId() == null) {
            throw new BusinessException("AUTHENTICATION_REQUIRED", "Debes iniciar sesion para continuar.");
        }
    }

    private String required(String value, String code) {
        if (value == null || value.isBlank()) throw new BusinessException(code, "Falta un dato requerido.");
        return value.trim();
    }

    private String normalized(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record CreateTemplate(String code, String channel, String locale, String subject,
                                 String body, String allowedVariablesJson, String status) {}

    public record UpdatePreference(boolean emailOperationalEnabled, boolean whatsappOperationalEnabled,
                                   boolean emailMarketingEnabled, boolean whatsappMarketingEnabled,
                                   String consentVersion, boolean consented) {}
}
