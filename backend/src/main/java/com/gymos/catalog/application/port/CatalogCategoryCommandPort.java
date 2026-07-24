package com.gymos.catalog.application.port;

import java.time.OffsetDateTime;
import java.util.UUID;

public interface CatalogCategoryCommandPort {
	void createCategory(CategoryMutation mutation);

	int updateCategory(CategoryMutation mutation);

	int archiveCategory(UUID organizationId, UUID categoryId, UUID userId, OffsetDateTime now);

	record CategoryMutation(
			UUID organizationId,
			UUID categoryId,
			UUID parentId,
			String name,
			String slug,
			String description,
			String status,
			int displayOrder,
			OffsetDateTime now,
			UUID userId
	) {
	}
}
