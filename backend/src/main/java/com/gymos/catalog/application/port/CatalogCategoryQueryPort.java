package com.gymos.catalog.application.port;

import com.gymos.catalog.application.query.CategoryView;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CatalogCategoryQueryPort {
	List<CategoryView> listCategories(UUID organizationId, boolean includeInactive);

	Optional<CategoryView> findCategory(UUID organizationId, UUID categoryId);

	boolean slugExists(UUID organizationId, String slug, UUID excludedCategoryId);

	boolean wouldCreateCycle(UUID organizationId, UUID categoryId, UUID parentId);

	boolean hasActiveChildren(UUID organizationId, UUID categoryId);

	boolean hasActiveProducts(UUID organizationId, UUID categoryId);
}
