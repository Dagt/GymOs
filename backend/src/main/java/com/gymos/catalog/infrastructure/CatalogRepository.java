package com.gymos.catalog.infrastructure;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class CatalogRepository {
	private final JdbcClient jdbcClient;

	public CatalogRepository(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	public List<ProductSummary> products(UUID organizationId) {
		return jdbcClient.sql("""
				SELECT id, sku, name, description, product_type, price_cents, currency,
				       taxable, status, created_at, updated_at
				FROM catalog_product
				WHERE organization_id = :organizationId
				  AND deleted_at IS NULL
				ORDER BY name
				""")
				.param("organizationId", organizationId)
				.query((resultSet, rowNumber) -> new ProductSummary(
						resultSet.getObject("id", UUID.class),
						resultSet.getString("sku"),
						resultSet.getString("name"),
						resultSet.getString("description"),
						resultSet.getString("product_type"),
						resultSet.getLong("price_cents"),
						resultSet.getString("currency"),
						resultSet.getBoolean("taxable"),
						resultSet.getString("status"),
						resultSet.getObject("created_at", OffsetDateTime.class),
						resultSet.getObject("updated_at", OffsetDateTime.class)
				))
				.list();
	}

	public Optional<ProductSummary> product(UUID organizationId, UUID productId) {
		return jdbcClient.sql("""
				SELECT id, sku, name, description, product_type, price_cents, currency,
				       taxable, status, created_at, updated_at
				FROM catalog_product
				WHERE organization_id = :organizationId
				  AND id = :productId
				  AND deleted_at IS NULL
				""")
				.param("organizationId", organizationId)
				.param("productId", productId)
				.query((resultSet, rowNumber) -> new ProductSummary(
						resultSet.getObject("id", UUID.class),
						resultSet.getString("sku"),
						resultSet.getString("name"),
						resultSet.getString("description"),
						resultSet.getString("product_type"),
						resultSet.getLong("price_cents"),
						resultSet.getString("currency"),
						resultSet.getBoolean("taxable"),
						resultSet.getString("status"),
						resultSet.getObject("created_at", OffsetDateTime.class),
						resultSet.getObject("updated_at", OffsetDateTime.class)
				))
				.optional();
	}

	public boolean skuExists(UUID organizationId, String sku, UUID excludedProductId) {
		if (excludedProductId == null) {
			Integer count = jdbcClient.sql("""
					SELECT COUNT(*)
					FROM catalog_product
					WHERE organization_id = :organizationId
					  AND sku = :sku
					  AND deleted_at IS NULL
					""")
					.param("organizationId", organizationId)
					.param("sku", sku)
					.query(Integer.class)
					.single();
			return count > 0;
		}
		Integer count = jdbcClient.sql("""
				SELECT COUNT(*)
				FROM catalog_product
				WHERE organization_id = :organizationId
				  AND sku = :sku
				  AND deleted_at IS NULL
				  AND id <> :excludedProductId
				""")
				.param("organizationId", organizationId)
				.param("sku", sku)
				.param("excludedProductId", excludedProductId)
				.query(Integer.class)
				.single();
		return count > 0;
	}

	public List<PlanSummary> plans(UUID organizationId) {
		return jdbcClient.sql("""
				SELECT mp.id, mp.product_id, cp.sku, cp.name, cp.price_cents, cp.currency,
				       mp.duration_days, mp.grace_days, mp.benefits_json::text AS benefits_json,
				       mp.multi_branch_access, mp.status, mp.created_at, mp.updated_at
				FROM membership_plan mp
				JOIN catalog_product cp ON cp.id = mp.product_id
				WHERE mp.organization_id = :organizationId
				  AND mp.deleted_at IS NULL
				  AND cp.deleted_at IS NULL
				ORDER BY cp.name
				""")
				.param("organizationId", organizationId)
				.query((resultSet, rowNumber) -> new PlanSummary(
						resultSet.getObject("id", UUID.class),
						resultSet.getObject("product_id", UUID.class),
						resultSet.getString("sku"),
						resultSet.getString("name"),
						resultSet.getLong("price_cents"),
						resultSet.getString("currency"),
						resultSet.getInt("duration_days"),
						resultSet.getInt("grace_days"),
						resultSet.getString("benefits_json"),
						resultSet.getBoolean("multi_branch_access"),
						resultSet.getString("status"),
						resultSet.getObject("created_at", OffsetDateTime.class),
						resultSet.getObject("updated_at", OffsetDateTime.class)
				))
				.list();
	}

	public Optional<PlanSummary> plan(UUID organizationId, UUID planId) {
		return jdbcClient.sql("""
				SELECT mp.id, mp.product_id, cp.sku, cp.name, cp.price_cents, cp.currency,
				       mp.duration_days, mp.grace_days, mp.benefits_json::text AS benefits_json,
				       mp.multi_branch_access, mp.status, mp.created_at, mp.updated_at
				FROM membership_plan mp
				JOIN catalog_product cp ON cp.id = mp.product_id
				WHERE mp.organization_id = :organizationId
				  AND mp.id = :planId
				  AND mp.deleted_at IS NULL
				  AND cp.deleted_at IS NULL
				""")
				.param("organizationId", organizationId)
				.param("planId", planId)
				.query((resultSet, rowNumber) -> new PlanSummary(
						resultSet.getObject("id", UUID.class),
						resultSet.getObject("product_id", UUID.class),
						resultSet.getString("sku"),
						resultSet.getString("name"),
						resultSet.getLong("price_cents"),
						resultSet.getString("currency"),
						resultSet.getInt("duration_days"),
						resultSet.getInt("grace_days"),
						resultSet.getString("benefits_json"),
						resultSet.getBoolean("multi_branch_access"),
						resultSet.getString("status"),
						resultSet.getObject("created_at", OffsetDateTime.class),
						resultSet.getObject("updated_at", OffsetDateTime.class)
				))
				.optional();
	}

	public record ProductSummary(
			UUID id,
			String sku,
			String name,
			String description,
			String productType,
			long priceCents,
			String currency,
			boolean taxable,
			String status,
			OffsetDateTime createdAt,
			OffsetDateTime updatedAt
	) {
	}

	public record PlanSummary(
			UUID id,
			UUID productId,
			String sku,
			String name,
			long priceCents,
			String currency,
			int durationDays,
			int graceDays,
			String benefitsJson,
			boolean multiBranchAccess,
			String status,
			OffsetDateTime createdAt,
			OffsetDateTime updatedAt
	) {
	}
}
