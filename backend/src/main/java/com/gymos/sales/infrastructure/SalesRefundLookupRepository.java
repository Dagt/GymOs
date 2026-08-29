package com.gymos.sales.infrastructure;

import com.gymos.sales.model.RefundDetail;
import com.gymos.sales.model.RefundItemSnapshot;
import com.gymos.sales.model.RefundableSaleItem;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class SalesRefundLookupRepository {
	private final JdbcClient jdbcClient;

	SalesRefundLookupRepository(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	Optional<RefundDetail> byIdempotencyKey(UUID organizationId, String idempotencyKey) {
		return jdbcClient.sql("""
				SELECT id, sale_id, payment_id, status, amount_cents, currency, reason,
				       idempotency_key, requested_by, requested_at, processed_at, created_at, updated_at
				FROM sale_refund
				WHERE organization_id = :organizationId
				  AND idempotency_key = :idempotencyKey
				""")
				.param("organizationId", organizationId)
				.param("idempotencyKey", idempotencyKey)
				.query(SalesRowMappers::refundDetail)
				.optional();
	}

	long processedTotal(UUID organizationId, UUID saleId) {
		Long total = jdbcClient.sql("""
				SELECT COALESCE(SUM(amount_cents), 0)
				FROM sale_refund
				WHERE organization_id = :organizationId
				  AND sale_id = :saleId
				  AND status = 'PROCESSED'
				""")
				.param("organizationId", organizationId)
				.param("saleId", saleId)
				.query(Long.class)
				.single();
		return total == null ? 0 : total;
	}

	boolean visitPackageHasConsumptions(UUID organizationId, UUID saleId) {
		return jdbcClient.sql("""
				SELECT EXISTS (
				    SELECT 1
				    FROM visit_package_entitlement entitlement
				    JOIN visit_package_consumption consumption
				      ON consumption.organization_id=entitlement.organization_id
				     AND consumption.entitlement_id=entitlement.id
				    WHERE entitlement.organization_id=:organizationId
				      AND entitlement.sale_id=:saleId
				      AND entitlement.deleted_at IS NULL
				      AND consumption.status='CONSUMED'
				)
				""")
				.param("organizationId", organizationId).param("saleId", saleId)
				.query(Boolean.class).single();
	}

	List<RefundableSaleItem> refundableItems(UUID organizationId, UUID saleId) {
		return jdbcClient.sql("""
				SELECT si.id AS sale_item_id, si.product_id, si.item_type, si.name,
				       si.quantity AS sold_quantity,
				       COALESCE(refunded.refunded_quantity, 0) AS refunded_quantity
				FROM sale_item si
				LEFT JOIN (
				    SELECT sri.sale_item_id, SUM(sri.quantity)::int AS refunded_quantity
				    FROM sale_refund_item sri
				    JOIN sale_refund sr ON sr.id = sri.refund_id
				      AND sr.organization_id = sri.organization_id
				    WHERE sri.organization_id = :organizationId
				      AND sri.sale_id = :saleId
				      AND sr.status = 'PROCESSED'
				    GROUP BY sri.sale_item_id
				) refunded ON refunded.sale_item_id = si.id
				WHERE si.organization_id = :organizationId
				  AND si.sale_id = :saleId
				ORDER BY si.created_at, si.id
				""")
				.param("organizationId", organizationId)
				.param("saleId", saleId)
				.query((resultSet, rowNumber) -> new RefundableSaleItem(
						resultSet.getObject("sale_item_id", UUID.class),
						resultSet.getObject("product_id", UUID.class),
						resultSet.getString("item_type"),
						resultSet.getString("name"),
						resultSet.getInt("sold_quantity"),
						resultSet.getInt("refunded_quantity")
				))
				.list();
	}

	List<RefundItemSnapshot> itemsByRefundId(UUID organizationId, UUID refundId) {
		return jdbcClient.sql("""
				SELECT sale_item_id, quantity, restock
				FROM sale_refund_item
				WHERE organization_id = :organizationId
				  AND refund_id = :refundId
				ORDER BY sale_item_id
				""")
				.param("organizationId", organizationId)
				.param("refundId", refundId)
				.query((resultSet, rowNumber) -> new RefundItemSnapshot(
						resultSet.getObject("sale_item_id", UUID.class),
						resultSet.getInt("quantity"),
						resultSet.getBoolean("restock")
				))
				.list();
	}

	boolean hasItemizedRefunds(UUID organizationId, UUID saleId) {
		return count("""
				SELECT COUNT(*)
				FROM sale_refund sr
				WHERE sr.organization_id = :organizationId
				  AND sr.sale_id = :saleId
				  AND sr.status = 'PROCESSED'
				  AND EXISTS (
				      SELECT 1 FROM sale_refund_item sri
				      WHERE sri.organization_id = sr.organization_id AND sri.refund_id = sr.id
				  )
				""", organizationId, saleId) > 0;
	}

	boolean hasAmountOnlyRefunds(UUID organizationId, UUID saleId) {
		return count("""
				SELECT COUNT(*)
				FROM sale_refund sr
				WHERE sr.organization_id = :organizationId
				  AND sr.sale_id = :saleId
				  AND sr.status = 'PROCESSED'
				  AND NOT EXISTS (
				      SELECT 1 FROM sale_refund_item sri
				      WHERE sri.organization_id = sr.organization_id AND sri.refund_id = sr.id
				  )
				""", organizationId, saleId) > 0;
	}

	private int count(String sql, UUID organizationId, UUID saleId) {
		return jdbcClient.sql(sql)
				.param("organizationId", organizationId)
				.param("saleId", saleId)
				.query(Integer.class)
				.single();
	}
}
