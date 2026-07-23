package com.gymos.sales.infrastructure;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class SalesPaymentCommandRepository {
	private final JdbcClient jdbcClient;

	public SalesPaymentCommandRepository(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	public Optional<UUID> saleIdByIdempotencyKey(UUID organizationId, String idempotencyKey) {
		return jdbcClient.sql("""
				SELECT sale_id
				FROM payment
				WHERE organization_id = :organizationId
				  AND idempotency_key = :idempotencyKey
				  AND deleted_at IS NULL
				""")
				.param("organizationId", organizationId)
				.param("idempotencyKey", idempotencyKey)
				.query(UUID.class)
				.optional();
	}

	public void create(PaymentRecord payment) {
		jdbcClient.sql("""
				INSERT INTO payment (
				    id, organization_id, branch_id, sale_id, client_id, method, status,
				    amount_cents, currency, reference, receipt_object_key, received_at,
				    validated_at, validated_by, notes, created_at, updated_at, created_by, updated_by, version,
				    idempotency_key, idempotency_request_hash
				)
				VALUES (
				    :id, :organizationId, :branchId, :saleId, :clientId, :method, :status,
				    :amountCents, :currency, :reference, :receiptObjectKey, :receivedAt,
				    :validatedAt, :validatedBy, :notes, :receivedAt, :receivedAt, :actorUserId, :actorUserId, 0,
				    :idempotencyKey, :idempotencyRequestHash
				)
				""")
				.param("id", payment.id())
				.param("organizationId", payment.organizationId())
				.param("branchId", payment.branchId())
				.param("saleId", payment.saleId())
				.param("clientId", payment.clientId())
				.param("method", payment.method())
				.param("status", payment.status())
				.param("amountCents", payment.amountCents())
				.param("currency", payment.currency())
				.param("reference", payment.reference())
				.param("receiptObjectKey", payment.receiptObjectKey())
				.param("receivedAt", payment.receivedAt())
				.param("validatedAt", payment.validatedAt())
				.param("validatedBy", payment.validatedBy())
				.param("notes", payment.notes())
				.param("actorUserId", payment.actorUserId())
				.param("idempotencyKey", payment.idempotencyKey())
				.param("idempotencyRequestHash", payment.idempotencyRequestHash())
				.update();
	}

	public void attachReceipt(UUID organizationId, UUID saleId, UUID paymentId, String objectKey, UUID actorUserId, OffsetDateTime updatedAt) {
		jdbcClient.sql("""
				UPDATE payment
				SET receipt_object_key = :receiptObjectKey,
				    updated_at = :updatedAt,
				    updated_by = :updatedBy,
				    version = version + 1
				WHERE organization_id = :organizationId
				  AND id = :paymentId
				  AND sale_id = :saleId
				  AND deleted_at IS NULL
				""")
				.param("receiptObjectKey", objectKey)
				.param("updatedAt", updatedAt)
				.param("updatedBy", actorUserId)
				.param("organizationId", organizationId)
				.param("paymentId", paymentId)
				.param("saleId", saleId)
				.update();
	}

	public void complete(UUID organizationId, UUID paymentId, UUID actorUserId, OffsetDateTime completedAt) {
		jdbcClient.sql("""
				UPDATE payment
				SET status = 'COMPLETED',
				    validated_at = :validatedAt,
				    validated_by = :validatedBy,
				    updated_at = :updatedAt,
				    updated_by = :updatedBy,
				    version = version + 1
				WHERE organization_id = :organizationId
				  AND id = :paymentId
				  AND deleted_at IS NULL
				""")
				.param("validatedAt", completedAt)
				.param("validatedBy", actorUserId)
				.param("updatedAt", completedAt)
				.param("updatedBy", actorUserId)
				.param("organizationId", organizationId)
				.param("paymentId", paymentId)
				.update();
	}

	public void reject(UUID organizationId, UUID paymentId, String reason, UUID actorUserId, OffsetDateTime rejectedAt) {
		jdbcClient.sql("""
				UPDATE payment
				SET status = 'REJECTED',
				    notes = :notes,
				    updated_at = :updatedAt,
				    updated_by = :updatedBy,
				    version = version + 1
				WHERE organization_id = :organizationId
				  AND id = :paymentId
				  AND deleted_at IS NULL
				""")
				.param("notes", reason)
				.param("updatedAt", rejectedAt)
				.param("updatedBy", actorUserId)
				.param("organizationId", organizationId)
				.param("paymentId", paymentId)
				.update();
	}

	public record PaymentRecord(
			UUID id,
			UUID organizationId,
			UUID branchId,
			UUID saleId,
			UUID clientId,
			String method,
			String status,
			long amountCents,
			String currency,
			String reference,
			String receiptObjectKey,
			OffsetDateTime receivedAt,
			OffsetDateTime validatedAt,
			UUID validatedBy,
			String notes,
			UUID actorUserId,
			String idempotencyKey,
			String idempotencyRequestHash
	) {
	}
}
