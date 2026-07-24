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
				WHERE organization_id = :organizationId AND deleted_at IS NULL
				%s
				ORDER BY display_order, name, id
				""".formatted(statusFilter))
				.param("organizationId", organizationId).query(this::map).list();
	}

	@Override
	public Optional<CategoryView> findCategory(UUID organizationId, UUID categoryId) {
		return jdbcClient.sql("""
				SELECT id, parent_id, name, slug, description, status, display_order, created_at, updated_at
				FROM catalog_category
				WHERE organization_id = :organizationId AND id = :categoryId AND deleted_at IS NULL
				""")
				.param("organizationId", organizationId).param("categoryId", categoryId)
				.query(this::map).optional();
	}

	@Override
	public boolean slugExists(UUID organizationId, String slug, UUID excludedCategoryId) {
		String exclusion = excludedCategoryId == null ? "" : " AND id <> :excludedCategoryId";
		var query = jdbcClient.sql("SELECT COUNT(*) FROM catalog_category WHERE organization_id=:organizationId AND slug=:slug AND deleted_at IS NULL" + exclusion)
				.param("organizationId", organizationId).param("slug", slug);
		if (excludedCategoryId != null) query = query.param("excludedCategoryId", excludedCategoryId);
		return query.query(Integer.class).single() > 0;
	}

	@Override
	public boolean wouldCreateCycle(UUID organizationId, UUID categoryId, UUID parentId) {
		if (parentId == null) return false;
		return Boolean.TRUE.equals(jdbcClient.sql("""
				WITH RECURSIVE ancestors AS (
				    SELECT id, parent_id FROM catalog_category
				    WHERE organization_id=:organizationId AND id=:parentId AND deleted_at IS NULL
				    UNION ALL
				    SELECT c.id, c.parent_id FROM catalog_category c
				    JOIN ancestors a ON c.id = a.parent_id
				    WHERE c.organization_id=:organizationId AND c.deleted_at IS NULL
				)
				SELECT EXISTS (SELECT 1 FROM ancestors WHERE id=:categoryId)
				""")
				.param("organizationId", organizationId).param("parentId", parentId).param("categoryId", categoryId)
				.query(Boolean.class).single());
	}

	@Override
	public boolean hasActiveChildren(UUID organizationId, UUID categoryId) {
		return exists("SELECT EXISTS (SELECT 1 FROM catalog_category WHERE organization_id=:organizationId AND parent_id=:categoryId AND deleted_at IS NULL AND status='ACTIVE')", organizationId, categoryId);
	}

	@Override
	public boolean hasActiveProducts(UUID organizationId, UUID categoryId) {
		return exists("""
				SELECT EXISTS (
				 SELECT 1 FROM catalog_product_category pc
				 JOIN catalog_product p ON p.id=pc.product_id AND p.organization_id=pc.organization_id
				 WHERE pc.organization_id=:organizationId AND pc.category_id=:categoryId
				 AND p.deleted_at IS NULL AND p.archived_at IS NULL AND p.status='ACTIVE')
				""", organizationId, categoryId);
	}

	private boolean exists(String sql, UUID organizationId, UUID categoryId) {
		return Boolean.TRUE.equals(jdbcClient.sql(sql).param("organizationId", organizationId).param("categoryId", categoryId).query(Boolean.class).single());
	}

	@Override
	public void createCategory(CategoryMutation mutation) {
		jdbcClient.sql("""
				INSERT INTO catalog_category (
				 id, organization_id, parent_id, name, slug, description, status, display_order,
				 created_at, updated_at, created_by, updated_by, version)
				VALUES (:id,:organizationId,:parentId,:name,:slug,:description,:status,:displayOrder,:now,:now,:userId,:userId,0)
				""")
				.param("id", mutation.categoryId()).param("organizationId", mutation.organizationId())
				.param("parentId", mutation.parentId()).param("name", mutation.name()).param("slug", mutation.slug())
				.param("description", mutation.description()).param("status", mutation.status())
				.param("displayOrder", mutation.displayOrder()).param("now", mutation.now()).param("userId", mutation.userId()).update();
	}

	@Override
	public int updateCategory(CategoryMutation mutation) {
		return jdbcClient.sql("""
				UPDATE catalog_category SET parent_id=:parentId,name=:name,slug=:slug,description=:description,
				 status=:status,display_order=:displayOrder,updated_at=:now,updated_by=:userId,version=version+1
				WHERE organization_id=:organizationId AND id=:id AND deleted_at IS NULL
				""")
				.param("parentId", mutation.parentId()).param("name", mutation.name()).param("slug", mutation.slug())
				.param("description", mutation.description()).param("status", mutation.status())
				.param("displayOrder", mutation.displayOrder()).param("now", mutation.now()).param("userId", mutation.userId())
				.param("organizationId", mutation.organizationId()).param("id", mutation.categoryId()).update();
	}

	@Override
	public int archiveCategory(UUID organizationId, UUID categoryId, UUID userId, OffsetDateTime now) {
		return jdbcClient.sql("""
				UPDATE catalog_category SET status='INACTIVE',deleted_at=:now,updated_at=:now,updated_by=:userId,version=version+1
				WHERE organization_id=:organizationId AND id=:categoryId AND deleted_at IS NULL
				""")
				.param("now", now).param("userId", userId).param("organizationId", organizationId)
				.param("categoryId", categoryId).update();
	}

	private CategoryView map(java.sql.ResultSet rs, int rowNumber) throws java.sql.SQLException {
		return new CategoryView(rs.getObject("id", UUID.class), rs.getObject("parent_id", UUID.class), rs.getString("name"),
				rs.getString("slug"), rs.getString("description"), rs.getString("status"), rs.getInt("display_order"),
				rs.getObject("created_at", OffsetDateTime.class), rs.getObject("updated_at", OffsetDateTime.class));
	}
}
