package com.gymos.automations.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymos.automations.infrastructure.AutomationRuleSnapshotRepository.AutomationRuleSnapshot;
import com.gymos.automations.infrastructure.OutboundMessageRepository;
import com.gymos.automations.infrastructure.OutboundMessageRepository.CreateOutboundMessageData;
import com.gymos.clients.api.operations.ClientLookupOperations;
import com.gymos.clients.api.operations.SelfServiceClientProfile;
import com.gymos.common.api.BusinessException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class CustomerMessageQueue {
    private final ClientLookupOperations clients;
    private final OutboundMessageRepository messages;
    private final ObjectMapper objectMapper;

    public CustomerMessageQueue(
            ClientLookupOperations clients,
            OutboundMessageRepository messages,
            ObjectMapper objectMapper
    ) {
        this.clients = clients;
        this.messages = messages;
        this.objectMapper = objectMapper;
    }

    public UUID enqueue(AutomationRuleSnapshot snapshot, JsonNode config) {
        JsonNode variables = config.path("variables");
        UUID clientId = uuid(variables.path("clientId").asText(null), "AUTOMATION_CLIENT_REQUIRED");
        SelfServiceClientProfile client = clients.selfServiceProfile(snapshot.organizationId(), clientId)
                .orElseThrow(() -> new BusinessException(
                        "AUTOMATION_CLIENT_NOT_FOUND", "El socio destinatario no existe o no esta disponible."
                ));

        List<String> channels = channels(config.path("channels"));
        if (channels.isEmpty()) {
            throw new BusinessException("AUTOMATION_CHANNEL_REQUIRED", "Selecciona al menos un canal de comunicacion.");
        }

        UUID batchId = UUID.randomUUID();
        String templateCode = text(config, "templateCode", snapshot.triggerType());
        int templateVersion = Math.max(1, config.path("templateVersion").asInt(1));
        String communicationType = text(config, "communicationType", "OPERATIONAL").toUpperCase(Locale.ROOT);
        String subject = render(text(config, "subject", snapshot.ruleName()), variables, client);
        String body = render(text(config, "body", ""), variables, client);
        if (body.isBlank()) {
            throw new BusinessException("AUTOMATION_MESSAGE_BODY_REQUIRED", "La comunicacion necesita contenido.");
        }

        for (String channel : channels) {
            String recipient = recipient(channel, client);
            String status = recipient == null ? "UNDELIVERABLE" : "PENDING";
            String idempotencyKey = String.join(":",
                    "customer-message",
                    snapshot.workItemId().toString(),
                    channel,
                    String.valueOf(templateVersion)
            );
            messages.enqueue(new CreateOutboundMessageData(
                    batchId,
                    snapshot.organizationId(),
                    snapshot.branchId(),
                    snapshot.ruleId(),
                    snapshot.workItemId(),
                    clientId,
                    channel,
                    recipient,
                    "EMAIL".equals(channel) ? subject : null,
                    body,
                    templateCode,
                    templateVersion,
                    communicationType,
                    status,
                    idempotencyKey
            ));
        }
        return batchId;
    }

    private List<String> channels(JsonNode node) {
        List<String> result = new ArrayList<>();
        if (node.isArray()) {
            node.forEach(item -> addChannel(result, item.asText("")));
        } else if (node.isTextual()) {
            addChannel(result, node.asText());
        }
        return result;
    }

    private void addChannel(List<String> result, String value) {
        String channel = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!List.of("EMAIL", "WHATSAPP", "PUSH").contains(channel)) {
            throw new BusinessException("AUTOMATION_CHANNEL_UNSUPPORTED", "El canal configurado no esta soportado.");
        }
        if (!result.contains(channel)) result.add(channel);
    }

    private String recipient(String channel, SelfServiceClientProfile client) {
        return switch (channel) {
            case "EMAIL" -> normalizeEmail(client.email());
            case "WHATSAPP" -> normalizePhone(client.phone());
            case "PUSH" -> null;
            default -> null;
        };
    }

    private String normalizeEmail(String value) {
        if (value == null || value.isBlank() || !value.contains("@")) return null;
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizePhone(String value) {
        if (value == null || value.isBlank()) return null;
        String digits = value.replaceAll("\\D", "");
        if (digits.startsWith("521") && digits.length() == 13) digits = "52" + digits.substring(3);
        if (digits.length() == 10) digits = "52" + digits;
        return digits.length() == 12 && digits.startsWith("52") ? "+" + digits : null;
    }

    private String render(String template, JsonNode variables, SelfServiceClientProfile client) {
        String result = template
                .replace("{{clientId}}", client.clientId().toString())
                .replace("{{clientName}}", client.displayName() == null ? "" : client.displayName());
        if (variables.isObject()) {
            var fields = variables.fields();
            while (fields.hasNext()) {
                var entry = fields.next();
                result = result.replace("{{" + entry.getKey() + "}}", entry.getValue().asText(""));
            }
        }
        return result;
    }

    private UUID uuid(String value, String code) {
        try {
            return UUID.fromString(value);
        } catch (RuntimeException exception) {
            throw new BusinessException(code, "La automatizacion no contiene un socio destinatario valido.");
        }
    }

    private String text(JsonNode node, String field, String fallback) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? fallback : value.asText(fallback);
    }
}
