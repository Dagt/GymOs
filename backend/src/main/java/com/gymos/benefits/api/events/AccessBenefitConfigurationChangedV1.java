package com.gymos.benefits.api.events;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record AccessBenefitConfigurationChangedV1(
		String eventKey,
		UUID organizationId,
		UUID branchId,
		UUID benefitId,
		List<UUID> planIds,
		OffsetDateTime occurredAt
) implements Serializable {
	public AccessBenefitConfigurationChangedV1 {
		planIds = planIds == null ? List.of() : List.copyOf(planIds);
	}
}
