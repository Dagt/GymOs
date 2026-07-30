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
				       discount_percent, requires_evidence, combinable, priority, valid_from, valid_until, status
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
						List.of()
				))
				.list();
		return summaries.stream()
				.map(summary -> summary.withPlanIds(membershipPlanIds(organizationId, summary.id())))
				.toList();
	}

	public List<BenefitSummary> benefitsForBranch(UUID organizationId, UUID branchId) {
		if (branchId == null) {
			return benefits(organizationId);
		}
		return benefits(organizationId).stream()
				.filter(benefit -> benefit.branchId() == null || branchId.equals(benefit.branchId()))
				.toList();
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
				    priority, valid_from, valid_until, status, created_at, updated_at, version
				)
				VALUES (
				    :id, :organizationId, :branchId, :code, :name, :benefitType, :discountType,
				    :discountValueCents, :discountPercent, :requiresEvidence, :combinable,
				    :priority, :validFrom, :validUntil, :status, :now, :now, 0
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
			List<UUID> planIds
	) {
		BenefitSummary withPlanIds(List<UUID> resolvedPlanIds) {
			return new BenefitSummary(id, branchId, code, name, benefitType, discountType,
					discountValueCents, discountPercent, requiresEvidence, combinable, priority,
					validFrom, validUntil, status, resolvedPlanIds);
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
