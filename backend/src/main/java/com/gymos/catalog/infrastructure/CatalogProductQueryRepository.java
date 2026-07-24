package com.gymos.catalog.infrastructure;

import com.gymos.catalog.application.query.ProductPageView;
import com.gymos.catalog.application.query.ProductSearchCriteria;
import com.gymos.catalog.application.query.ProductSummaryView;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class CatalogProductQueryRepository {
	private static final String PRODUCT_COLUMNS = """
			cp.id AS id, cp.sku, cp.name, cp.description, cp.product_type, cp.commercial_subtype,
			cp.price_cents, cp.currency, cp.taxable, cp.status, cp.barcode, cp.brand, cp.presentation,
			cp.unit_of_measure, cp.inventory_tracked, cp.display_order, cp.image_object_key,
			cp.supplier_name, cp.reference_cost_cents, cp.promotional_price_cents,
			cp.promotion_starts_at, cp.promotion_ends_at,
			category.id AS category_id, category.name AS category_name, tags.tags_csv,
			cp.archived_at, cp.created_at, cp.updated_at, cp.version
			""";
	private static final String CATEGORY_JOIN = """
			LEFT JOIN LATERAL (
			 SELECT c.id, c.name FROM catalog_product_category pc
			 JOIN catalog_category c ON c.id = pc.category_id AND c.organization_id = pc.organization_id AND c.deleted_at IS NULL
			 WHERE pc.organization_id = cp.organization_id AND pc.product_id = cp.id
			 ORDER BY c.display_order, c.name LIMIT 1
			) category ON TRUE
			""";
	private static final String TAG_JOIN = """
			LEFT JOIN LATERAL (
			 SELECT STRING_AGG(t.name, ',' ORDER BY t.name) AS tags_csv
			 FROM catalog_product_tag pt JOIN catalog_tag t ON t.id = pt.tag_id AND t.organization_id = pt.organization_id
			 WHERE pt.organization_id = cp.organization_id AND pt.product_id = cp.id
			) tags ON TRUE
			""";
	private final JdbcClient jdbcClient;

	CatalogProductQueryRepository(JdbcClient jdbcClient) { this.jdbcClient = jdbcClient; }

	List<ProductSummaryView> list(UUID organizationId) {
		return list(organizationId, null);
	}

	List<ProductSummaryView> list(UUID organizationId, UUID branchId) {
		String sql = baseSelect() + " WHERE cp.organization_id=:organizationId" + availability(branchId)
				+ " AND cp.product_type<>'MEMBERSHIP_PLAN' AND cp.deleted_at IS NULL AND cp.archived_at IS NULL"
				+ " ORDER BY cp.display_order, cp.name";
		return bindBranch(jdbcClient.sql(sql).param("organizationId", organizationId), branchId)
				.query(CatalogRowMappers.productSummary()).list();
	}

	Optional<ProductSummaryView> find(UUID organizationId, UUID productId) {
		return find(organizationId, null, productId, false);
	}

	Optional<ProductSummaryView> find(UUID organizationId, UUID branchId, UUID productId) {
		return find(organizationId, branchId, productId, false);
	}

	Optional<ProductSummaryView> findIncludingArchived(UUID organizationId, UUID productId) {
		return find(organizationId, null, productId, true);
	}

	Optional<ProductSummaryView> findIncludingArchived(UUID organizationId, UUID branchId, UUID productId) {
		return find(organizationId, branchId, productId, true);
	}

	Optional<ProductSummaryView> findActiveByBarcode(UUID organizationId, String barcode) {
		return findActiveByBarcode(organizationId, null, barcode);
	}

	Optional<ProductSummaryView> findActiveByBarcode(UUID organizationId, UUID branchId, String barcode) {
		String sql = baseSelect() + " WHERE cp.organization_id=:organizationId" + availability(branchId)
				+ " AND cp.product_type<>'MEMBERSHIP_PLAN' AND cp.barcode=:barcode AND cp.status='ACTIVE'"
				+ " AND cp.deleted_at IS NULL AND cp.archived_at IS NULL";
		return bindBranch(jdbcClient.sql(sql).param("organizationId", organizationId).param("barcode", barcode), branchId)
				.query(CatalogRowMappers.productSummary()).optional();
	}

	private Optional<ProductSummaryView> find(UUID organizationId, UUID branchId, UUID productId, boolean includeArchived) {
		String archived = includeArchived ? "" : " AND cp.archived_at IS NULL";
		String sql = baseSelect() + " WHERE cp.organization_id=:organizationId" + availability(branchId)
				+ " AND cp.id=:productId AND cp.deleted_at IS NULL" + archived;
		return bindBranch(jdbcClient.sql(sql).param("organizationId", organizationId).param("productId", productId), branchId)
				.query(CatalogRowMappers.productSummary()).optional();
	}

	ProductPageView search(UUID organizationId, ProductSearchCriteria criteria) {
		return search(organizationId, null, criteria);
	}

	ProductPageView search(UUID organizationId, UUID branchId, ProductSearchCriteria criteria) {
		String filters = filters(criteria, branchId);
		String sql = baseSelect() + " WHERE cp.organization_id=:organizationId " + filters
				+ " ORDER BY " + orderBy(criteria.sort()) + " LIMIT :size OFFSET :offset";
		JdbcClient.StatementSpec itemSpec = bind(jdbcClient.sql(sql), organizationId, branchId, criteria)
				.param("size", criteria.size()).param("offset", criteria.page() * criteria.size());
		List<ProductSummaryView> items = itemSpec.query(CatalogRowMappers.productSummary()).list();
		long total = bind(jdbcClient.sql("SELECT COUNT(*) FROM catalog_product cp WHERE cp.organization_id=:organizationId " + filters),
				organizationId, branchId, criteria).query(Long.class).single();
		return ProductPageView.of(items, criteria.page(), criteria.size(), total);
	}

	private String baseSelect() { return "SELECT " + PRODUCT_COLUMNS + " FROM catalog_product cp " + CATEGORY_JOIN + TAG_JOIN; }

	private String availability(UUID branchId) {
		if (branchId == null) return "";
		return " AND EXISTS (SELECT 1 FROM catalog_product_branch cpb WHERE cpb.organization_id=cp.organization_id"
				+ " AND cpb.product_id=cp.id AND cpb.branch_id=:branchId)";
	}

	private String filters(ProductSearchCriteria criteria, UUID branchId) {
		StringBuilder value = new StringBuilder(" AND cp.deleted_at IS NULL");
		value.append(availability(branchId));
		if (!criteria.includeArchived()) value.append(" AND cp.archived_at IS NULL");
		if (hasText(criteria.query())) value.append(" AND (LOWER(cp.name) LIKE :query OR LOWER(cp.sku) LIKE :query OR LOWER(COALESCE(cp.barcode,'')) LIKE :query OR EXISTS (SELECT 1 FROM catalog_product_tag spt JOIN catalog_tag st ON st.id=spt.tag_id WHERE spt.organization_id=cp.organization_id AND spt.product_id=cp.id AND st.normalized_name LIKE :query))");
		if (hasText(criteria.productType())) value.append(" AND cp.product_type=:productType"); else value.append(" AND cp.product_type <> 'MEMBERSHIP_PLAN'");
		if (hasText(criteria.status())) value.append(" AND cp.status=:status");
		if (criteria.inventoryTracked() != null) value.append(" AND cp.inventory_tracked=:inventoryTracked");
		if (criteria.categoryId() != null) value.append(" AND EXISTS (SELECT 1 FROM catalog_product_category pc WHERE pc.organization_id=:organizationId AND pc.product_id=cp.id AND pc.category_id=:categoryId)");
		return value.toString();
	}

	private JdbcClient.StatementSpec bind(JdbcClient.StatementSpec spec, UUID organizationId, UUID branchId, ProductSearchCriteria criteria) {
		spec = bindBranch(spec.param("organizationId", organizationId), branchId);
		if (hasText(criteria.query())) spec = spec.param("query", "%" + criteria.query().trim().toLowerCase() + "%");
		if (hasText(criteria.productType())) spec = spec.param("productType", criteria.productType().trim().toUpperCase());
		if (hasText(criteria.status())) spec = spec.param("status", criteria.status().trim().toUpperCase());
		if (criteria.inventoryTracked() != null) spec = spec.param("inventoryTracked", criteria.inventoryTracked());
		if (criteria.categoryId() != null) spec = spec.param("categoryId", criteria.categoryId());
		return spec;
	}

	private JdbcClient.StatementSpec bindBranch(JdbcClient.StatementSpec spec, UUID branchId) {
		return branchId == null ? spec : spec.param("branchId", branchId);
	}

	private String orderBy(String sort) {
		return switch (sort) {
			case "name,asc" -> "cp.name ASC, cp.id";
			case "name,desc" -> "cp.name DESC, cp.id";
			case "createdAt,asc" -> "cp.created_at ASC, cp.id";
			case "createdAt,desc" -> "cp.created_at DESC, cp.id";
			case "price,asc" -> "cp.price_cents ASC, cp.id";
			case "price,desc" -> "cp.price_cents DESC, cp.id";
			case "displayOrder,desc" -> "cp.display_order DESC, cp.name, cp.id";
			default -> "cp.display_order ASC, cp.name, cp.id";
		};
	}

	private boolean hasText(String value) { return value != null && !value.isBlank(); }
}
