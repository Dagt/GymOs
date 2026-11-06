package com.gymos.common.security;

import java.time.Instant;
import java.util.UUID;

public record SessionContext(UUID userId, AccountScope accountScope, UUID organizationId, UUID branchId,
		boolean supportMode, Instant supportExpiresAt, long credentialVersion, String fingerprint, Instant expiresAt) {
	public static SessionContext from(GymOsPrincipal principal, String fingerprint, Instant expiresAt) {
		return new SessionContext(principal.userId(), principal.accountScope(), principal.organizationId(),
				principal.branchId(), principal.supportMode(), principal.supportExpiresAt(), principal.credentialVersion(),
				fingerprint, expiresAt);
	}

	public String serialize() {
		return String.join("|", userId.toString(), accountScope.name(),
				organizationId == null ? "" : organizationId.toString(), branchId == null ? "" : branchId.toString(),
				Boolean.toString(supportMode), supportExpiresAt == null ? "" : Long.toString(supportExpiresAt.getEpochSecond()),
				Long.toString(credentialVersion), fingerprint, Long.toString(expiresAt.toEpochMilli()));
	}
}
