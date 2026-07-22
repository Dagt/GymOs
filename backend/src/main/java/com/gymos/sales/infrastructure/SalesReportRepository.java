package com.gymos.sales.infrastructure;

import com.gymos.sales.model.PaymentMethodBreakdown;
import com.gymos.sales.model.PaymentReportTotals;
import com.gymos.sales.model.SaleStatusBreakdown;
import com.gymos.sales.model.SalesReportTotals;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class SalesReportRepository {
	private final JdbcClient jdbcClient;

	public SalesReportRepository(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	public SalesReportTotals salesTotals(UUID organizationId, UUID branchId, OffsetDateTime from, OffsetDateTime toExclusive) {
		return jdbcClient.sql("""
				SELECT COALESCE(MAX(s.currency), 'MXN') AS currency,
				       COUNT(*) AS sale_count,
				       COALESCE(SUM(s.subtotal_cents), 0) AS subtotal_cents,
				       COALESCE(SUM(s.discount_cents), 0) AS discount_cents,
				       COALESCE(SUM(s.total_cents), 0) AS total_cents,
				       COALESCE(SUM(s.paid_cents), 0) AS paid_cents,
				       COALESCE(SUM(s.balance_cents), 0) AS balance_cents,
				       COALESCE(SUM(CASE WHEN s.status = 'PAID' THEN 1 ELSE 0 END), 0) AS paid_sales,
				       COALESCE(SUM(CASE WHEN s.status = 'PARTIALLY_PAID' THEN 1 ELSE 0 END), 0) AS partially_paid_sales,
				       COALESCE(SUM(CASE WHEN s.status = 'PENDING_PAYMENT' THEN 1 ELSE 0 END), 0) AS pending_sales
				FROM sale s
				WHERE s.organization_id = :organizationId
				  AND s.branch_id = :branchId
				  AND s.sold_at >= :from
				  AND s.sold_at < :toExclusive
				  AND s.deleted_at IS NULL
				""")
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("from", from)
				.param("toExclusive", toExclusive)
				.query((resultSet, rowNumber) -> new SalesReportTotals(
						resultSet.getString("currency"),
						resultSet.getLong("sale_count"),
						resultSet.getLong("subtotal_cents"),
						resultSet.getLong("discount_cents"),
						resultSet.getLong("total_cents"),
						resultSet.getLong("paid_cents"),
						resultSet.getLong("balance_cents"),
						resultSet.getLong("paid_sales"),
						resultSet.getLong("partially_paid_sales"),
						resultSet.getLong("pending_sales")
				))
				.single();
	}

	public PaymentReportTotals paymentTotals(UUID organizationId, UUID branchId, OffsetDateTime from, OffsetDateTime toExclusive) {
		return jdbcClient.sql("""
				SELECT COUNT(*) AS payment_count,
				       COALESCE(SUM(CASE WHEN p.status = 'COMPLETED' THEN 1 ELSE 0 END), 0) AS completed_payments,
				       COALESCE(SUM(CASE WHEN p.status = 'PENDING_VALIDATION' THEN 1 ELSE 0 END), 0) AS pending_payments,
				       COALESCE(SUM(CASE WHEN p.status = 'REJECTED' THEN 1 ELSE 0 END), 0) AS rejected_payments,
				       COALESCE(SUM(CASE WHEN p.status = 'COMPLETED' THEN p.amount_cents ELSE 0 END), 0) AS completed_cents,
				       COALESCE(SUM(CASE WHEN p.status = 'PENDING_VALIDATION' THEN p.amount_cents ELSE 0 END), 0) AS pending_cents,
				       COALESCE(SUM(CASE WHEN p.status = 'REJECTED' THEN p.amount_cents ELSE 0 END), 0) AS rejected_cents
				FROM payment p
				WHERE p.organization_id = :organizationId
				  AND p.branch_id = :branchId
				  AND p.received_at >= :from
				  AND p.received_at < :toExclusive
				  AND p.deleted_at IS NULL
				""")
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("from", from)
				.param("toExclusive", toExclusive)
				.query((resultSet, rowNumber) -> new PaymentReportTotals(
						resultSet.getLong("payment_count"),
						resultSet.getLong("completed_payments"),
						resultSet.getLong("pending_payments"),
						resultSet.getLong("rejected_payments"),
						resultSet.getLong("completed_cents"),
						resultSet.getLong("pending_cents"),
						resultSet.getLong("rejected_cents")
				))
				.single();
	}

	public List<SaleStatusBreakdown> salesByStatus(UUID organizationId, UUID branchId, OffsetDateTime from, OffsetDateTime toExclusive) {
		return jdbcClient.sql("""
				SELECT s.status, COUNT(*) AS sale_count,
				       COALESCE(SUM(s.total_cents), 0) AS total_cents,
				       COALESCE(SUM(s.paid_cents), 0) AS paid_cents,
				       COALESCE(SUM(s.balance_cents), 0) AS balance_cents
				FROM sale s
				WHERE s.organization_id = :organizationId
				  AND s.branch_id = :branchId
				  AND s.sold_at >= :from
				  AND s.sold_at < :toExclusive
				  AND s.deleted_at IS NULL
				GROUP BY s.status
				ORDER BY s.status
				""")
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("from", from)
				.param("toExclusive", toExclusive)
				.query((resultSet, rowNumber) -> new SaleStatusBreakdown(
						resultSet.getString("status"),
						resultSet.getLong("sale_count"),
						resultSet.getLong("total_cents"),
						resultSet.getLong("paid_cents"),
						resultSet.getLong("balance_cents")
				))
				.list();
	}

	public List<PaymentMethodBreakdown> paymentsByMethod(UUID organizationId, UUID branchId, OffsetDateTime from, OffsetDateTime toExclusive) {
		return jdbcClient.sql("""
				SELECT p.method, COUNT(*) AS payment_count,
				       COALESCE(SUM(p.amount_cents), 0) AS total_cents,
				       COALESCE(SUM(CASE WHEN p.status = 'COMPLETED' THEN p.amount_cents ELSE 0 END), 0) AS completed_cents,
				       COALESCE(SUM(CASE WHEN p.status = 'PENDING_VALIDATION' THEN p.amount_cents ELSE 0 END), 0) AS pending_cents,
				       COALESCE(SUM(CASE WHEN p.status = 'REJECTED' THEN p.amount_cents ELSE 0 END), 0) AS rejected_cents
				FROM payment p
				WHERE p.organization_id = :organizationId
				  AND p.branch_id = :branchId
				  AND p.received_at >= :from
				  AND p.received_at < :toExclusive
				  AND p.deleted_at IS NULL
				GROUP BY p.method
				ORDER BY p.method
				""")
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("from", from)
				.param("toExclusive", toExclusive)
				.query((resultSet, rowNumber) -> new PaymentMethodBreakdown(
						resultSet.getString("method"),
						resultSet.getLong("payment_count"),
						resultSet.getLong("total_cents"),
						resultSet.getLong("completed_cents"),
						resultSet.getLong("pending_cents"),
						resultSet.getLong("rejected_cents")
				))
				.list();
	}
}
