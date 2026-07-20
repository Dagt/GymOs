package com.gymos.selfservice.application.result;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CreateSessionResult(
		UUID sessionId,
		String sessionToken,
		String terminalName,
		String organizationName,
		String brandName,
		String logoUrl,
		String primaryColor,
		String accentColor,
		String surfaceColor,
		OffsetDateTime expiresAt,
		boolean verified
) {
}
