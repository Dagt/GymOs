package com.gymos.sales.infrastructure;

import java.time.OffsetDateTime;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class SalesRepository {
	private final JdbcClient jdbcClient;

	public SalesRepository(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	public Optional<ProductForSale> productForSale(UUID organizationId, UUID productId) {
		return jdbcClient.sql("""
				SELECT id, name, product_type, price_cents, currency
				FROM catalog_product
				WHERE organization_id = :organizationId
				  AND id = :productId
				  AND status = 'ACTIVE'
				  AND deleted_at IS NULL
				""")
				.param("organizationId", organizationId)
				.param("productId", productId)
				.query((resultSet, rowNumber) -> new ProductForSale(
						resultSet.getObject("id", UUID.class),
						resultSet.getString("name"),
						resultSet.getString("product_type"),
						resultSet.getLong("price_cents"),
						resultSet.getString("currency")
				))
				.optional();
	}

	public boolean clientExists(UUID organizationId, UUID clientId) {
		Integer count = jdbcClient.sql("""
				SELECT COUNT(*)
				FROM client
				WHERE organization_id = :organizationId
				  AND id = :clientId
				  AND deleted_at IS NULL
				""")
				.param("organizationId", organizationId)
				.param("clientId", clientId)
				.query(Integer.class)
				.single();
		return count > 0;
	}

	public List<SaleSummary> recentSales(UUID organizationId, UUID branchId) {
		return jdbcClient.sql("""
				SELECT s.id, s.sale_number, s.client_id,
				       COALESCE(p.first_name || ' ' || p.last_name, 'Venta mostrador') AS client_name,
				       s.status, s.total_cents, s.paid_cents, s.balance_cents,
				       s.currency, s.sold_at
				FROM sale s
				LEFT JOIN client c ON c.id = s.client_id
				LEFT JOIN person p ON p.id = c.person_id
				WHERE s.organization_id = :organizationId
				  AND s.branch_id = :branchId
				  AND s.deleted_at IS NULL
				ORDER BY s.sold_at DESC
				LIMIT 25
				""")
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.query((resultSet, rowNumber) -> new SaleSummary(
						resultSet.getObject("id", UUID.class),
						resultSet.getString("sale_number"),
						resultSet.getObject("client_id", UUID.class),
						resultSet.getString("client_name"),
						resultSet.getString("status"),
						resultSet.getLong("total_cents"),
						resultSet.getLong("paid_cents"),
						resultSet.getLong("balance_cents"),
						resultSet.getString("currency"),
						resultSet.getObject("sold_at", OffsetDateTime.class)
				))
				.list();
	}

	public SalesReportTotals salesReportTotals(
			UUID organizationId,
			UUID branchId,
			OffsetDateTime from,
			OffsetDateTime toExclusive
	) {
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

	public PaymentReportTotals paymentReportTotals(
			UUID organizationId,
			UUID branchId,
			OffsetDateTime from,
			OffsetDateTime toExclusive
	) {
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

	public List<SaleStatusBreakdown> salesByStatus(
			UUID organizationId,
			UUID branchId,
			OffsetDateTime from,
			OffsetDateTime toExclusive
	) {
		return jdbcClient.sql("""
				SELECT s.status,
				       COUNT(*) AS sale_count,
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

	public List<PaymentMethodBreakdown> paymentsByMethod(
			UUID organizationId,
			UUID branchId,
			OffsetDateTime from,
			OffsetDateTime toExclusive
	) {
		return jdbcClient.sql("""
				SELECT p.method,
				       COUNT(*) AS payment_count,
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

	public Optional<SaleHeader> saleHeader(UUID organizationId, UUID saleId) {
		return jdbcClient.sql("""
				SELECT s.id, s.sale_number, s.branch_id, s.client_id,
				       COALESCE(p.first_name || ' ' || p.last_name, 'Venta mostrador') AS client_name,
				       s.status, s.subtotal_cents, s.discount_cents, s.total_cents,
				       s.paid_cents, s.balance_cents, s.currency, s.notes, s.sold_at,
				       s.created_at, s.updated_at
				FROM sale s
				LEFT JOIN client c ON c.id = s.client_id
				LEFT JOIN person p ON p.id = c.person_id
				WHERE s.organization_id = :organizationId
				  AND s.id = :saleId
				  AND s.deleted_at IS NULL
				""")
				.param("organizationId", organizationId)
				.param("saleId", saleId)
				.query((resultSet, rowNumber) -> new SaleHeader(
						resultSet.getObject("id", UUID.class),
						resultSet.getString("sale_number"),
						resultSet.getObject("branch_id", UUID.class),
						resultSet.getObject("client_id", UUID.class),
						resultSet.getString("client_name"),
						resultSet.getString("status"),
						resultSet.getLong("subtotal_cents"),
						resultSet.getLong("discount_cents"),
						resultSet.getLong("total_cents"),
						resultSet.getLong("paid_cents"),
						resultSet.getLong("balance_cents"),
						resultSet.getString("currency"),
						resultSet.getString("notes"),
						resultSet.getObject("sold_at", OffsetDateTime.class),
						resultSet.getObject("created_at", OffsetDateTime.class),
						resultSet.getObject("updated_at", OffsetDateTime.class)
				))
				.optional();
	}

	public List<SaleItemDetail> saleItems(UUID organizationId, UUID saleId) {
		return jdbcClient.sql("""
				SELECT id, product_id, item_type, name, quantity, unit_price_cents,
				       discount_cents, total_cents, created_at
				FROM sale_item
				WHERE organization_id = :organizationId
				  AND sale_id = :saleId
				ORDER BY created_at, name
				""")
				.param("organizationId", organizationId)
				.param("saleId", saleId)
				.query((resultSet, rowNumber) -> new SaleItemDetail(
						resultSet.getObject("id", UUID.class),
						resultSet.getObject("product_id", UUID.class),
						resultSet.getString("item_type"),
						resultSet.getString("name"),
						resultSet.getInt("quantity"),
						resultSet.getLong("unit_price_cents"),
						resultSet.getLong("discount_cents"),
						resultSet.getLong("total_cents"),
						resultSet.getObject("created_at", OffsetDateTime.class)
				))
				.list();
	}

	public List<PaymentDetail> payments(UUID organizationId, UUID saleId) {
		return jdbcClient.sql("""
				SELECT id, method, status, amount_cents, currency, reference,
				       receipt_object_key, received_at, validated_at, notes
				FROM payment
				WHERE organization_id = :organizationId
				  AND sale_id = :saleId
				  AND deleted_at IS NULL
				ORDER BY received_at
				""")
				.param("organizationId", organizationId)
				.param("saleId", saleId)
				.query((resultSet, rowNumber) -> new PaymentDetail(
						resultSet.getObject("id", UUID.class),
						resultSet.getString("method"),
						resultSet.getString("status"),
						resultSet.getLong("amount_cents"),
						resultSet.getString("currency"),
						resultSet.getString("reference"),
						resultSet.getString("receipt_object_key"),
						resultSet.getObject("received_at", OffsetDateTime.class),
						resultSet.getObject("validated_at", OffsetDateTime.class),
						resultSet.getString("notes")
				))
				.list();
	}

	public Optional<PaymentDetail> payment(UUID organizationId, UUID paymentId) {
		return jdbcClient.sql("""
				SELECT id, method, status, amount_cents, currency, reference,
				       receipt_object_key, received_at, validated_at, notes
				FROM payment
				WHERE organization_id = :organizationId
				  AND id = :paymentId
				  AND deleted_at IS NULL
				""")
				.param("organizationId", organizationId)
				.param("paymentId", paymentId)
				.query((resultSet, rowNumber) -> new PaymentDetail(
						resultSet.getObject("id", UUID.class),
						resultSet.getString("method"),
						resultSet.getString("status"),
						resultSet.getLong("amount_cents"),
						resultSet.getString("currency"),
						resultSet.getString("reference"),
						resultSet.getString("receipt_object_key"),
						resultSet.getObject("received_at", OffsetDateTime.class),
						resultSet.getObject("validated_at", OffsetDateTime.class),
						resultSet.getString("notes")
				))
				.optional();
	}

	public List<MembershipPlanForSale> membershipPlansForSale(UUID organizationId, UUID saleId) {
		return jdbcClient.sql("""
				SELECT DISTINCT mp.id AS plan_id, si.product_id, mp.duration_days, mp.grace_days
				FROM sale_item si
				JOIN membership_plan mp ON mp.product_id = si.product_id
				WHERE si.organization_id = :organizationId
				  AND si.sale_id = :saleId
				  AND mp.status = 'ACTIVE'
				  AND mp.deleted_at IS NULL
				""")
				.param("organizationId", organizationId)
				.param("saleId", saleId)
				.query((resultSet, rowNumber) -> new MembershipPlanForSale(
						resultSet.getObject("plan_id", UUID.class),
						resultSet.getObject("product_id", UUID.class),
						resultSet.getInt("duration_days"),
						resultSet.getInt("grace_days")
				))
				.list();
	}

	public boolean membershipExistsForSalePlan(UUID organizationId, UUID saleId, UUID planId) {
		Integer count = jdbcClient.sql("""
				SELECT COUNT(*)
				FROM membership
				WHERE organization_id = :organizationId
				  AND sale_id = :saleId
				  AND plan_id = :planId
				  AND deleted_at IS NULL
				""")
				.param("organizationId", organizationId)
				.param("saleId", saleId)
				.param("planId", planId)
				.query(Integer.class)
				.single();
		return count > 0;
	}

	public Optional<LocalDate> latestMembershipEnd(UUID organizationId, UUID clientId, UUID planId) {
		return jdbcClient.sql("""
				SELECT MAX(ends_on)
				FROM membership
				WHERE organization_id = :organizationId
				  AND client_id = :clientId
				  AND plan_id = :planId
				  AND status = 'ACTIVE'
				  AND deleted_at IS NULL
				""")
				.param("organizationId", organizationId)
				.param("clientId", clientId)
				.param("planId", planId)
				.query(LocalDate.class)
				.optional();
	}

	public List<MembershipDetail> membershipsForSale(UUID organizationId, UUID saleId) {
		return jdbcClient.sql("""
				SELECT m.id, m.client_id, m.plan_id, cp.name AS plan_name, m.status,
				       m.activated_at, m.starts_on, m.ends_on, m.grace_ends_on
				FROM membership m
				JOIN membership_plan mp ON mp.id = m.plan_id
				JOIN catalog_product cp ON cp.id = mp.product_id
				WHERE m.organization_id = :organizationId
				  AND m.sale_id = :saleId
				  AND m.deleted_at IS NULL
				ORDER BY m.activated_at DESC
				""")
				.param("organizationId", organizationId)
				.param("saleId", saleId)
				.query((resultSet, rowNumber) -> new MembershipDetail(
						resultSet.getObject("id", UUID.class),
						resultSet.getObject("client_id", UUID.class),
						resultSet.getObject("plan_id", UUID.class),
						resultSet.getString("plan_name"),
						resultSet.getString("status"),
						resultSet.getObject("activated_at", OffsetDateTime.class),
						resultSet.getObject("starts_on", LocalDate.class),
						resultSet.getObject("ends_on", LocalDate.class),
						resultSet.getObject("grace_ends_on", LocalDate.class)
				))
				.list();
	}

	public Optional<UUID> saleIdForPayment(UUID organizationId, UUID paymentId) {
		return jdbcClient.sql("""
				SELECT sale_id
				FROM payment
				WHERE organization_id = :organizationId
				  AND id = :paymentId
				  AND deleted_at IS NULL
				""")
				.param("organizationId", organizationId)
				.param("paymentId", paymentId)
				.query(UUID.class)
				.optional();
	}

	public record MembershipPlanForSale(UUID planId, UUID productId, int durationDays, int graceDays) {
	}

	public record MembershipDetail(
			UUID id,
			UUID clientId,
			UUID planId,
			String planName,
			String status,
			OffsetDateTime activatedAt,
			LocalDate startsOn,
			LocalDate endsOn,
			LocalDate graceEndsOn
	) {
	}

	public record ProductForSale(UUID id, String name, String productType, long priceCents, String currency) {
	}

	public record SaleSummary(
			UUID id,
			String saleNumber,
			UUID clientId,
			String clientName,
			String status,
			long totalCents,
			long paidCents,
			long balanceCents,
			String currency,
			OffsetDateTime soldAt
	) {
	}

	public record SalesReportTotals(
			String currency,
			long saleCount,
			long subtotalCents,
			long discountCents,
			long totalCents,
			long paidCents,
			long balanceCents,
			long paidSales,
			long partiallyPaidSales,
			long pendingSales
	) {
	}

	public record PaymentReportTotals(
			long paymentCount,
			long completedPayments,
			long pendingPayments,
			long rejectedPayments,
			long completedCents,
			long pendingCents,
			long rejectedCents
	) {
	}

	public record SaleStatusBreakdown(
			String status,
			long saleCount,
			long totalCents,
			long paidCents,
			long balanceCents
	) {
	}

	public record PaymentMethodBreakdown(
			String method,
			long paymentCount,
			long totalCents,
			long completedCents,
			long pendingCents,
			long rejectedCents
	) {
	}

	public record SaleHeader(
			UUID id,
			String saleNumber,
			UUID branchId,
			UUID clientId,
			String clientName,
			String status,
			long subtotalCents,
			long discountCents,
			long totalCents,
			long paidCents,
			long balanceCents,
			String currency,
			String notes,
			OffsetDateTime soldAt,
			OffsetDateTime createdAt,
			OffsetDateTime updatedAt
	) {
	}

	public record SaleItemDetail(
			UUID id,
			UUID productId,
			String itemType,
			String name,
			int quantity,
			long unitPriceCents,
			long discountCents,
			long totalCents,
			OffsetDateTime createdAt
	) {
	}

	public record PaymentDetail(
			UUID id,
			String method,
			String status,
			long amountCents,
			String currency,
			String reference,
			String receiptObjectKey,
			OffsetDateTime receivedAt,
			OffsetDateTime validatedAt,
			String notes
	) {
	}
}
