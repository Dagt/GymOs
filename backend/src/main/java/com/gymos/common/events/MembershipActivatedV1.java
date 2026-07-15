package com.gymos.common.events;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record MembershipActivatedV1(
		String eventKey,
		UUID organizationId,
		UUID branchId,
		UUID clientId,
		UUID membershipId,
		UUID planId,
		UUID saleId,
		LocalDate startsOn,
		LocalDate endsOn,
		OffsetDateTime occurredAt
) implements Serializable {
}
