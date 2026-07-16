package com.gymos.memberships.api.events;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record MembershipPlanChangedV1(
		String eventKey,
		UUID organizationId,
		UUID branchId,
		UUID clientId,
		UUID sourceMembershipId,
		UUID changedMembershipId,
		UUID targetPlanId,
		UUID saleId,
		LocalDate startsOn,
		LocalDate endsOn,
		OffsetDateTime occurredAt
) implements Serializable {
}
