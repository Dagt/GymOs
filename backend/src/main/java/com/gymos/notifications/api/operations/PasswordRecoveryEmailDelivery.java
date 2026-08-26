package com.gymos.notifications.api.operations;

import java.time.OffsetDateTime;
import java.util.UUID;

public interface PasswordRecoveryEmailDelivery {
	String deliver(RecoveryEmail email);
	record RecoveryEmail(UUID id, UUID organizationId, String recipientName, String recipientEmail, String url, OffsetDateTime expiresAt) {
		@Override public String toString() { return "RecoveryEmail[id=" + id + ", credentials=REDACTED]"; }
	}
}
