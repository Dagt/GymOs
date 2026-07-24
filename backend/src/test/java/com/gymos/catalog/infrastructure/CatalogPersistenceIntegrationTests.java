package com.gymos.catalog.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gymos.catalog.application.port.CatalogProductCommandPort.ProductMutation;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class CatalogPersistenceIntegrationTests {
	@Container
	static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");

	private JdbcClient jdbcClient;
	private CatalogProductCommandRepository products;
	private CatalogProductQueryRepository productQueries;
	private UUID organizationId;
	private UUID secondOrganizationId;
	private UUID firstBranchId;
	private UUID secondBranchId;
	private UUID userId;

	@BeforeEach
	void setUp() {
		var dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
		Flyway.configure().dataSource(dataSource).locations("classpath:db/migration")
				.cleanDisabled(false).load().clean();
		Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
		jdbcClient = JdbcClient.create(dataSource);
		products = new CatalogProductCommandRepository(jdbcClient);
		productQueries = new CatalogProductQueryRepository(jdbcClient);
		organizationId = UUID.randomUUID();
		secondOrganizationId = UUID.randomUUID();
		firstBranchId = UUID.randomUUID();
		secondBranchId = UUID.randomUUID();
		userId = UUID.randomUUID();
		insertOrganization(organizationId, "Catalog One");
		insertOrganization(secondOrganizationId, "Catalog Two");
		insertBranch(organizationId, firstBranchId, "CENTRO");
		insertBranch(organizationId, secondBranchId, "NORTE");
	}

	@Test
	void productIsVisibleOnlyInAttachedBranch() {
		UUID productId = UUID.randomUUID();
		OffsetDateTime now = OffsetDateTime.now();
		products.create(new ProductMutation(
				organizationId, productId, "SKU-BRANCH", "Producto Centro", null, "PHYSICAL", 1_000,
				"MXN", true, "ACTIVE", null, null, null, null, true, 0, null, now, userId
		));
		products.attachBranch(organizationId, firstBranchId, productId, userId, now);

		assertThat(productQueries.list(organizationId, firstBranchId))
				.extracting(com.gymos.catalog.application.query.ProductSummaryView::id)
				.containsExactly(productId);
		assertThat(productQueries.list(organizationId, secondBranchId)).isEmpty();
	}

	@Test
	void staleProductVersionCannotOverwriteNewerUpdate() {
		UUID productId = UUID.randomUUID();
		OffsetDateTime now = OffsetDateTime.now();
		products.create(new ProductMutation(
				organizationId, productId, "SKU-LOCK", "Original", null, "PHYSICAL", 1_000,
				"MXN", true, "ACTIVE", null, null, null, null, true, 0, null, now, userId
		));
		ProductMutation firstUpdate = mutation(productId, "Primera edicion", 0L);

		assertThat(products.update(firstUpdate)).isEqualTo(1);
		assertThat(products.update(firstUpdate)).isZero();
		assertThat(jdbcClient.sql("SELECT name FROM catalog_product WHERE id=:id")
				.param("id", productId).query(String.class).single()).isEqualTo("Primera edicion");
		assertThat(jdbcClient.sql("SELECT version FROM catalog_product WHERE id=:id")
				.param("id", productId).query(Long.class).single()).isEqualTo(1L);
	}

	@Test
	void productCategoryRelationCannotCrossOrganizations() {
		UUID productId = UUID.randomUUID();
		UUID categoryId = UUID.randomUUID();
		OffsetDateTime now = OffsetDateTime.now();
		products.create(new ProductMutation(
				organizationId, productId, "SKU-TENANT", "Tenant Product", null, "PHYSICAL", 1_000,
				"MXN", true, "ACTIVE", null, null, null, null, true, 0, null, now, userId
		));
		insertCategory(secondOrganizationId, categoryId, "Foreign", "foreign");

		assertThatThrownBy(() -> jdbcClient.sql("""
				INSERT INTO catalog_product_category (organization_id, product_id, category_id, created_at, created_by)
				VALUES (:organizationId, :productId, :categoryId, now(), :userId)
				""")
				.param("organizationId", organizationId)
				.param("productId", productId)
				.param("categoryId", categoryId)
				.param("userId", userId)
				.update()).isInstanceOf(DataAccessException.class);
	}

	@Test
	void archivedCategorySlugCanBeReused() {
		UUID firstCategoryId = UUID.randomUUID();
		insertCategory(organizationId, firstCategoryId, "Bebidas", "bebidas");
		jdbcClient.sql("UPDATE catalog_category SET deleted_at=now(), status='INACTIVE' WHERE id=:id")
				.param("id", firstCategoryId).update();

		insertCategory(organizationId, UUID.randomUUID(), "Bebidas", "bebidas");

		assertThat(jdbcClient.sql("SELECT COUNT(*) FROM catalog_category WHERE organization_id=:organizationId AND slug='bebidas'")
				.param("organizationId", organizationId).query(Long.class).single()).isEqualTo(2L);
	}

	private ProductMutation mutation(UUID productId, String name, long expectedVersion) {
		return new ProductMutation(
				organizationId, productId, "SKU-LOCK", name, null, "PHYSICAL", "GENERAL_PRODUCT",
				1_000, "MXN", true, "ACTIVE", null, null, null, null, true, 0, null,
				null, null, null, null, null, expectedVersion, OffsetDateTime.now(), userId
		);
	}

	private void insertOrganization(UUID id, String name) {
		jdbcClient.sql("""
				INSERT INTO organization (
				 id, legal_name, trade_name, status, timezone, default_currency, created_at, updated_at, version)
				VALUES (:id, :name, :name, 'ACTIVE', 'America/Mexico_City', 'MXN', now(), now(), 0)
				""")
				.param("id", id).param("name", name).update();
	}

	private void insertBranch(UUID organization, UUID id, String code) {
		jdbcClient.sql("""
				INSERT INTO branch (
				 id, organization_id, name, code, status, timezone, created_at, updated_at, version)
				VALUES (:id, :organizationId, :name, :code, 'ACTIVE', 'America/Mexico_City', now(), now(), 0)
				""")
				.param("id", id).param("organizationId", organization)
				.param("name", code).param("code", code).update();
	}

	private void insertCategory(UUID organization, UUID categoryId, String name, String slug) {
		jdbcClient.sql("""
				INSERT INTO catalog_category (
				 id, organization_id, parent_id, name, slug, description, status, display_order,
				 created_at, updated_at, created_by, updated_by, version)
				VALUES (:id, :organizationId, null, :name, :slug, null, 'ACTIVE', 0,
				 now(), now(), :userId, :userId, 0)
				""")
				.param("id", categoryId).param("organizationId", organization)
				.param("name", name).param("slug", slug).param("userId", userId).update();
	}
}
