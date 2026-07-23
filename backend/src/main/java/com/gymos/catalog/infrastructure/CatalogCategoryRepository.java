package com.gymos.catalog.infrastructure;

import com.gymos.catalog.application.port.CatalogCategoryCommandPort;
import com.gymos.catalog.application.port.CatalogCategoryQueryPort;
import com.gymos.catalog.application.query.CategoryView;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class CatalogCategoryRepository implements CatalogCategoryQueryPort, CatalogCategoryCommandPort {
	private final JdbcClient jdbcClient;

	CatalogCategoryRepository(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	@Override
	public List<CategoryView> listCategories(UUID organizationId, boolean includeInactive) {
		String statusFilter = includeInactive ? "" : " AND status = 'ACTIVE'";
		return jdbcClient.sql("""
				SELECT id, parent_id, name, slug, description, status, display_order, created_at, updated_at
				FROM catalog_category
				WHERE organization_id = :organizationId
				  AND deleted_at IS NULL
				%s
				ORDER BY display_order, name, id
				""".formatted(statusFilter))
				.param("organizationId", organizationId)
				.query(this::map)
				.list();
	}

	@Override
	public Optional<CategoryView> findCategory(UUID organizationId, UUID categoryId) {
		return jdbcClient.sql("""
				SELECT id, parent_id, name, slug, description, status, display_order, created_at, updated_at
				FROM catalog_category
				WHERE organization_id = :organizationId
				  AND id = :categoryId
				  AND deleted_at IS NULL
				""")
				.param("organizationId", organizationId)
				.param("categoryId", categoryId)
				.query(this::map)
				.optional();
	}

	@Override
	public boolean slugExists(UUID organizationId, String slug, UUID excludedCategoryId) {
		String exclusion = excludedCategoryId == null ? "" : " AND id <> :excludedCategoryId";
		var query = jdbcClient.sql("""
				SELECT COUNT(*)
				FROM catalog_category
				WHERE organization_id = :organizationId
				  AND slug = :slug
				  AND deleted_at IS NULL
				%s
				""".formatted(exclusion))
				.param("organizationId", organizationId)
				.param("slug", slug);
		if (excludedCategoryId != null) {
			query = query.param("excludedCategoryId", excludedCategoryId);
		}
		return query.query(Integer.class).single() > 0;
	}

	@Override
	public void createCategory(CategoryMutation mutation) {
		jdbcClient.sql("""
				INSERT INTO catalog_category (
				    id, organization_id, parent_id, name, slug, description, status, display_order,
				    created_at, updated_at, created_by, updated_by, version
				)
				VALUES (
				    :id, :organizationId, :parentId, :name, :slug, :description, :status, :displayOrder,
				    :now, :now, :userId, :userId, 0
				)
				""")
				.param("id", mutation.categoryId())
				.param("organizationId", mutation.organizationId())
				.param("parentId", mutation.parentId())
				.param("name", mutation.name())
				.param("slug", mutation.slug())
				.param("description", mutation.description())
				.param("status", mutation.status())
				.param("displayOrder", mutation.displayOrder())
				.param("now", mutation.now())
				.param("userId", mutation.userId())
				.update();
	}

	@Override
	public int updateCategory(CategoryMutation mutation) {
		return jdbcClient.sql("""
				UPDATE catalog_category
				SET parent_id = :parentId,
				    name = :name,
				    slug = :slug,
				    description = :description,
				    status = :status,
				    display_order = :displayOrder,
				    updated_at = :now,
				    updated_by = :userId,
				    version = version + 1
				WHERE organization_id = :organizationId
				  AND id = :id
				  AND deleted_at IS NULL
				""")
				.param("parentId", mutation.parentId())
				.param("name", mutation.name())
				.param("slug", mutation.slug())
				.param("description", mutation.description())
				.param("status", mutation.status())
				.param("displayOrder", mutation.displayOrder())
				.param("now", mutation.now())
				.param("userId", mutation.userId())
				.param("organizationId", mutation.organizationId())
				.param("id", mutation.categoryId())
				.update();
	}

	@Override
	public int archiveCategory(UUID organizationId, UUID categoryId, UUID userId, OffsetDateTime now) {
		return jdbcClient.sql("""
				UPDATE catalog_category
				SET status = 'INACTIVE',
				    deleted_at = :now,
				    updated_at = :now,
				    updated_by = :userId,
				    version = version + 1
				WHERE organization_id = :organizationId
				  AND id = :categoryId
				  AND deleted_at IS NULL
				""")
				.param("now", now)
				.param("userId", userId)
				.param("organizationId", organizationId)
				.param("categoryId", categoryId)
				.update();
	}

	private CategoryView map(java.sql.ResultSet resultSet, int rowNumber) throws java.sql.SQLException {
		return new CategoryView(
				resultSet.getObject("id", UUID.class),
				resultSet.getObject("parent_id", UUID.class),
				resultSet.getString("name"),
				resultSet.getString("slug"),
				resultSet.getString("description"),
				resultSet.getString("status"),
				resultSet.getInt("display_order"),
				resultSet.getObject("created_at", OffsetDateTime.class),
				resultSet.getObject("updated_at", OffsetDateTime.class)
		);
	}
}
