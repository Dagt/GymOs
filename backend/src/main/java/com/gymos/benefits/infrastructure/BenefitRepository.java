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
		return jdbcClient.sql("""
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
						rs.getString("status")
				))
				.list();
	}

	public Optional<BenefitSummary> benefit(UUID organizationId, UUID benefitId) {
		return benefits(organizationId).stream().filter(benefit -> benefit.id().equals(benefitId)).findFirst();
	}

	public boolean codeExists(UUID organizationId, String code) {
		Integer count = jdbcClient.sql("""
				SELECT COUNT(*)
				FROM benefit
				WHERE organization_id = :organizationId
				  AND code = :code
				  AND deleted_at IS NULL
				""")
				.param("organizationId", organizationId)
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

	public UUID createBenefit(GymOsPrincipal principal, CreateBenefitCommand command) {
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
				.param("branchId", command.branchId())
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
				SELECT cb.id, cb.client_id, cb.benefit_id, b.name AS benefit_name, cb.status,
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
			String status
	) {
	}

	public record ClientBenefitSummary(
			UUID id,
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
