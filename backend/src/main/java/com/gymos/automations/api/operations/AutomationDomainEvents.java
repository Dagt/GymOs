package com.gymos.automations.api.operations;

import java.time.OffsetDateTime;
import java.util.UUID;

public interface AutomationDomainEvents {
    UUID publish(DomainEvent event);

    record DomainEvent(
            UUID organizationId,
            UUID branchId,
            String eventType,
            String eventId,
            int eventVersion,
            OffsetDateTime occurredAt,
            String payloadJson
    ) {}
}
