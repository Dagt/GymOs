package com.gymos.access.application.command;

import java.time.OffsetDateTime;
import java.util.UUID;

public record TemporaryPassCommand(
		UUID clientId,
		OffsetDateTime startsAt,
		OffsetDateTime expiresAt,
		int maxUses,
		String reason
) {
}
