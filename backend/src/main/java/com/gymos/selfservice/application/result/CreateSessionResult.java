package com.gymos.selfservice.application.result;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CreateSessionResult(
		UUID sessionId,
		String sessionToken,
		String terminalName,
		OffsetDateTime expiresAt,
		boolean verified
) {
}
