package com.gymos.catalog.infrastructure;

import com.gymos.catalog.application.port.CatalogProductQueryPort;
import com.gymos.catalog.application.query.ProductPageView;
import com.gymos.catalog.application.query.ProductSearchCriteria;
import com.gymos.catalog.application.query.ProductSummaryView;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class CatalogProductQueryRepository implements CatalogProductQueryPort {
	private static final String PRODUCT_COLUMNS = """
			cp.id AS id, cp.sku, cp.name, cp.description, cp.product_type, cp.price_cents, cp.currency,
			cp.taxable, cp.status, cp.barcode, cp.brand, cp.presentation, cp.unit_of_measure,
			cp.inventory_tracked, cp.display_order, cp.image_object_key,
			category.id AS category_id, category.name AS category_name, tags.tags_csv,
			cp.archived_at, cp.created_at, cp.updated_at
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
	private static final String TAG_JOIN = """
			LEFT JOIN LATERAL (
			    SELECT STRING_AGG(t.name, ',' ORDER BY t.name) AS tags_csv
			    FROM catalog_product_tag pt
			    JOIN catalog_tag t ON t.id = pt.tag_id
			        AND t.organization_id = pt.organization_id
			    WHERE pt.organization_id = cp.organization_id
			      AND pt.product_id = cp.id
			) tags ON TRUE
			""";

	private final JdbcClient jdbcClient;

	CatalogProductQueryRepository(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	@Override
	public List<ProductSummaryView> listProducts(UUID organizationId) {
		return jdbcClient.sql(baseSelect() + """
				WHERE cp.organization_id = :organizationId
				  AND cp.deleted_at IS NULL
				  AND cp.archived_at IS NULL
				ORDER BY cp.display_order, cp.name
				""")
				.param("organizationId", organizationId)
				.query(CatalogRowMappers.productSummary())
				.list();
	}

	@Override
	public Optional<ProductSummaryView> findProduct(UUID organizationId, UUID productId) {
		return find(organizationId, productId, false);
	}

	@Override
	public Optional<ProductSummaryView> findProductIncludingArchived(UUID organizationId, UUID productId) {
		return find(organizationId, productId, true);
	}

	private Optional<ProductSummaryView> find(UUID organizationId, UUID productId, boolean includeArchived) {
		String archivedFilter = includeArchived ? "" : "AND cp.archived_at IS NULL";
		return jdbcClient.sql(baseSelect() + """
				WHERE cp.organization_id = :organizationId
				  AND cp.id = :productId
				  AND cp.deleted_at IS NULL
				  %s
				""".formatted(archivedFilter))
				.param("organizationId", organizationId)
				.param("productId", productId)
				.query(CatalogRowMappers.productSummary())
				.optional();
	}

	@Override
	public ProductPageView search(UUID organizationId, ProductSearchCriteria criteria) {
		String filters = filters(criteria);
		String querySql = baseSelect() + """
				WHERE cp.organization_id = :organizationId
				%s
				ORDER BY %s
				LIMIT :size OFFSET :offset
				""".formatted(filters, orderBy(criteria.sort()));
		List<ProductSummaryView> items = bind(jdbcClient.sql(querySql), organizationId, criteria)
				.param("size", criteria.size()).param("offset", criteria.page() * criteria.size())
				.query(CatalogRowMappers.productSummary()).list();
		String countSql = "SELECT COUNT(*) FROM catalog_product cp WHERE cp.organization_id = :organizationId " + filters;
		long total = bind(jdbcClient.sql(countSql), organizationId, criteria).query(Long.class).single();
		return ProductPageView.of(items, criteria.page(), criteria.size(), total);
	}

	@Override
	public boolean skuExists(UUID organizationId, String sku, UUID excludedProductId) {
		return existsBy("sku", organizationId, sku, excludedProductId);
	}

	@Override
	public boolean barcodeExists(UUID organizationId, String barcode, UUID excludedProductId) {
		return existsBy("barcode", organizationId, barcode, excludedProductId);
	}

	private boolean existsBy(String column, UUID organizationId, String value, UUID excludedProductId) {
		String excluded = excludedProductId == null ? "" : "AND id <> :excludedProductId";
		var spec = jdbcClient.sql("SELECT EXISTS (SELECT 1 FROM catalog_product WHERE organization_id = :organizationId AND "
				+ column + " = :value AND deleted_at IS NULL AND archived_at IS NULL " + excluded + ")")
				.param("organizationId", organizationId).param("value", value);
		if (excludedProductId != null) spec = spec.param("excludedProductId", excludedProductId);
		return Boolean.TRUE.equals(spec.query(Boolean.class).single());
	}

	private String baseSelect() {
		return "SELECT " + PRODUCT_COLUMNS + " FROM catalog_product cp " + CATEGORY_JOIN + TAG_JOIN + " ";
	}

	private String filters(ProductSearchCriteria criteria) {
		StringBuilder filters = new StringBuilder("\n AND cp.deleted_at IS NULL");
		if (!criteria.includeArchived()) filters.append("\n AND cp.archived_at IS NULL");
		if (hasText(criteria.query())) filters.append("""
				 AND (LOWER(cp.name) LIKE :query OR LOWER(cp.sku) LIKE :query
				 OR LOWER(COALESCE(cp.barcode, '')) LIKE :query OR EXISTS (
				 SELECT 1 FROM catalog_product_tag spt JOIN catalog_tag st ON st.id = spt.tag_id
				 WHERE spt.organization_id = cp.organization_id AND spt.product_id = cp.id
				 AND st.normalized_name LIKE :query))
				""");
		if (hasText(criteria.productType())) filters.append("\n AND cp.product_type = :productType");
		if (hasText(criteria.status())) filters.append("\n AND cp.status = :status");
		if (criteria.inventoryTracked() != null) filters.append("\n AND cp.inventory_tracked = :inventoryTracked");
		if (criteria.categoryId() != null) filters.append("""
				 AND EXISTS (SELECT 1 FROM catalog_product_category pc
				 WHERE pc.organization_id = :organizationId AND pc.product_id = cp.id AND pc.category_id = :categoryId)
				""");
		return filters.toString();
	}

	private JdbcClient.StatementSpec bind(JdbcClient.StatementSpec spec, UUID organizationId, ProductSearchCriteria criteria) {
		spec = spec.param("organizationId", organizationId);
		if (hasText(criteria.query())) spec = spec.param("query", "%" + criteria.query().trim().toLowerCase() + "%");
		if (hasText(criteria.productType())) spec = spec.param("productType", criteria.productType().trim().toUpperCase());
		if (hasText(criteria.status())) spec = spec.param("status", criteria.status().trim().toUpperCase());
		if (criteria.inventoryTracked() != null) spec = spec.param("inventoryTracked", criteria.inventoryTracked());
		if (criteria.categoryId() != null) spec = spec.param("categoryId", criteria.categoryId());
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
