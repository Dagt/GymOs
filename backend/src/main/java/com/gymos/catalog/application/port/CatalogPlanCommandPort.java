package com.gymos.catalog.application.port;

import java.time.OffsetDateTime;
import java.util.UUID;

public interface CatalogPlanCommandPort {
	void create(PlanMutation mutation);

	void update(PlanMutation mutation);

	record PlanMutation(
			UUID organizationId, UUID planId, UUID productId, int durationDays, int graceDays,
			String benefitsJson, boolean multiBranchAccess, String status,
			OffsetDateTime now, UUID userId
	) {
	}
}
