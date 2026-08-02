package com.gymos.automations.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gymos.automations.api.operations.AutomationDomainEvents;
import com.gymos.automations.infrastructure.AutomationDomainEventRepository;
import com.gymos.automations.infrastructure.AutomationDomainEventRepository.DomainEventRecord;
import com.gymos.automations.infrastructure.AutomationRepository;
import com.gymos.automations.infrastructure.AutomationRepository.AutomationRuleRecord;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AutomationDomainEventService implements AutomationDomainEvents {
    private static final Set<String> SUPPORTED_EVENTS = Set.of(
            "PAYMENT_COMPLETED",
            "PAYMENT_FAILED",
            "BOOKING_CREATED",
            "BOOKING_CANCELLED",
            "MEMBERSHIP_CREATED",
            "MEMBERSHIP_RENEWED",
            "MEMBERSHIP_CANCELLED"
    );

    private final AutomationDomainEventRepository eventRepository;
    private final AutomationRepository automationRepository;
    private final AutomationService automationService;
    private final ObjectMapper objectMapper;

    public AutomationDomainEventService(
            AutomationDomainEventRepository eventRepository,
            AutomationRepository automationRepository,
            AutomationService automationService,
            ObjectMapper objectMapper
    ) {
        this.eventRepository = eventRepository;
        this.automationRepository = automationRepository;
        this.automationService = automationService;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public UUID publish(DomainEvent event) {
        String eventType = normalizeEventType(event.eventType());
        validatePayload(event.payloadJson());
        DomainEventRecord stored = eventRepository.append(
                event.organizationId(), event.branchId(), eventType,
                required(event.eventId(), "eventId"), Math.max(1, event.eventVersion()),
                event.occurredAt() == null ? OffsetDateTime.now() : event.occurredAt(), event.payloadJson()
        );
        return stored.id();
    }

    @Transactional
    public int processPending(int limit) {
        int processed = 0;
        for (DomainEventRecord event : eventRepository.pending(limit)) {
            try {
                materialize(event);
                eventRepository.markProcessed(event.id());
                processed++;
            } catch (RuntimeException exception) {
                eventRepository.markFailed(event.id(), exception.getMessage());
            }
        }
        return processed;
    }

    private void materialize(DomainEventRecord event) {
        for (AutomationRuleRecord rule : automationRepository.enabledRulesByTrigger(event.eventType(), 500)) {
            if (!rule.organizationId().equals(event.organizationId())) continue;
            if (rule.branchId() != null && !rule.branchId().equals(event.branchId())) continue;
            automationService.enqueueDomainOccurrence(
                    rule,
                    "event:" + rule.id() + ":" + event.eventType() + ":" + event.eventId(),
                    event.eventType().toLowerCase(Locale.ROOT) + ":" + event.eventId(),
                    event.occurredAt(),
                    mergeVariables(rule, event)
            );
        }
    }

    private String mergeVariables(AutomationRuleRecord rule, DomainEventRecord event) {
        try {
            JsonNode actionNode = objectMapper.readTree(rule.actionConfigJson());
            ObjectNode action = actionNode != null && actionNode.isObject()
                    ? (ObjectNode) actionNode.deepCopy() : objectMapper.createObjectNode();
            JsonNode payload = objectMapper.readTree(event.payloadJson());
            ObjectNode variables = action.withObject("variables");
            variables.put("eventId", event.eventId());
            variables.put("eventType", event.eventType());
            variables.put("eventVersion", event.eventVersion());
            variables.put("occurredAt", event.occurredAt().toString());
            if (payload != null && payload.isObject()) {
                payload.fields().forEachRemaining(entry -> {
                    JsonNode value = entry.getValue();
                    if (value.isValueNode()) variables.set(entry.getKey(), value);
                });
            }
            return objectMapper.writeValueAsString(action);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Invalid automation domain event payload", exception);
        }
    }

    private String normalizeEventType(String value) {
        String normalized = required(value, "eventType").toUpperCase(Locale.ROOT);
        if (!SUPPORTED_EVENTS.contains(normalized)) {
            throw new IllegalArgumentException("Unsupported automation domain event: " + normalized);
        }
        return normalized;
    }

    private void validatePayload(String payloadJson) {
        try {
            JsonNode payload = objectMapper.readTree(payloadJson);
            if (payload == null || !payload.isObject()) {
                throw new IllegalArgumentException("Domain event payload must be a JSON object");
            }
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Domain event payload is invalid JSON", exception);
        }
    }

    private String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value.trim();
    }
}
