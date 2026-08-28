package com.gymos.eligibility.infrastructure;

import com.gymos.eligibility.api.EligibilityPolicy.ActiveMembership;
import com.gymos.eligibility.api.EligibilityPolicy.BenefitType;
import com.gymos.eligibility.api.EligibilityPolicy.EligibleBenefit;
import com.gymos.eligibility.application.EligibilityFactsProvider;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class EligibilityRepository implements EligibilityFactsProvider {
	private final JdbcClient jdbcClient;

	public EligibilityRepository(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	@Override
	public Optional<String> clientStatus(UUID organizationId, UUID clientId) {
		return jdbcClient.sql("""
				SELECT status
				FROM client
				WHERE organization_id = :organizationId
				  AND id = :clientId
				  AND deleted_at IS NULL
				""")
				.param("organizationId", organizationId)
				.param("clientId", clientId)
				.query(String.class)
				.optional();
	}

	@Override
	public Optional<ActiveMembership> activeMembership(
			UUID organizationId, UUID branchId, UUID clientId, LocalDate effectiveOn
	) {
		return jdbcClient.sql("""
				SELECT m.id, m.plan_id, cp.name AS plan_name,
				       m.starts_on, m.ends_on, m.grace_ends_on
				FROM membership m
				JOIN membership_plan mp ON mp.id = m.plan_id
				JOIN catalog_product cp ON cp.id = mp.product_id
				WHERE m.organization_id = :organizationId
				  AND (m.branch_id = :branchId OR mp.multi_branch_access = true)
				  AND m.client_id = :clientId
				  AND m.status = 'ACTIVE'
				  AND m.starts_on <= :effectiveOn
				  AND m.grace_ends_on >= :effectiveOn
				  AND m.deleted_at IS NULL
				  AND mp.deleted_at IS NULL
				  AND cp.deleted_at IS NULL
				ORDER BY m.ends_on DESC, m.id
				LIMIT 1
				""")
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("clientId", clientId)
				.param("effectiveOn", effectiveOn)
				.query((resultSet, rowNumber) -> new ActiveMembership(
						resultSet.getObject("id", UUID.class),
						resultSet.getObject("plan_id", UUID.class),
						resultSet.getString("plan_name"),
						resultSet.getObject("starts_on", LocalDate.class),
						resultSet.getObject("ends_on", LocalDate.class),
						resultSet.getObject("grace_ends_on", LocalDate.class)
				))
				.optional();
	}

	@Override
	public List<EligibleBenefit> pricingBenefits(
			UUID organizationId, UUID branchId, UUID clientId, LocalDate effectiveOn, UUID targetPlanId
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
				  AND (cb.valid_from IS NULL OR cb.valid_from <= :effectiveOn)
				  AND (cb.valid_until IS NULL OR cb.valid_until >= :effectiveOn)
				  AND b.benefit_type <> 'ACCESS'
				  AND b.status = 'ACTIVE'
				  AND b.deleted_at IS NULL
				  AND (b.discount_type <> 'SPECIAL_PRICE' OR EXISTS (
				      SELECT 1
				      FROM pricing_benefit_membership_plan pbmp
				      WHERE pbmp.organization_id = b.organization_id
				        AND pbmp.benefit_id = b.id
				        AND pbmp.membership_plan_id = :targetPlanId
				  ))
				  AND (b.branch_id IS NULL OR b.branch_id = :branchId)
				  AND (b.valid_from IS NULL OR b.valid_from <= :effectiveOn)
				  AND (b.valid_until IS NULL OR b.valid_until >= :effectiveOn)
				  AND (b.requires_evidence = false OR cb.status = 'VERIFIED')
				ORDER BY b.priority ASC, b.id
				""")
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("clientId", clientId)
				.param("effectiveOn", effectiveOn)
				.param("targetPlanId", targetPlanId)
				.query((resultSet, rowNumber) -> new EligibleBenefit(
						resultSet.getObject("id", UUID.class),
						resultSet.getString("code"),
						resultSet.getString("name"),
						BenefitType.PRICING,
						resultSet.getString("discount_type"),
						(Integer) resultSet.getObject("discount_value_cents"),
						resultSet.getBigDecimal("discount_percent") == null
								? null
								: resultSet.getBigDecimal("discount_percent").doubleValue(),
						resultSet.getBoolean("combinable"),
						resultSet.getInt("priority"),
						null, null, null
				))
				.list();
	}

	@Override
	public List<EligibleBenefit> accessBenefits(
			UUID organizationId, UUID branchId, UUID clientId, LocalDate effectiveOn
	) {
		return jdbcClient.sql("""
				SELECT DISTINCT b.id, b.code, b.name,
				       m.id AS membership_id, m.plan_id, cp.name AS plan_name
				FROM membership m
				JOIN membership_plan mp ON mp.id = m.plan_id
				JOIN catalog_product cp ON cp.id = mp.product_id
				JOIN benefit_membership_plan bmp
				  ON bmp.membership_plan_id = m.plan_id
				 AND bmp.organization_id = m.organization_id
				JOIN benefit b
				  ON b.id = bmp.benefit_id
				 AND b.organization_id = m.organization_id
				WHERE m.organization_id = :organizationId
				  AND (m.branch_id = :branchId OR mp.multi_branch_access = true)
				  AND m.client_id = :clientId
				  AND m.status = 'ACTIVE'
				  AND m.starts_on <= :effectiveOn
				  AND m.grace_ends_on >= :effectiveOn
				  AND m.deleted_at IS NULL
				  AND mp.deleted_at IS NULL
				  AND cp.deleted_at IS NULL
				  AND b.benefit_type = 'ACCESS'
				  AND b.status = 'ACTIVE'
				  AND b.deleted_at IS NULL
				  AND (b.access_scope = 'PLAN_COVERAGE' OR b.branch_id IS NULL OR b.branch_id = :branchId)
				  AND (b.valid_from IS NULL OR b.valid_from <= :effectiveOn)
				  AND (b.valid_until IS NULL OR b.valid_until >= :effectiveOn)
				ORDER BY b.name, b.id
				""")
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("clientId", clientId)
				.param("effectiveOn", effectiveOn)
				.query((resultSet, rowNumber) -> new EligibleBenefit(
						resultSet.getObject("id", UUID.class),
						resultSet.getString("code"),
						resultSet.getString("name"),
						BenefitType.ACCESS,
						"NONE", null, null, false, 0,
						resultSet.getObject("membership_id", UUID.class),
						resultSet.getObject("plan_id", UUID.class),
						resultSet.getString("plan_name")
				))
				.list();
	}
}
