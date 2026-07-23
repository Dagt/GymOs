package com.gymos.catalog.infrastructure;

import com.gymos.catalog.application.query.ProductSummaryView;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class CatalogProductQueryRepository {
	private static final String PRODUCT_COLUMNS = """
			id, sku, name, description, product_type, price_cents, currency,
			taxable, status, barcode, brand, presentation, unit_of_measure,
			inventory_tracked, display_order, image_object_key, created_at, updated_at
			""";

	private final JdbcClient jdbcClient;

	CatalogProductQueryRepository(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	List<ProductSummaryView> list(UUID organizationId) {
		return jdbcClient.sql("""
				SELECT %s
				FROM catalog_product
				WHERE organization_id = :organizationId
				  AND deleted_at IS NULL
				  AND archived_at IS NULL
				ORDER BY display_order, name
				""".formatted(PRODUCT_COLUMNS))
				.param("organizationId", organizationId)
				.query(CatalogRowMappers.productSummary())
				.list();
	}

	Optional<ProductSummaryView> find(UUID organizationId, UUID productId) {
		return jdbcClient.sql("""
				SELECT %s
				FROM catalog_product
				WHERE organization_id = :organizationId
				  AND id = :productId
				  AND deleted_at IS NULL
				  AND archived_at IS NULL
				""".formatted(PRODUCT_COLUMNS))
				.param("organizationId", organizationId)
				.param("productId", productId)
				.query(CatalogRowMappers.productSummary())
				.optional();
	}
}
