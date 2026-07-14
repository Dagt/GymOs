package com.gymos.sales.infrastructure;

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
