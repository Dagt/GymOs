package com.gymos.catalog.application.query;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ProductHistoryEntryView(
		UUID id,
		String action,
		UUID actorUserId,
		String actorRole,
		String reason,
		String origin,
		UUID correlationId,
		OffsetDateTime occurredAt
) {
}
