package com.gymos.clients.api.operations;

import java.util.UUID;

public record ClientVerificationResult(
		UUID clientId,
		String displayName
) {
}
