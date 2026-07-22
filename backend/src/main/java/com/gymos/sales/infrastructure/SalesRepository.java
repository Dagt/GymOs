package com.gymos.sales.infrastructure;

import com.gymos.sales.model.MembershipPlanForSale;
import com.gymos.sales.model.PaymentDetail;
import com.gymos.sales.model.ProductForSale;
import com.gymos.sales.model.RefundDetail;
import com.gymos.sales.model.SaleHeader;
import java.time.LocalDate;
import java.time.OffsetDateTime;
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
				.query((resultSet, rowNumber) -> saleHeader(resultSet))
				.optional();
	}

	public Optional<SaleHeader> saleHeaderByIdempotencyKey(UUID organizationId, String idempotencyKey) {
		return jdbcClient.sql("""
				SELECT s.id, s.sale_number, s.branch_id, s.client_id,
				       COALESCE(p.first_name || ' ' || p.last_name, 'Cliente general') AS client_name,
				       s.status, s.subtotal_cents, s.discount_cents, s.total_cents,
				       s.paid_cents, s.balance_cents, s.currency, s.notes, s.sold_at,
				       s.created_at, s.updated_at
				FROM sale s
				LEFT JOIN client c ON c.id = s.client_id
				LEFT JOIN person p ON p.id = c.person_id
				WHERE s.organization_id = :organizationId
				  AND s.idempotency_key = :idempotencyKey
				  AND s.deleted_at IS NULL
				""")
				.param("organizationId", organizationId)
				.param("idempotencyKey", idempotencyKey)
				.query((resultSet, rowNumber) -> saleHeader(resultSet))
				.optional();
	}

	private SaleHeader saleHeader(java.sql.ResultSet resultSet) throws java.sql.SQLException {
		return new SaleHeader(
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
		);
	}

	public Optional<String> saleRequestHashByIdempotencyKey(UUID organizationId, String idempotencyKey) {
		return idempotencyHash("sale", organizationId, idempotencyKey);
	}

	public Optional<String> paymentRequestHashByIdempotencyKey(UUID organizationId, String idempotencyKey) {
		return idempotencyHash("payment", organizationId, idempotencyKey);
	}

	private Optional<String> idempotencyHash(String table, UUID organizationId, String idempotencyKey) {
		return jdbcClient.sql("SELECT idempotency_request_hash FROM " + table + " WHERE organization_id = :organizationId AND idempotency_key = :idempotencyKey AND deleted_at IS NULL")
				.param("organizationId", organizationId)
				.param("idempotencyKey", idempotencyKey)
				.query(String.class)
				.optional();
	}

	public Optional<RefundDetail> refundByIdempotencyKey(UUID organizationId, String idempotencyKey) {
		return jdbcClient.sql("""
				SELECT id, sale_id, payment_id, status, amount_cents, currency, reason,
				       idempotency_key, requested_by, requested_at, processed_at, created_at, updated_at
				FROM sale_refund
				WHERE organization_id = :organizationId
				  AND idempotency_key = :idempotencyKey
				""")
				.param("organizationId", organizationId)
				.param("idempotencyKey", idempotencyKey)
				.query((resultSet, rowNumber) -> refundDetail(resultSet))
				.optional();
	}

	public long processedRefundTotal(UUID organizationId, UUID saleId) {
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
				.query((resultSet, rowNumber) -> paymentDetail(resultSet))
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

	private PaymentDetail paymentDetail(java.sql.ResultSet resultSet) throws java.sql.SQLException {
		return new PaymentDetail(
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
		);
	}

	private RefundDetail refundDetail(java.sql.ResultSet resultSet) throws java.sql.SQLException {
		return new RefundDetail(
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
		);
	}
}
