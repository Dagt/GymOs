package com.gymos.organizations.infrastructure;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class OrganizationQueryRepository {
	private final JdbcClient jdbcClient;

	public OrganizationQueryRepository(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	public Optional<OrganizationSummary> findCurrentOrganization(UUID organizationId) {
		return jdbcClient.sql("""
				SELECT id, legal_name, trade_name, tax_id, status, timezone,
				       default_currency, contact_email, contact_phone
				FROM organization
				WHERE id = :organizationId
				  AND deleted_at IS NULL
				""")
				.param("organizationId", organizationId)
				.query((resultSet, rowNumber) -> new OrganizationSummary(
						resultSet.getObject("id", UUID.class),
						resultSet.getString("legal_name"),
						resultSet.getString("trade_name"),
						resultSet.getString("tax_id"),
						resultSet.getString("status"),
						resultSet.getString("timezone"),
						resultSet.getString("default_currency"),
						resultSet.getString("contact_email"),
						resultSet.getString("contact_phone")
				))
				.optional();
	}

	public List<ManagedOrganizationSummary> findOrganizationsForUser(UUID userId) {
		return jdbcClient.sql("""
				SELECT o.id, o.legal_name, o.trade_name, o.tax_id, o.status, o.timezone,
				       o.default_currency, o.contact_email, o.contact_phone,
				       COUNT(DISTINCT b.id) AS branch_count,
				       COUNT(DISTINCT CASE WHEN b.status = 'ACTIVE' THEN b.id END) AS active_branch_count
				FROM organization o
				LEFT JOIN branch b ON b.organization_id = o.id AND b.deleted_at IS NULL
				WHERE o.deleted_at IS NULL
				  AND EXISTS (
				      SELECT 1
				      FROM user_branch_access uba
				      JOIN branch accessible_branch ON accessible_branch.id = uba.branch_id
				      WHERE uba.user_id = :userId
				        AND accessible_branch.organization_id = o.id
				        AND accessible_branch.deleted_at IS NULL
				  )
				GROUP BY o.id, o.legal_name, o.trade_name, o.tax_id, o.status, o.timezone,
				         o.default_currency, o.contact_email, o.contact_phone
				ORDER BY LOWER(o.trade_name)
				""")
				.param("userId", userId)
				.query((resultSet, rowNumber) -> new ManagedOrganizationSummary(
						resultSet.getObject("id", UUID.class),
						resultSet.getString("legal_name"),
						resultSet.getString("trade_name"),
						resultSet.getString("tax_id"),
						resultSet.getString("status"),
						resultSet.getString("timezone"),
						resultSet.getString("default_currency"),
						resultSet.getString("contact_email"),
						resultSet.getString("contact_phone"),
						resultSet.getInt("branch_count"),
						resultSet.getInt("active_branch_count")
				))
				.list();
	}

	public List<BranchSummary> findBranchesForUser(UUID userId, UUID organizationId) {
		return jdbcClient.sql("""
				SELECT b.id, b.name, b.code, b.status, b.timezone, b.phone, uba.access_scope
				FROM user_branch_access uba
				JOIN branch b ON b.id = uba.branch_id
				WHERE uba.user_id = :userId
				  AND b.organization_id = :organizationId
				  AND b.deleted_at IS NULL
				ORDER BY b.name
				""")
				.param("userId", userId)
				.param("organizationId", organizationId)
				.query((resultSet, rowNumber) -> new BranchSummary(
						resultSet.getObject("id", UUID.class),
						resultSet.getString("name"),
						resultSet.getString("code"),
						resultSet.getString("status"),
						resultSet.getString("timezone"),
						resultSet.getString("phone"),
						resultSet.getString("access_scope")
				))
				.list();
	}

	public Optional<BranchSummary> findBranchForUser(UUID userId, UUID organizationId, UUID branchId) {
		return jdbcClient.sql("""
				SELECT b.id, b.name, b.code, b.status, b.timezone, b.phone, uba.access_scope
				FROM user_branch_access uba
				JOIN branch b ON b.id = uba.branch_id
				WHERE uba.user_id = :userId
				  AND b.organization_id = :organizationId
				  AND b.id = :branchId
				  AND b.deleted_at IS NULL
				""")
				.param("userId", userId)
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.query((resultSet, rowNumber) -> new BranchSummary(
						resultSet.getObject("id", UUID.class),
						resultSet.getString("name"),
						resultSet.getString("code"),
						resultSet.getString("status"),
						resultSet.getString("timezone"),
						resultSet.getString("phone"),
						resultSet.getString("access_scope")
				))
				.optional();
	}

	public boolean userHasBranchAccess(UUID userId, UUID organizationId, UUID branchId) {
		Integer count = jdbcClient.sql("""
				SELECT COUNT(*)
				FROM user_branch_access uba
				JOIN branch b ON b.id = uba.branch_id
				WHERE uba.user_id = :userId
				  AND b.organization_id = :organizationId
				  AND b.id = :branchId
				  AND b.deleted_at IS NULL
				""")
				.param("userId", userId)
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.query(Integer.class)
				.single();
		return count > 0;
	}

	public record ManagedOrganizationSummary(
			UUID id,
			String legalName,
			String tradeName,
			String taxId,
			String status,
			String timezone,
			String defaultCurrency,
			String contactEmail,
			String contactPhone,
			int branchCount,
			int activeBranchCount
	) {
	}

	public record OrganizationSummary(
			UUID id,
			String legalName,
			String tradeName,
			String taxId,
			String status,
			String timezone,
			String defaultCurrency,
			String contactEmail,
			String contactPhone
	) {
	}

	public record BranchSummary(
			UUID id,
			String name,
			String code,
			String status,
			String timezone,
			String phone,
			String accessScope
	) {
	}
}
