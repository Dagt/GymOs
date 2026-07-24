package com.gymos.catalog.application.query;

public record ProductDependencyView(
		long saleItems,
		long inventoryMovements,
		long branchesWithStock,
		long activeMembershipPlans,
		long activeProductsInCategory
) {
}
