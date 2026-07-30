package com.gymos.organizations.infrastructure;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.OffsetDateTime;
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
				       default_currency, contact_email, contact_phone,
				       brand_name, logo_url, primary_color, accent_color, surface_color
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
						resultSet.getString("contact_phone"),
						resultSet.getString("brand_name"),
						resultSet.getString("logo_url"),
						resultSet.getString("primary_color"),
						resultSet.getString("accent_color"),
						resultSet.getString("surface_color")
				))
				.optional();
	}

	public Optional<String> findLogoObjectKey(UUID organizationId) {
		return jdbcClient.sql("""
				SELECT logo_object_key
				FROM organization
				WHERE id = :organizationId
				  AND deleted_at IS NULL
				  AND logo_object_key IS NOT NULL
				""")
				.param("organizationId", organizationId)
				.query(String.class)
				.optional();
	}

	public List<ManagedOrganizationSummary> findOrganizationsForUser(UUID userId) {
		return jdbcClient.sql("""
				SELECT o.id, o.legal_name, o.trade_name, o.tax_id, o.status, o.timezone,
				       o.default_currency, o.contact_email, o.contact_phone,
				       o.brand_name, o.logo_url, o.primary_color, o.accent_color, o.surface_color,
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
				         o.default_currency, o.contact_email, o.contact_phone,
				         o.brand_name, o.logo_url, o.primary_color, o.accent_color, o.surface_color
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
						resultSet.getString("brand_name"),
						resultSet.getString("logo_url"),
						resultSet.getString("primary_color"),
						resultSet.getString("accent_color"),
						resultSet.getString("surface_color"),
						resultSet.getInt("branch_count"),
						resultSet.getInt("active_branch_count")
				))
				.list();
	}

	public List<ManagedOrganizationSummary> findAllOrganizations() {
		return jdbcClient.sql("""
				SELECT o.id, o.legal_name, o.trade_name, o.tax_id, o.status, o.timezone,
				       o.default_currency, o.contact_email, o.contact_phone,
				       o.brand_name, o.logo_url, o.primary_color, o.accent_color, o.surface_color,
				       COUNT(DISTINCT b.id) AS branch_count,
				       COUNT(DISTINCT CASE WHEN b.status = 'ACTIVE' THEN b.id END) AS active_branch_count
				FROM organization o
				LEFT JOIN branch b ON b.organization_id = o.id AND b.deleted_at IS NULL
				WHERE o.deleted_at IS NULL
				  AND o.id <> '11111111-1111-1111-1111-111111111111'
				GROUP BY o.id, o.legal_name, o.trade_name, o.tax_id, o.status, o.timezone,
				         o.default_currency, o.contact_email, o.contact_phone,
				         o.brand_name, o.logo_url, o.primary_color, o.accent_color, o.surface_color
				ORDER BY LOWER(o.trade_name)
				""")
				.query(this::managedOrganizationSummary)
				.list();
	}

	public Optional<ManagedOrganizationSummary> findManagedOrganization(UUID organizationId) {
		return jdbcClient.sql("""
				SELECT o.id, o.legal_name, o.trade_name, o.tax_id, o.status, o.timezone,
				       o.default_currency, o.contact_email, o.contact_phone,
				       o.brand_name, o.logo_url, o.primary_color, o.accent_color, o.surface_color,
				       COUNT(DISTINCT b.id) AS branch_count,
				       COUNT(DISTINCT CASE WHEN b.status = 'ACTIVE' THEN b.id END) AS active_branch_count
				FROM organization o
				LEFT JOIN branch b ON b.organization_id = o.id AND b.deleted_at IS NULL
				WHERE o.id = :organizationId
				  AND o.deleted_at IS NULL
				GROUP BY o.id, o.legal_name, o.trade_name, o.tax_id, o.status, o.timezone,
				         o.default_currency, o.contact_email, o.contact_phone,
				         o.brand_name, o.logo_url, o.primary_color, o.accent_color, o.surface_color
				""")
				.param("organizationId", organizationId)
				.query(this::managedOrganizationSummary)
				.optional();
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
				.query(this::branchSummary)
				.list();
	}

	public List<BranchSummary> findBranchesForOrganization(UUID organizationId) {
		return jdbcClient.sql("""
				SELECT b.id, b.name, b.code, b.status, b.timezone, b.phone, 'SUPPORT' AS access_scope
				FROM branch b
				WHERE b.organization_id = :organizationId
				  AND b.deleted_at IS NULL
				ORDER BY b.name
				""")
				.param("organizationId", organizationId)
				.query(this::branchSummary)
				.list();
	}

	public Optional<BranchSummary> findBranch(UUID organizationId, UUID branchId) {
		return jdbcClient.sql("""
				SELECT b.id, b.name, b.code, b.status, b.timezone, b.phone, 'SUPPORT' AS access_scope
				FROM branch b
				WHERE b.organization_id = :organizationId
				  AND b.id = :branchId
				  AND b.deleted_at IS NULL
				""")
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.query(this::branchSummary)
				.optional();
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
				.query(this::branchSummary)
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

	public void updateOrganizationStatus(UUID organizationId, String status, UUID actorUserId, OffsetDateTime now) {
		jdbcClient.sql("""
				UPDATE organization
				SET status = :status,
				    updated_at = :updatedAt,
				    updated_by = :updatedBy,
				    version = version + 1
				WHERE id = :organizationId
				  AND deleted_at IS NULL
				""")
				.param("status", status)
				.param("updatedAt", now)
				.param("updatedBy", actorUserId)
				.param("organizationId", organizationId)
				.update();
	}

	private ManagedOrganizationSummary managedOrganizationSummary(java.sql.ResultSet resultSet, int rowNumber) throws java.sql.SQLException {
		return new ManagedOrganizationSummary(
				resultSet.getObject("id", UUID.class),
				resultSet.getString("legal_name"),
				resultSet.getString("trade_name"),
				resultSet.getString("tax_id"),
				resultSet.getString("status"),
				resultSet.getString("timezone"),
				resultSet.getString("default_currency"),
				resultSet.getString("contact_email"),
				resultSet.getString("contact_phone"),
				resultSet.getString("brand_name"),
				resultSet.getString("logo_url"),
				resultSet.getString("primary_color"),
				resultSet.getString("accent_color"),
				resultSet.getString("surface_color"),
				resultSet.getInt("branch_count"),
				resultSet.getInt("active_branch_count")
		);
	}

	private BranchSummary branchSummary(java.sql.ResultSet resultSet, int rowNumber) throws java.sql.SQLException {
		return new BranchSummary(
				resultSet.getObject("id", UUID.class),
				resultSet.getString("name"),
				resultSet.getString("code"),
				resultSet.getString("status"),
				resultSet.getString("timezone"),
				resultSet.getString("phone"),
				resultSet.getString("access_scope")
		);
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
			String brandName,
			String logoUrl,
			String primaryColor,
			String accentColor,
			String surfaceColor,
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
			String contactPhone,
			String brandName,
			String logoUrl,
			String primaryColor,
			String accentColor,
			String surfaceColor
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
