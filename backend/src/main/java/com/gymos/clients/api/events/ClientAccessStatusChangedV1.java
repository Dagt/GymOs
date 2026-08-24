package com.gymos.clients.api.events;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ClientAccessStatusChangedV1(
		String eventKey,
		UUID organizationId,
		UUID clientId,
		String previousStatus,
		String currentStatus,
		OffsetDateTime occurredAt
) implements Serializable {
}
