package com.gymos.sales.infrastructure;

import com.gymos.sales.api.operations.MembershipBenefitSnapshot;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class MembershipSaleCommandRepository {
	private final JdbcClient jdbcClient;

	public MembershipSaleCommandRepository(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	public boolean create(MembershipSaleRecord record) {
		int inserted = jdbcClient.sql("""
				INSERT INTO sale (
				    id, organization_id, branch_id, client_id, sale_number, status,
				    subtotal_cents, discount_cents, total_cents, paid_cents, balance_cents,
				    currency, notes, sold_at, created_at, updated_at, created_by, updated_by, version,
				    idempotency_key, idempotency_request_hash, business_context, context_membership_id, context_target_plan_id
				)
				VALUES (
				    :id, :organizationId, :branchId, :clientId, :saleNumber, :status,
				    :listPriceCents, :discountCents, :totalCents, :paidCents, :balanceCents,
				    :currency, :notes, :occurredAt, :occurredAt, :occurredAt, :actorUserId, :actorUserId, 0,
				    :idempotencyKey, :requestHash, :businessContext, :sourceMembershipId, :targetPlanId
				)
				ON CONFLICT (organization_id, idempotency_key)
				    WHERE idempotency_key IS NOT NULL AND deleted_at IS NULL
				DO NOTHING
				""")
				.param("id", record.saleId())
				.param("organizationId", record.organizationId())
				.param("branchId", record.branchId())
				.param("clientId", record.clientId())
				.param("saleNumber", record.saleNumber())
				.param("status", record.saleStatus())
				.param("listPriceCents", record.listPriceCents())
				.param("discountCents", record.discountCents())
				.param("totalCents", record.totalCents())
				.param("paidCents", record.paidCents())
				.param("balanceCents", record.balanceCents())
				.param("currency", record.currency())
				.param("notes", record.notes())
				.param("occurredAt", record.occurredAt())
				.param("actorUserId", record.actorUserId())
				.param("idempotencyKey", record.idempotencyKey())
				.param("requestHash", record.requestHash())
				.param("businessContext", record.businessContext())
				.param("sourceMembershipId", record.sourceMembershipId())
				.param("targetPlanId", record.targetPlanId())
				.update();
		if (inserted == 0) {
			return false;
		}

		jdbcClient.sql("""
				INSERT INTO sale_item (
				    id, sale_id, organization_id, product_id, item_type, name, quantity,
				    unit_price_cents, discount_cents, total_cents, created_at
				)
				VALUES (:id, :saleId, :organizationId, :productId, 'MEMBERSHIP_PLAN', :name, 1,
				        :listPriceCents, :discountCents, :totalCents, :occurredAt)
				""")
				.param("id", UUID.randomUUID())
				.param("saleId", record.saleId())
				.param("organizationId", record.organizationId())
				.param("productId", record.productId())
				.param("name", record.planName())
				.param("listPriceCents", record.planPriceCents())
				.param("discountCents", record.discountCents())
				.param("totalCents", record.planPriceCents() - record.discountCents())
				.param("occurredAt", record.occurredAt())
				.update();

		if (record.registrationFeeCents() > 0) {
			jdbcClient.sql("""
					INSERT INTO sale_item (
					    id, sale_id, organization_id, product_id, item_type, name, quantity,
					    unit_price_cents, discount_cents, total_cents, created_at
					)
					VALUES (
					    :id, :saleId, :organizationId, NULL, 'REGISTRATION_FEE', :name, 1,
					    :amountCents, 0, :amountCents, :occurredAt
					)
					""")
					.param("id", UUID.randomUUID())
					.param("saleId", record.saleId())
					.param("organizationId", record.organizationId())
					.param("name", record.registrationFeeLabel())
					.param("amountCents", record.registrationFeeCents())
					.param("occurredAt", record.occurredAt())
					.update();
		}

		for (int index = 0; index < record.appliedBenefits().size(); index++) {
			MembershipBenefitSnapshot benefit = record.appliedBenefits().get(index);
			jdbcClient.sql("""
					INSERT INTO membership_sale_benefit (
					    id, organization_id, sale_id, benefit_id, benefit_code_snapshot,
					    benefit_name_snapshot, discount_cents, application_order, created_at
					)
					VALUES (
					    :id, :organizationId, :saleId, :benefitId, :code,
					    :name, :discountCents, :applicationOrder, :createdAt
					)
					""")
					.param("id", UUID.randomUUID())
					.param("organizationId", record.organizationId())
					.param("saleId", record.saleId())
					.param("benefitId", benefit.benefitId())
					.param("code", benefit.code())
					.param("name", benefit.name())
					.param("discountCents", benefit.discountCents())
					.param("applicationOrder", index)
					.param("createdAt", record.occurredAt())
					.update();
		}

		jdbcClient.sql("""
				INSERT INTO payment (
				    id, organization_id, branch_id, sale_id, client_id, method, status,
				    amount_cents, currency, reference, receipt_object_key, received_at,
				    validated_at, validated_by, notes, created_at, updated_at, created_by, updated_by, version,
				    idempotency_key, idempotency_request_hash
				)
				VALUES (
				    :id, :organizationId, :branchId, :saleId, :clientId, :method, :status,
				    :totalCents, :currency, :reference, NULL, :occurredAt,
				    :validatedAt, :validatedBy, :notes, :occurredAt, :occurredAt, :actorUserId, :actorUserId, 0,
				    :paymentIdempotencyKey, :paymentRequestHash
				)
				""")
				.param("id", record.paymentId())
				.param("organizationId", record.organizationId())
				.param("branchId", record.branchId())
				.param("saleId", record.saleId())
				.param("clientId", record.clientId())
				.param("method", record.paymentMethod())
				.param("status", record.paymentStatus())
				.param("totalCents", record.totalCents())
				.param("currency", record.currency())
				.param("reference", record.reference())
				.param("occurredAt", record.occurredAt())
				.param("validatedAt", record.validatedAt())
				.param("validatedBy", record.validatedBy())
				.param("notes", record.notes())
				.param("actorUserId", record.actorUserId())
				.param("paymentIdempotencyKey", record.paymentIdempotencyKey())
				.param("paymentRequestHash", record.paymentRequestHash())
				.update();
		return true;
	}

	public record MembershipSaleRecord(
			UUID saleId,
			UUID paymentId,
			UUID organizationId,
			UUID branchId,
			UUID clientId,
			UUID actorUserId,
			UUID sourceMembershipId,
			UUID targetPlanId,
			UUID productId,
			String planName,
			String saleNumber,
			String saleStatus,
			String paymentMethod,
			String paymentStatus,
			long planPriceCents,
			long registrationFeeCents,
			String registrationFeeLabel,
			long listPriceCents,
			long discountCents,
			long totalCents,
			long paidCents,
			long balanceCents,
			String currency,
			List<MembershipBenefitSnapshot> appliedBenefits,
			String reference,
			String notes,
			String idempotencyKey,
			String requestHash,
			String paymentIdempotencyKey,
			String paymentRequestHash,
			String businessContext,
			OffsetDateTime occurredAt,
			OffsetDateTime validatedAt,
			UUID validatedBy
	) {
	}
}
