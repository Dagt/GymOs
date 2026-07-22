package com.gymos.sales.infrastructure;

import com.gymos.sales.model.MembershipDetail;
import com.gymos.sales.model.PaymentDetail;
import com.gymos.sales.model.RefundDetail;
import com.gymos.sales.model.SaleItemDetail;
import com.gymos.sales.model.SaleSummary;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class SalesReadRepository {
	private final JdbcClient jdbcClient;

	public SalesReadRepository(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
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

	public List<RefundDetail> refunds(UUID organizationId, UUID saleId) {
		return jdbcClient.sql("""
				SELECT id, sale_id, payment_id, status, amount_cents, currency, reason,
				       idempotency_key, requested_by, requested_at, processed_at, created_at, updated_at
				FROM sale_refund
				WHERE organization_id = :organizationId
				  AND sale_id = :saleId
				ORDER BY requested_at DESC
				""")
				.param("organizationId", organizationId)
				.param("saleId", saleId)
				.query((resultSet, rowNumber) -> new RefundDetail(
						resultSet.getObject("id", UUID.class),
						resultSet.getObject("sale_id", UUID.class),
						resultSet.getObject("payment_id", UUID.class),
						resultSet.getString("status"),
						resultSet.getLong("amount_cents"),
						resultSet.getString("currency"),
						resultSet.getString("reason"),
						resultSet.getString("idempotency_key"),
						resultSet.getObject("requested_by", UUID.class),
						resultSet.getObject("requested_at", OffsetDateTime.class),
						resultSet.getObject("processed_at", OffsetDateTime.class),
						resultSet.getObject("created_at", OffsetDateTime.class),
						resultSet.getObject("updated_at", OffsetDateTime.class)
				))
				.list();
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
}
