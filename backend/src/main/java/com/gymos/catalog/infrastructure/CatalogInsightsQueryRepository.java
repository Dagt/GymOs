package com.gymos.catalog.infrastructure;

import com.gymos.catalog.application.port.CatalogInsightsQueryPort;
import com.gymos.catalog.application.query.CatalogSummaryView;
import com.gymos.catalog.application.query.ProductDependencyView;
import com.gymos.catalog.application.query.ProductHistoryEntryView;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class CatalogInsightsQueryRepository implements CatalogInsightsQueryPort {
	private final JdbcClient jdbcClient;

	CatalogInsightsQueryRepository(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	@Override
	public CatalogSummaryView summary(UUID organizationId, UUID branchId) {
		return jdbcClient.sql("""
				SELECT
				  COUNT(*) FILTER (WHERE product_type='PHYSICAL' AND status='ACTIVE' AND archived_at IS NULL) AS active_products,
				  COUNT(*) FILTER (WHERE product_type='SERVICE' AND status='ACTIVE' AND archived_at IS NULL) AS active_services,
				  COUNT(*) FILTER (WHERE product_type='MEMBERSHIP_PLAN' AND status='ACTIVE' AND archived_at IS NULL) AS active_plans,
				  COUNT(*) FILTER (WHERE product_type<>'MEMBERSHIP_PLAN' AND status='INACTIVE' AND archived_at IS NULL) AS inactive_products,
				  COUNT(*) FILTER (WHERE product_type<>'MEMBERSHIP_PLAN' AND archived_at IS NOT NULL) AS archived_products,
				  COUNT(*) FILTER (WHERE product_type='PHYSICAL' AND inventory_tracked=TRUE AND archived_at IS NULL) AS tracked_products,
				  COUNT(*) FILTER (WHERE product_type<>'MEMBERSHIP_PLAN' AND archived_at IS NULL AND NOT EXISTS (
				      SELECT 1 FROM catalog_product_category pc WHERE pc.organization_id=cp.organization_id AND pc.product_id=cp.id
				  )) AS uncategorized_products,
				  COUNT(*) FILTER (WHERE product_type<>'MEMBERSHIP_PLAN' AND archived_at IS NULL AND NOT EXISTS (
				      SELECT 1 FROM catalog_product_barcode barcode
				      WHERE barcode.organization_id=cp.organization_id AND barcode.product_id=cp.id AND barcode.active=TRUE
				  )) AS without_barcode,
				  COUNT(*) FILTER (WHERE product_type<>'MEMBERSHIP_PLAN' AND archived_at IS NULL AND image_object_key IS NULL) AS without_image,
				  (SELECT COUNT(*) FROM inventory_stock s
				    JOIN catalog_product p ON p.id=s.product_id AND p.organization_id=s.organization_id
				    WHERE s.organization_id=:organizationId AND (:branchId IS NULL OR s.branch_id=:branchId)
				      AND s.deleted_at IS NULL AND p.deleted_at IS NULL AND p.archived_at IS NULL
				      AND p.inventory_tracked=TRUE AND s.reorder_point>0 AND s.quantity_on_hand<=s.reorder_point) AS low_stock
				FROM catalog_product cp
				WHERE cp.organization_id=:organizationId AND cp.deleted_at IS NULL
				  AND (:branchId IS NULL OR EXISTS (
				      SELECT 1 FROM catalog_product_branch cpb
				      WHERE cpb.organization_id=cp.organization_id
				        AND cpb.product_id=cp.id
				        AND cpb.branch_id=:branchId
				  ))
				""")
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.query((rs, row) -> new CatalogSummaryView(
						rs.getLong("active_products"), rs.getLong("active_services"), rs.getLong("active_plans"),
						rs.getLong("inactive_products"), rs.getLong("archived_products"), rs.getLong("tracked_products"),
						rs.getLong("uncategorized_products"), rs.getLong("without_barcode"), rs.getLong("without_image"),
						rs.getLong("low_stock")
				))
				.single();
	}

	@Override
	public ProductDependencyView dependencies(UUID organizationId, UUID productId) {
		return jdbcClient.sql("""
				SELECT
				 (SELECT COUNT(*) FROM sale_item WHERE organization_id=:organizationId AND product_id=:productId) AS sale_items,
				 (SELECT COUNT(*) FROM inventory_movement WHERE organization_id=:organizationId AND product_id=:productId) AS movements,
				 (SELECT COUNT(*) FROM inventory_stock WHERE organization_id=:organizationId AND product_id=:productId
				    AND deleted_at IS NULL AND quantity_on_hand<>0) AS branches_with_stock,
				 (SELECT COUNT(*) FROM membership_plan WHERE organization_id=:organizationId AND product_id=:productId
				    AND deleted_at IS NULL AND status='ACTIVE') AS active_plans,
				 (SELECT COUNT(*) FROM catalog_product_category pc
				    JOIN catalog_product p ON p.id=pc.product_id AND p.organization_id=pc.organization_id
				    WHERE pc.organization_id=:organizationId AND pc.category_id IN (
				      SELECT category_id FROM catalog_product_category WHERE organization_id=:organizationId AND product_id=:productId
				    ) AND p.id<>:productId AND p.deleted_at IS NULL AND p.archived_at IS NULL AND p.status='ACTIVE') AS category_products
				""")
				.param("organizationId", organizationId)
				.param("productId", productId)
				.query((rs, row) -> new ProductDependencyView(
						rs.getLong("sale_items"), rs.getLong("movements"), rs.getLong("branches_with_stock"),
						rs.getLong("active_plans"), rs.getLong("category_products")
				))
				.single();
	}

	@Override
	public List<ProductHistoryEntryView> history(UUID organizationId, UUID productId, int limit) {
		return jdbcClient.sql("""
				SELECT id, action, actor_user_id, actor_role, reason, origin, correlation_id, occurred_at
				FROM audit_log
				WHERE organization_id=:organizationId AND entity_id=:productId
				  AND entity_type IN ('CATALOG_PRODUCT','PRODUCT')
				ORDER BY occurred_at DESC
				LIMIT :limit
				""")
				.param("organizationId", organizationId)
				.param("productId", productId)
				.param("limit", Math.max(1, Math.min(limit, 200)))
				.query((rs, row) -> new ProductHistoryEntryView(
						rs.getObject("id", UUID.class), rs.getString("action"), rs.getObject("actor_user_id", UUID.class),
						rs.getString("actor_role"), rs.getString("reason"), rs.getString("origin"),
						rs.getObject("correlation_id", UUID.class), rs.getObject("occurred_at", OffsetDateTime.class)
				))
				.list();
	}
}
