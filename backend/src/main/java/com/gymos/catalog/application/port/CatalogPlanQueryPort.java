package com.gymos.catalog.application.port;

import com.gymos.catalog.application.query.PlanView;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CatalogPlanQueryPort {
	List<PlanView> listPlans(UUID organizationId);

	Optional<PlanView> findPlan(UUID organizationId, UUID planId);
}
