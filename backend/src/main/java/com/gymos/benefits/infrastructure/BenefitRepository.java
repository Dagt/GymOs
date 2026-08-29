package com.gymos.benefits.infrastructure;

import com.gymos.benefits.application.command.AssignClientBenefitCommand;
import com.gymos.benefits.application.command.CreateBenefitCommand;
import com.gymos.common.security.GymOsPrincipal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class BenefitRepository {
	private final JdbcClient jdbcClient;

	public BenefitRepository(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	public List<BenefitSummary> benefits(UUID organizationId) {
		List<BenefitSummary> summaries = jdbcClient.sql("""
				SELECT id, branch_id, code, name, benefit_type, discount_type, discount_value_cents,
				       discount_percent, requires_evidence, combinable, priority, valid_from, valid_until,
				       status, access_scope, version
				FROM benefit
				WHERE organization_id = :organizationId
				  AND deleted_at IS NULL
				ORDER BY priority ASC, name ASC
				""")
				.param("organizationId", organizationId)
				.query((rs, row) -> new BenefitSummary(
						rs.getObject("id", UUID.class),
						rs.getObject("branch_id", UUID.class),
						rs.getString("code"),
						rs.getString("name"),
						rs.getString("benefit_type"),
						rs.getString("discount_type"),
						(Integer) rs.getObject("discount_value_cents"),
						rs.getBigDecimal("discount_percent") == null ? null : rs.getBigDecimal("discount_percent").doubleValue(),
						rs.getBoolean("requires_evidence"),
						rs.getBoolean("combinable"),
						rs.getInt("priority"),
						rs.getObject("valid_from", LocalDate.class),
						rs.getObject("valid_until", LocalDate.class),
						rs.getString("status"),
						List.of(), rs.getString("access_scope"), List.of(), List.of(), rs.getLong("version")
				))
				.list();
		return summaries.stream()
				.map(summary -> summary.withPlanLinks(
						membershipPlanIds(organizationId, summary.id()),
						applicableMembershipPlanIds(organizationId, summary.id())))
				.map(summary -> summary.withAccessBranches(accessBranchIds(organizationId, summary)))
				.toList();
	}

	public List<BenefitSummary> benefitsForBranch(UUID organizationId, UUID branchId) {
		if (branchId == null) {
			return benefits(organizationId);
		}
		return benefits(organizationId).stream()
				.filter(benefit -> !"ACCESS".equals(benefit.benefitType())
						|| accessBenefitCoversBranch(benefit, branchId))
				.toList();
	}

	private boolean accessBenefitCoversBranch(BenefitSummary benefit, UUID branchId) {
		return benefit.accessBranchIds().contains(branchId);
	}

	private List<UUID> accessBranchIds(UUID organizationId, BenefitSummary benefit) {
		if (!"ACCESS".equals(benefit.benefitType()) || !"ACTIVE".equals(benefit.status())) {
			return List.of();
		}
		if (!"PLAN_COVERAGE".equals(benefit.accessScope())) {
			if (benefit.branchId() != null) {
				return List.of(benefit.branchId());
			}
			return jdbcClient.sql("""
					SELECT id FROM branch
					WHERE organization_id=:organizationId AND status='ACTIVE' AND deleted_at IS NULL
					ORDER BY id
					""").param("organizationId", organizationId).query(UUID.class).list();
		}
		return jdbcClient.sql("""
				SELECT branch.id
				FROM branch branch
				WHERE branch.organization_id=:organizationId
				  AND branch.status='ACTIVE'
				  AND branch.deleted_at IS NULL
				  AND EXISTS (
				    SELECT 1
				    FROM benefit_membership_plan benefit_plan
				    JOIN membership_plan membership_plan
				      ON membership_plan.organization_id = benefit_plan.organization_id
				     AND membership_plan.id = benefit_plan.membership_plan_id
				     AND membership_plan.status = 'ACTIVE'
				     AND membership_plan.deleted_at IS NULL
				    WHERE benefit_plan.organization_id = :organizationId
				      AND benefit_plan.benefit_id = :benefitId
				      AND (
				          membership_plan.multi_branch_access = TRUE
				          OR EXISTS (
				              SELECT 1 FROM catalog_product_branch product_branch
				              WHERE product_branch.organization_id = membership_plan.organization_id
				                AND product_branch.product_id = membership_plan.product_id
				                AND product_branch.branch_id = branch.id
				          )
				      )
				)
				ORDER BY branch.id
				""")
				.param("organizationId", organizationId)
				.param("benefitId", benefit.id())
				.query(UUID.class).list();
	}

	public Optional<BenefitSummary> benefit(UUID organizationId, UUID benefitId) {
		return benefits(organizationId).stream().filter(benefit -> benefit.id().equals(benefitId)).findFirst();
	}

	public boolean codeExists(UUID organizationId, UUID branchId, String code) {
		Integer count = jdbcClient.sql("""
				SELECT COUNT(*)
				FROM benefit
				WHERE organization_id = :organizationId
				  AND branch_id = :branchId
				  AND code = :code
				  AND deleted_at IS NULL
				""")
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("code", code.trim().toUpperCase())
				.query(Integer.class)
				.single();
		return count > 0;
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

	public boolean branchExists(UUID organizationId, UUID branchId) {
		Integer count = jdbcClient.sql("""
				SELECT COUNT(*)
				FROM branch
				WHERE organization_id = :organizationId
				  AND id = :branchId
				  AND status = 'ACTIVE'
				  AND deleted_at IS NULL
				""")
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.query(Integer.class)
				.single();
		return count > 0;
	}

	public UUID createBenefit(
			GymOsPrincipal principal,
			UUID branchId,
			CreateBenefitCommand command
	) {
		UUID id = UUID.randomUUID();
		OffsetDateTime now = OffsetDateTime.now();
		jdbcClient.sql("""
				INSERT INTO benefit (
				    id, organization_id, branch_id, code, name, benefit_type, discount_type,
				    discount_value_cents, discount_percent, requires_evidence, combinable,
				    priority, valid_from, valid_until, status, access_scope, created_at, updated_at, version
				)
				VALUES (
				    :id, :organizationId, :branchId, :code, :name, :benefitType, :discountType,
				    :discountValueCents, :discountPercent, :requiresEvidence, :combinable,
				    :priority, :validFrom, :validUntil, :status, :accessScope, :now, :now, 0
				)
				""")
				.param("id", id)
				.param("organizationId", principal.organizationId())
				.param("branchId", branchId)
				.param("code", command.code().trim().toUpperCase())
				.param("name", command.name().trim())
				.param("benefitType", command.benefitType().trim().toUpperCase())
				.param("discountType", command.discountType().trim().toUpperCase())
				.param("discountValueCents", command.discountValueCents())
				.param("discountPercent", command.discountPercent())
				.param("requiresEvidence", command.requiresEvidence())
				.param("combinable", command.combinable())
				.param("priority", command.priority())
				.param("validFrom", command.validFrom())
				.param("validUntil", command.validUntil())
				.param("status", command.status().trim().toUpperCase())
				.param("accessScope", command.accessScope().trim().toUpperCase())
				.param("now", now)
				.update();
		return id;
	}

	public boolean membershipPlansExist(UUID organizationId, List<UUID> planIds) {
		if (planIds == null || planIds.isEmpty()) {
			return true;
		}
		List<UUID> distinctPlanIds = planIds.stream().distinct().toList();
		Integer count = jdbcClient.sql("""
				SELECT COUNT(*)
				FROM membership_plan
				WHERE organization_id = :organizationId
				  AND id IN (:planIds)
				  AND status = 'ACTIVE'
				  AND deleted_at IS NULL
				""")
				.param("organizationId", organizationId)
				.param("planIds", distinctPlanIds)
				.query(Integer.class)
				.single();
		return count == distinctPlanIds.size();
	}

	public boolean updateBenefit(UUID organizationId, UUID benefitId, CreateBenefitCommand command, long expectedVersion) {
		return jdbcClient.sql("""
				UPDATE benefit SET name = :name, discount_type = :discountType,
				    discount_value_cents = :discountValueCents, discount_percent = :discountPercent,
				    requires_evidence = :requiresEvidence, combinable = :combinable, priority = :priority,
				    valid_from = :validFrom, valid_until = :validUntil, status = :status,
				    access_scope = :accessScope,
				    updated_at = :now, version = version + 1
				WHERE organization_id = :organizationId AND id = :benefitId
				  AND deleted_at IS NULL AND version = :expectedVersion
				""")
				.param("organizationId", organizationId).param("benefitId", benefitId)
				.param("name", command.name().trim()).param("discountType", command.discountType())
				.param("discountValueCents", command.discountValueCents()).param("discountPercent", command.discountPercent())
				.param("requiresEvidence", command.requiresEvidence()).param("combinable", command.combinable())
				.param("priority", command.priority()).param("validFrom", command.validFrom()).param("validUntil", command.validUntil())
				.param("status", command.status()).param("accessScope", command.accessScope())
				.param("now", OffsetDateTime.now()).param("expectedVersion", expectedVersion)
				.update() == 1;
	}

	public void replaceMembershipPlans(GymOsPrincipal principal, UUID benefitId, List<UUID> planIds) {
		jdbcClient.sql("""
				DELETE FROM benefit_membership_plan
				WHERE organization_id = :organizationId
				  AND benefit_id = :benefitId
				""")
				.param("organizationId", principal.organizationId())
				.param("benefitId", benefitId)
				.update();
		OffsetDateTime now = OffsetDateTime.now();
		for (UUID planId : planIds.stream().distinct().toList()) {
			jdbcClient.sql("""
					INSERT INTO benefit_membership_plan (
					    benefit_id, membership_plan_id, organization_id, created_at, created_by
					)
					VALUES (:benefitId, :planId, :organizationId, :now, :userId)
					""")
					.param("benefitId", benefitId)
					.param("planId", planId)
					.param("organizationId", principal.organizationId())
					.param("now", now)
					.param("userId", principal.userId())
					.update();
		}
	}

	public void replaceApplicableMembershipPlans(GymOsPrincipal principal, UUID benefitId, List<UUID> planIds) {
		jdbcClient.sql("""
				DELETE FROM pricing_benefit_membership_plan
				WHERE organization_id = :organizationId
				  AND benefit_id = :benefitId
				""")
				.param("organizationId", principal.organizationId())
				.param("benefitId", benefitId)
				.update();
		OffsetDateTime now = OffsetDateTime.now();
		for (UUID planId : planIds.stream().distinct().toList()) {
			jdbcClient.sql("""
					INSERT INTO pricing_benefit_membership_plan (
					    benefit_id, membership_plan_id, organization_id, created_at, created_by
					)
					VALUES (:benefitId, :planId, :organizationId, :now, :userId)
					""")
					.param("benefitId", benefitId)
					.param("planId", planId)
					.param("organizationId", principal.organizationId())
					.param("now", now)
					.param("userId", principal.userId())
					.update();
		}
	}

	private List<UUID> membershipPlanIds(UUID organizationId, UUID benefitId) {
		return jdbcClient.sql("""
				SELECT membership_plan_id
				FROM benefit_membership_plan
				WHERE organization_id = :organizationId
				  AND benefit_id = :benefitId
				ORDER BY membership_plan_id
				""")
				.param("organizationId", organizationId)
				.param("benefitId", benefitId)
				.query(UUID.class)
				.list();
	}

	private List<UUID> applicableMembershipPlanIds(UUID organizationId, UUID benefitId) {
		return jdbcClient.sql("""
				SELECT membership_plan_id
				FROM pricing_benefit_membership_plan
				WHERE organization_id = :organizationId
				  AND benefit_id = :benefitId
				ORDER BY membership_plan_id
				""")
				.param("organizationId", organizationId)
				.param("benefitId", benefitId)
				.query(UUID.class)
				.list();
	}

	public UUID assignClientBenefit(GymOsPrincipal principal, AssignClientBenefitCommand command) {
		UUID id = UUID.randomUUID();
		OffsetDateTime now = OffsetDateTime.now();
		jdbcClient.sql("""
				INSERT INTO client_benefit (
				    id, organization_id, branch_id, client_id, benefit_id, status, assigned_at,
				    valid_from, valid_until, notes, created_at, updated_at, version
				)
				VALUES (
				    :id, :organizationId, :branchId, :clientId, :benefitId, 'ASSIGNED', :now,
				    :validFrom, :validUntil, :notes, :now, :now, 0
				)
				""")
				.param("id", id)
				.param("organizationId", principal.organizationId())
				.param("branchId", command.branchId() == null ? principal.branchId() : command.branchId())
				.param("clientId", command.clientId())
				.param("benefitId", command.benefitId())
				.param("validFrom", command.validFrom())
				.param("validUntil", command.validUntil())
				.param("notes", command.notes())
				.param("now", now)
				.update();
		return id;
	}

	public Optional<ClientBenefitSummary> clientBenefit(UUID organizationId, UUID clientBenefitId) {
		return jdbcClient.sql("""
				SELECT cb.id, cb.branch_id, cb.client_id, cb.benefit_id, b.name AS benefit_name, cb.status,
				       cb.valid_from, cb.valid_until, cb.verified_at
				FROM client_benefit cb
				JOIN benefit b ON b.id = cb.benefit_id
				WHERE cb.organization_id = :organizationId
				  AND cb.id = :clientBenefitId
				  AND cb.deleted_at IS NULL
				""")
				.param("organizationId", organizationId)
				.param("clientBenefitId", clientBenefitId)
				.query((rs, row) -> new ClientBenefitSummary(
						rs.getObject("id", UUID.class),
						rs.getObject("branch_id", UUID.class),
						rs.getObject("client_id", UUID.class),
						rs.getObject("benefit_id", UUID.class),
						rs.getString("benefit_name"),
						rs.getString("status"),
						rs.getObject("valid_from", LocalDate.class),
						rs.getObject("valid_until", LocalDate.class),
						rs.getObject("verified_at", OffsetDateTime.class)
				))
				.optional();
	}

	public List<ClientBenefitSummary> clientBenefits(UUID organizationId, UUID branchId, UUID clientId) {
		if (branchId == null) {
			return clientBenefits(organizationId, clientId);
		}
		return jdbcClient.sql("""
				SELECT cb.id, cb.branch_id, cb.client_id, cb.benefit_id, b.name AS benefit_name, cb.status,
				       cb.valid_from, cb.valid_until, cb.verified_at
				FROM client_benefit cb
				JOIN benefit b
				  ON b.id = cb.benefit_id
				 AND b.organization_id = cb.organization_id
				WHERE cb.organization_id = :organizationId
				  AND cb.branch_id = :branchId
				  AND cb.client_id = :clientId
				  AND cb.deleted_at IS NULL
				ORDER BY cb.assigned_at DESC, cb.id DESC
				""")
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("clientId", clientId)
				.query((rs, row) -> new ClientBenefitSummary(
						rs.getObject("id", UUID.class),
						rs.getObject("branch_id", UUID.class),
						rs.getObject("client_id", UUID.class),
						rs.getObject("benefit_id", UUID.class),
						rs.getString("benefit_name"),
						rs.getString("status"),
						rs.getObject("valid_from", LocalDate.class),
						rs.getObject("valid_until", LocalDate.class),
						rs.getObject("verified_at", OffsetDateTime.class)
				))
				.list();
	}

	private List<ClientBenefitSummary> clientBenefits(UUID organizationId, UUID clientId) {
		return jdbcClient.sql("""
				SELECT cb.id, cb.branch_id, cb.client_id, cb.benefit_id, b.name AS benefit_name, cb.status,
				       cb.valid_from, cb.valid_until, cb.verified_at
				FROM client_benefit cb
				JOIN benefit b
				  ON b.id = cb.benefit_id
				 AND b.organization_id = cb.organization_id
				WHERE cb.organization_id = :organizationId
				  AND cb.client_id = :clientId
				  AND cb.deleted_at IS NULL
				ORDER BY cb.assigned_at DESC, cb.id DESC
				""")
				.param("organizationId", organizationId)
				.param("clientId", clientId)
				.query((rs, row) -> new ClientBenefitSummary(
						rs.getObject("id", UUID.class),
						rs.getObject("branch_id", UUID.class),
						rs.getObject("client_id", UUID.class),
						rs.getObject("benefit_id", UUID.class),
						rs.getString("benefit_name"),
						rs.getString("status"),
						rs.getObject("valid_from", LocalDate.class),
						rs.getObject("valid_until", LocalDate.class),
						rs.getObject("verified_at", OffsetDateTime.class)
				))
				.list();
	}

	public void verifyClientBenefit(GymOsPrincipal principal, UUID clientBenefitId) {
		jdbcClient.sql("""
				UPDATE client_benefit
				SET status = 'VERIFIED',
				    verified_at = :now,
				    verified_by = :userId,
				    updated_at = :now,
				    version = version + 1
				WHERE organization_id = :organizationId
				  AND id = :clientBenefitId
				  AND deleted_at IS NULL
				""")
				.param("organizationId", principal.organizationId())
				.param("clientBenefitId", clientBenefitId)
				.param("userId", principal.userId())
				.param("now", OffsetDateTime.now())
				.update();
	}

	public record BenefitSummary(
			UUID id,
			UUID branchId,
			String code,
			String name,
			String benefitType,
			String discountType,
			Integer discountValueCents,
			Double discountPercent,
			boolean requiresEvidence,
			boolean combinable,
			int priority,
			LocalDate validFrom,
			LocalDate validUntil,
			String status,
			List<UUID> planIds,
			String accessScope,
			List<UUID> applicablePlanIds,
			List<UUID> accessBranchIds,
			long version
	) {
		public BenefitSummary {
			planIds = planIds == null ? List.of() : List.copyOf(planIds);
			applicablePlanIds = applicablePlanIds == null ? List.of() : List.copyOf(applicablePlanIds);
			accessBranchIds = accessBranchIds == null ? List.of() : List.copyOf(accessBranchIds);
		}
		public BenefitSummary(UUID id, UUID branchId, String code, String name, String benefitType,
				String discountType, Integer discountValueCents, Double discountPercent, boolean requiresEvidence,
				boolean combinable, int priority, LocalDate validFrom, LocalDate validUntil, String status, List<UUID> planIds) {
			this(id, branchId, code, name, benefitType, discountType, discountValueCents, discountPercent,
					requiresEvidence, combinable, priority, validFrom, validUntil, status, planIds,
					"LOCAL", List.of(), List.of(), 0);
		}
		BenefitSummary withPlanLinks(List<UUID> resolvedPlanIds, List<UUID> resolvedApplicablePlanIds) {
			return new BenefitSummary(id, branchId, code, name, benefitType, discountType,
					discountValueCents, discountPercent, requiresEvidence, combinable, priority,
					validFrom, validUntil, status, resolvedPlanIds, accessScope,
					resolvedApplicablePlanIds, accessBranchIds, version);
		}
		BenefitSummary withAccessBranches(List<UUID> resolvedAccessBranchIds) {
			return new BenefitSummary(id, branchId, code, name, benefitType, discountType,
					discountValueCents, discountPercent, requiresEvidence, combinable, priority,
					validFrom, validUntil, status, planIds, accessScope,
					applicablePlanIds, resolvedAccessBranchIds, version);
		}
	}

	public record ClientBenefitSummary(
			UUID id,
			UUID branchId,
			UUID clientId,
			UUID benefitId,
			String benefitName,
			String status,
			LocalDate validFrom,
			LocalDate validUntil,
			OffsetDateTime verifiedAt
	) {
	}
}
