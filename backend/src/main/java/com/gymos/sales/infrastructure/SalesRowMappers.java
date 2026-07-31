package com.gymos.sales.infrastructure;

import com.gymos.sales.model.PaymentDetail;
import com.gymos.sales.model.RefundDetail;
import com.gymos.sales.model.SaleHeader;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.UUID;

final class SalesRowMappers {
	private SalesRowMappers() {
	}

	static SaleHeader saleHeader(ResultSet resultSet, int rowNumber) throws SQLException {
		return new SaleHeader(
				resultSet.getObject("id", UUID.class),
				resultSet.getString("sale_number"),
				resultSet.getObject("branch_id", UUID.class),
				resultSet.getObject("client_id", UUID.class),
				resultSet.getString("client_name"),
				resultSet.getString("status"),
				resultSet.getString("business_context"),
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

	static PaymentDetail paymentDetail(ResultSet resultSet, int rowNumber) throws SQLException {
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

	static RefundDetail refundDetail(ResultSet resultSet, int rowNumber) throws SQLException {
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
