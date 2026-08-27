package com.gymos.catalog.api.events;

import com.gymos.common.security.GymOsPrincipal;
import java.util.List;
import java.util.UUID;

/**
 * Requests inventory defaults for every independently sellable variant created
 * by one catalog batch. The synchronous inventory listener participates in the
 * catalog transaction so a failure rolls the complete batch back.
 */
public record ProductVariantInventoryDefaultsRequestedV1(
		GymOsPrincipal principal,
		List<UUID> productIds,
		int reorderPoint,
		String correlationId
) {
	public ProductVariantInventoryDefaultsRequestedV1 {
		productIds = List.copyOf(productIds);
	}
}
