package com.gymos.catalog.application.port;

import java.util.UUID;

public interface CatalogDependencyQueryPort {
	boolean hasInventoryMovements(UUID organizationId, UUID productId);

	boolean hasActiveMembershipPlan(UUID organizationId, UUID productId);
}
