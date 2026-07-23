package com.gymos.sales.infrastructure;

import com.gymos.sales.api.events.PaymentCompletedLineItemV1;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class SalePaymentCompletionRepository {
	private final JdbcClient jdbcClient;

	public SalePaymentCompletionRepository(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	public long completedPaymentTotal(UUID organizationId, UUID saleId) {
		return jdbcClient.sql("""
				SELECT COALESCE(SUM(amount_cents), 0)
				FROM payment
				WHERE organization_id = :organizationId
				  AND sale_id = :saleId
				  AND status = 'COMPLETED'
				  AND deleted_at IS NULL
				""")
				.param("organizationId", organizationId)
				.param("saleId", saleId)
				.query(Long.class)
				.single();
	}

	public void updateFinancials(
			UUID organizationId,
			UUID saleId,
			String status,
			long paidCents,
			long balanceCents,
			UUID actorUserId,
			OffsetDateTime updatedAt
	) {
		jdbcClient.sql("""
				UPDATE sale
				SET status = :status,
				    paid_cents = :paidCents,
				    balance_cents = :balanceCents,
				    updated_at = :updatedAt,
				    updated_by = :updatedBy,
				    version = version + 1
				WHERE organization_id = :organizationId
				  AND id = :saleId
				  AND deleted_at IS NULL
				""")
				.param("status", status)
				.param("paidCents", paidCents)
				.param("balanceCents", balanceCents)
				.param("updatedAt", updatedAt)
				.param("updatedBy", actorUserId)
				.param("organizationId", organizationId)
				.param("saleId", saleId)
				.update();
	}

	public boolean markPaymentCompleted(UUID organizationId, UUID saleId, OffsetDateTime completedAt) {
		return jdbcClient.sql("""
				UPDATE sale
				SET payment_completed_at = :paymentCompletedAt
				WHERE organization_id = :organizationId
				  AND id = :saleId
				  AND payment_completed_at IS NULL
				  AND deleted_at IS NULL
				""")
				.param("paymentCompletedAt", completedAt)
				.param("organizationId", organizationId)
				.param("saleId", saleId)
				.update() > 0;
	}

	public SaleContext context(UUID organizationId, UUID saleId) {
		return jdbcClient.sql("""
				SELECT business_context, context_membership_id, context_target_plan_id
				FROM sale
				WHERE organization_id = :organizationId
				  AND id = :saleId
				  AND deleted_at IS NULL
				""")
				.param("organizationId", organizationId)
				.param("saleId", saleId)
				.query((resultSet, rowNumber) -> new SaleContext(
						resultSet.getString("business_context"),
						resultSet.getObject("context_membership_id", UUID.class),
						resultSet.getObject("context_target_plan_id", UUID.class)
				))
				.single();
	}

	public List<PaymentCompletedLineItemV1> items(UUID organizationId, UUID saleId) {
		return jdbcClient.sql("""
				SELECT product_id, item_type, quantity
				FROM sale_item
				WHERE organization_id = :organizationId
				  AND sale_id = :saleId
				ORDER BY created_at, id
				""")
				.param("organizationId", organizationId)
				.param("saleId", saleId)
				.query((resultSet, rowNumber) -> new PaymentCompletedLineItemV1(
						resultSet.getObject("product_id", UUID.class),
						resultSet.getString("item_type"),
						resultSet.getInt("quantity")
				))
				.list();
	}

	public record SaleContext(String businessContext, UUID contextMembershipId, UUID contextTargetPlanId) {
	}
}
