package com.gymos.memberships.infrastructure;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class MembershipPricingRepository {
	private final JdbcClient jdbcClient;

	public MembershipPricingRepository(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	public List<EligiblePricingBenefit> eligibleBenefits(
			UUID organizationId,
			UUID branchId,
			UUID clientId,
			LocalDate businessDate
	) {
		return jdbcClient.sql("""
				SELECT b.id, b.code, b.name, b.discount_type, b.discount_value_cents,
				       b.discount_percent, b.combinable, b.priority
				FROM client_benefit cb
				JOIN benefit b
				  ON b.id = cb.benefit_id
				 AND b.organization_id = cb.organization_id
				WHERE cb.organization_id = :organizationId
				  AND cb.client_id = :clientId
				  AND cb.status IN ('ASSIGNED', 'VERIFIED')
				  AND cb.deleted_at IS NULL
				  AND (cb.branch_id IS NULL OR cb.branch_id = :branchId)
				  AND (cb.valid_from IS NULL OR cb.valid_from <= :businessDate)
				  AND (cb.valid_until IS NULL OR cb.valid_until >= :businessDate)
				  AND b.benefit_type <> 'ACCESS'
				  AND b.status = 'ACTIVE'
				  AND b.deleted_at IS NULL
				  AND (b.branch_id IS NULL OR b.branch_id = :branchId)
				  AND (b.valid_from IS NULL OR b.valid_from <= :businessDate)
				  AND (b.valid_until IS NULL OR b.valid_until >= :businessDate)
				  AND (b.requires_evidence = false OR cb.status = 'VERIFIED')
				ORDER BY b.priority ASC, b.id
				""")
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("clientId", clientId)
				.param("businessDate", businessDate)
				.query((resultSet, rowNumber) -> new EligiblePricingBenefit(
						resultSet.getObject("id", UUID.class),
						resultSet.getString("code"),
						resultSet.getString("name"),
						resultSet.getString("discount_type"),
						(Integer) resultSet.getObject("discount_value_cents"),
						resultSet.getBigDecimal("discount_percent") == null
								? null
								: resultSet.getBigDecimal("discount_percent").doubleValue(),
						resultSet.getBoolean("combinable"),
						resultSet.getInt("priority")
				))
				.list();
	}

	public record EligiblePricingBenefit(
			UUID id,
			String code,
			String name,
			String discountType,
			Integer discountValueCents,
			Double discountPercent,
			boolean combinable,
			int priority
	) {
	}
}
