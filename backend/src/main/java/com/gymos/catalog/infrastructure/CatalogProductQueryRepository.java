package com.gymos.catalog.infrastructure;

import com.gymos.catalog.application.query.ProductSummaryView;
import com.gymos.catalog.application.query.ProductPageView;
import com.gymos.catalog.application.query.ProductSearchCriteria;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class CatalogProductQueryRepository {
	private static final String PRODUCT_COLUMNS = """
			cp.id AS id, cp.sku, cp.name, cp.description, cp.product_type, cp.price_cents, cp.currency,
			cp.taxable, cp.status, cp.barcode, cp.brand, cp.presentation, cp.unit_of_measure,
			cp.inventory_tracked, cp.display_order, cp.image_object_key,
			category.id AS category_id, category.name AS category_name, cp.created_at, cp.updated_at
			""";
	private static final String CATEGORY_JOIN = """
			LEFT JOIN LATERAL (
			    SELECT c.id, c.name
			    FROM catalog_product_category pc
			    JOIN catalog_category c ON c.id = pc.category_id
			        AND c.organization_id = pc.organization_id
			        AND c.deleted_at IS NULL
			    WHERE pc.organization_id = cp.organization_id
			      AND pc.product_id = cp.id
			    ORDER BY c.display_order, c.name
			    LIMIT 1
			) category ON TRUE
			""";

	private final JdbcClient jdbcClient;

	CatalogProductQueryRepository(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	List<ProductSummaryView> list(UUID organizationId) {
		return jdbcClient.sql("""
				SELECT %s
				FROM catalog_product cp
				%s
				WHERE cp.organization_id = :organizationId
				  AND cp.deleted_at IS NULL
				  AND cp.archived_at IS NULL
				ORDER BY cp.display_order, cp.name
				""".formatted(PRODUCT_COLUMNS, CATEGORY_JOIN))
				.param("organizationId", organizationId)
				.query(CatalogRowMappers.productSummary())
				.list();
	}

	Optional<ProductSummaryView> find(UUID organizationId, UUID productId) {
		return jdbcClient.sql("""
				SELECT %s
				FROM catalog_product cp
				%s
				WHERE cp.organization_id = :organizationId
				  AND cp.id = :productId
				  AND cp.deleted_at IS NULL
				  AND cp.archived_at IS NULL
				""".formatted(PRODUCT_COLUMNS, CATEGORY_JOIN))
				.param("organizationId", organizationId)
				.param("productId", productId)
				.query(CatalogRowMappers.productSummary())
				.optional();
	}

	ProductPageView search(UUID organizationId, ProductSearchCriteria criteria) {
		String filters = filters(criteria);
		String orderBy = orderBy(criteria.sort());
		String querySql = """
				SELECT %s
				FROM catalog_product cp
				%s
				WHERE cp.organization_id = :organizationId
				%s
				ORDER BY %s
				LIMIT :size OFFSET :offset
				""".formatted(PRODUCT_COLUMNS, CATEGORY_JOIN, filters, orderBy);
		var query = bind(jdbcClient.sql(querySql), organizationId, criteria)
				.param("size", criteria.size())
				.param("offset", criteria.page() * criteria.size());
		List<ProductSummaryView> items = query.query(CatalogRowMappers.productSummary()).list();

		String countSql = """
				SELECT COUNT(*)
				FROM catalog_product cp
				WHERE cp.organization_id = :organizationId
				%s
				""".formatted(filters);
		long total = bind(jdbcClient.sql(countSql), organizationId, criteria)
				.query(Long.class)
				.single();
		return ProductPageView.of(items, criteria.page(), criteria.size(), total);
	}

	private String filters(ProductSearchCriteria criteria) {
		StringBuilder filters = new StringBuilder("\n AND cp.deleted_at IS NULL");
		if (!criteria.includeArchived()) {
			filters.append("\n AND cp.archived_at IS NULL");
		}
		if (hasText(criteria.query())) {
			filters.append("""

					 AND (
					     LOWER(cp.name) LIKE :query
					     OR LOWER(cp.sku) LIKE :query
					     OR LOWER(COALESCE(cp.barcode, '')) LIKE :query
					 )
					""");
		}
		if (hasText(criteria.productType())) {
			filters.append("\n AND cp.product_type = :productType");
		}
		if (hasText(criteria.status())) {
			filters.append("\n AND cp.status = :status");
		}
		if (criteria.inventoryTracked() != null) {
			filters.append("\n AND cp.inventory_tracked = :inventoryTracked");
		}
		if (criteria.categoryId() != null) {
			filters.append("""

					 AND EXISTS (
					     SELECT 1
					     FROM catalog_product_category product_category
					     WHERE product_category.organization_id = :organizationId
					       AND product_category.product_id = cp.id
					       AND product_category.category_id = :categoryId
					 )
					""");
		}
		return filters.toString();
	}

	private JdbcClient.StatementSpec bind(
			JdbcClient.StatementSpec spec,
			UUID organizationId,
			ProductSearchCriteria criteria
	) {
		spec = spec.param("organizationId", organizationId);
		if (hasText(criteria.query())) {
			spec = spec.param("query", "%" + criteria.query().trim().toLowerCase() + "%");
		}
		if (hasText(criteria.productType())) {
			spec = spec.param("productType", criteria.productType().trim().toUpperCase());
		}
		if (hasText(criteria.status())) {
			spec = spec.param("status", criteria.status().trim().toUpperCase());
		}
		if (criteria.inventoryTracked() != null) {
			spec = spec.param("inventoryTracked", criteria.inventoryTracked());
		}
		if (criteria.categoryId() != null) {
			spec = spec.param("categoryId", criteria.categoryId());
		}
		return spec;
	}

	private String orderBy(String sort) {
		return switch (sort) {
			case "name,desc" -> "cp.name DESC, cp.id";
			case "createdAt,asc" -> "cp.created_at ASC, cp.id";
			case "createdAt,desc" -> "cp.created_at DESC, cp.id";
			case "price,asc" -> "cp.price_cents ASC, cp.id";
			case "price,desc" -> "cp.price_cents DESC, cp.id";
			case "displayOrder,desc" -> "cp.display_order DESC, cp.name, cp.id";
			default -> "cp.display_order ASC, cp.name, cp.id";
		};
	}

	private boolean hasText(String value) {
		return value != null && !value.isBlank();
	}
}
