package com.gymos.catalog.application.port;

import com.gymos.catalog.application.query.ProductFamilySummaryView;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CatalogProductFamilyPort {
	List<ProductFamilySummaryView> list(UUID organizationId, UUID branchId);
	Optional<ProductFamilySummaryView> find(UUID organizationId, UUID familyId);
	Optional<ProductFamilySummaryView> findByIdentity(UUID organizationId, String name, String brand);
	void create(FamilyMutation family);

	record FamilyMutation(
			UUID id,
			UUID organizationId,
			String name,
			String brand,
			String description,
			OffsetDateTime now,
			UUID userId
	) {
	}
}
