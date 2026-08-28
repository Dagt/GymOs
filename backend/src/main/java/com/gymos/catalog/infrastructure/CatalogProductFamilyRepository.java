package com.gymos.catalog.infrastructure;

import com.gymos.catalog.application.port.CatalogProductFamilyPort;
import com.gymos.catalog.application.query.ProductFamilySummaryView;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class CatalogProductFamilyRepository implements CatalogProductFamilyPort {
	private final JdbcClient jdbcClient;

	CatalogProductFamilyRepository(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	@Override
	public List<ProductFamilySummaryView> list(UUID organizationId, UUID branchId) {
		return jdbcClient.sql("""
				SELECT family.id, family.name, family.brand, family.description,
				       COUNT(product.id) FILTER (WHERE product.status='ACTIVE' AND product.archived_at IS NULL) AS active_variants,
				       COUNT(product.id) AS total_variants
				FROM catalog_product_family family
				LEFT JOIN catalog_product product
				  ON product.organization_id=family.organization_id AND product.family_id=family.id
				 AND product.deleted_at IS NULL
				 AND (:branchId IS NULL OR EXISTS (
				     SELECT 1 FROM catalog_product_branch branch_product
				     WHERE branch_product.organization_id=product.organization_id
				       AND branch_product.product_id=product.id AND branch_product.branch_id=:branchId
				 ))
				WHERE family.organization_id=:organizationId
				GROUP BY family.id, family.name, family.brand, family.description
				ORDER BY LOWER(family.name), LOWER(COALESCE(family.brand,'')), family.id
				""")
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.query(this::map)
				.list();
	}

	@Override
	public Optional<ProductFamilySummaryView> find(UUID organizationId, UUID familyId) {
		return jdbcClient.sql("""
				SELECT family.id, family.name, family.brand, family.description,
				       COUNT(product.id) FILTER (WHERE product.status='ACTIVE' AND product.archived_at IS NULL) AS active_variants,
				       COUNT(product.id) AS total_variants
				FROM catalog_product_family family
				LEFT JOIN catalog_product product
				  ON product.organization_id=family.organization_id AND product.family_id=family.id
				 AND product.deleted_at IS NULL
				WHERE family.organization_id=:organizationId AND family.id=:familyId
				GROUP BY family.id, family.name, family.brand, family.description
				""")
				.param("organizationId", organizationId)
				.param("familyId", familyId)
				.query(this::map)
				.optional();
	}

	@Override
	public Optional<ProductFamilySummaryView> findByIdentity(UUID organizationId, String name, String brand) {
		return jdbcClient.sql("""
				SELECT family.id, family.name, family.brand, family.description,
				       COUNT(product.id) FILTER (WHERE product.status='ACTIVE' AND product.archived_at IS NULL) AS active_variants,
				       COUNT(product.id) AS total_variants
				FROM catalog_product_family family
				LEFT JOIN catalog_product product
				  ON product.organization_id=family.organization_id AND product.family_id=family.id
				 AND product.deleted_at IS NULL
				WHERE family.organization_id=:organizationId
				  AND LOWER(BTRIM(family.name))=LOWER(BTRIM(:name))
				  AND LOWER(COALESCE(BTRIM(family.brand),''))=LOWER(COALESCE(BTRIM(:brand),''))
				GROUP BY family.id, family.name, family.brand, family.description
				""")
				.param("organizationId", organizationId)
				.param("name", name)
				.param("brand", brand)
				.query(this::map)
				.optional();
	}

	@Override
	public void create(FamilyMutation family) {
		jdbcClient.sql("""
				INSERT INTO catalog_product_family (
				 id, organization_id, name, brand, description,
				 created_at, updated_at, created_by, updated_by, version
				) VALUES (
				 :id, :organizationId, :name, :brand, :description,
				 :now, :now, :userId, :userId, 0
				)
				""")
				.param("id", family.id())
				.param("organizationId", family.organizationId())
				.param("name", family.name())
				.param("brand", family.brand())
				.param("description", family.description())
				.param("now", family.now())
				.param("userId", family.userId())
				.update();
	}

	private ProductFamilySummaryView map(java.sql.ResultSet resultSet, int rowNumber) throws java.sql.SQLException {
		return new ProductFamilySummaryView(
				resultSet.getObject("id", UUID.class), resultSet.getString("name"),
				resultSet.getString("brand"), resultSet.getString("description"),
				resultSet.getLong("active_variants"), resultSet.getLong("total_variants")
		);
	}
}
