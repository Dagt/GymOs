package com.gymos.catalog.application.port;

import com.gymos.catalog.application.query.PlanView;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CatalogPlanQueryPort {
	List<PlanView> listPlans(UUID organizationId);

	List<PlanView> listPlans(UUID organizationId, UUID branchId);

	Optional<PlanView> findPlan(UUID organizationId, UUID planId);

	Optional<PlanView> findPlan(UUID organizationId, UUID branchId, UUID planId);

	Optional<PlanView> findPlanByProduct(UUID organizationId, UUID productId);
}
