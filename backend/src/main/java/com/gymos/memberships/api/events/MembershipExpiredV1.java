package com.gymos.memberships.api.events;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record MembershipExpiredV1(
		String eventKey,
		UUID organizationId,
		UUID branchId,
		UUID clientId,
		UUID membershipId,
		UUID planId,
		LocalDate endsOn,
		LocalDate graceEndsOn,
		LocalDate expiredOn,
		OffsetDateTime occurredAt
) implements Serializable {
}
