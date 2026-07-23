package com.gymos.catalog.infrastructure;

import com.gymos.catalog.infrastructure.CatalogRepository.ProductSummary;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class CatalogProductQueryRepository {
	private final JdbcClient jdbcClient;

	CatalogProductQueryRepository(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	List<ProductSummary> list(UUID organizationId) {
		return jdbcClient.sql("""
				SELECT id, sku, name, description, product_type, price_cents, currency,
				       taxable, status, created_at, updated_at
				FROM catalog_product
				WHERE organization_id = :organizationId
				  AND deleted_at IS NULL
				ORDER BY name
				""")
				.param("organizationId", organizationId)
				.query(CatalogRowMappers.productSummary())
				.list();
	}

	Optional<ProductSummary> find(UUID organizationId, UUID productId) {
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
				.query(CatalogRowMappers.productSummary())
				.optional();
	}
}
