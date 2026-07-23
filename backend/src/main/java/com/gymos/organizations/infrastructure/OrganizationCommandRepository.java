package com.gymos.organizations.infrastructure;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class OrganizationCommandRepository {
	private final JdbcClient jdbcClient;

	public OrganizationCommandRepository(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	public void createOrganization(CreateOrganizationRecord record) {
		jdbcClient.sql("""
				INSERT INTO organization (
				    id, legal_name, trade_name, tax_id, status, timezone, default_currency,
				    contact_email, contact_phone, brand_name, logo_url, primary_color, accent_color, surface_color,
				    created_at, updated_at, created_by, updated_by, version
				)
				VALUES (
				    :id, :legalName, :tradeName, :taxId, 'ACTIVE', :timezone, :defaultCurrency,
				    :contactEmail, :contactPhone, :brandName, :logoUrl, :primaryColor, :accentColor, :surfaceColor,
				    :createdAt, :updatedAt, :createdBy, :updatedBy, 0
				)
				""")
				.param("id", record.organizationId())
				.param("legalName", record.legalName())
				.param("tradeName", record.tradeName())
				.param("taxId", record.taxId())
				.param("timezone", record.timezone())
				.param("defaultCurrency", record.defaultCurrency())
				.param("contactEmail", record.contactEmail())
				.param("contactPhone", record.contactPhone())
				.param("brandName", record.brandName())
				.param("logoUrl", record.logoUrl())
				.param("primaryColor", record.primaryColor())
				.param("accentColor", record.accentColor())
				.param("surfaceColor", record.surfaceColor())
				.param("createdAt", record.now())
				.param("updatedAt", record.now())
				.param("createdBy", record.userId())
				.param("updatedBy", record.userId())
				.update();
	}

	public void updateOrganization(UpdateOrganizationRecord record) {
		jdbcClient.sql("""
				UPDATE organization
				SET legal_name = :legalName,
				    trade_name = :tradeName,
				    tax_id = :taxId,
				    timezone = :timezone,
				    default_currency = :defaultCurrency,
				    contact_email = :contactEmail,
				    contact_phone = :contactPhone,
				    brand_name = :brandName,
				    logo_url = :logoUrl,
				    primary_color = :primaryColor,
				    accent_color = :accentColor,
				    surface_color = :surfaceColor,
				    updated_at = :updatedAt,
				    updated_by = :updatedBy,
				    version = version + 1
				WHERE id = :organizationId
				  AND deleted_at IS NULL
				""")
				.param("legalName", record.legalName())
				.param("tradeName", record.tradeName())
				.param("taxId", record.taxId())
				.param("timezone", record.timezone())
				.param("defaultCurrency", record.defaultCurrency())
				.param("contactEmail", record.contactEmail())
				.param("contactPhone", record.contactPhone())
				.param("brandName", record.brandName())
				.param("logoUrl", record.logoUrl())
				.param("primaryColor", record.primaryColor())
				.param("accentColor", record.accentColor())
				.param("surfaceColor", record.surfaceColor())
				.param("updatedAt", record.now())
				.param("updatedBy", record.userId())
				.param("organizationId", record.organizationId())
				.update();
	}

	public void createBranch(CreateBranchRecord record) {
		jdbcClient.sql("""
				INSERT INTO branch (
				    id, organization_id, name, code, timezone, status, phone,
				    created_at, updated_at, created_by, updated_by, version
				)
				VALUES (
				    :branchId, :organizationId, :name, :code, :timezone, 'ACTIVE', :phone,
				    :createdAt, :updatedAt, :createdBy, :updatedBy, 0
				)
				""")
				.param("branchId", record.branchId())
				.param("organizationId", record.organizationId())
				.param("name", record.name())
				.param("code", record.code())
				.param("timezone", record.timezone())
				.param("phone", record.phone())
				.param("createdAt", record.now())
				.param("updatedAt", record.now())
				.param("createdBy", record.userId())
				.param("updatedBy", record.userId())
				.update();
	}

	public void updateBranch(UpdateBranchRecord record) {
		jdbcClient.sql("""
				UPDATE branch
				SET name = :name,
				    code = :code,
				    timezone = :timezone,
				    phone = :phone,
				    updated_at = :updatedAt,
				    updated_by = :updatedBy,
				    version = version + 1
				WHERE id = :branchId
				  AND organization_id = :organizationId
				  AND deleted_at IS NULL
				""")
				.param("name", record.name())
				.param("code", record.code())
				.param("timezone", record.timezone())
				.param("phone", record.phone())
				.param("updatedAt", record.now())
				.param("updatedBy", record.userId())
				.param("branchId", record.branchId())
				.param("organizationId", record.organizationId())
				.update();
	}

	public void grantUserBranchAccess(UUID userId, UUID branchId, String accessScope) {
		jdbcClient.sql("""
				INSERT INTO user_branch_access (user_id, branch_id, access_scope)
				VALUES (:userId, :branchId, :accessScope)
				ON CONFLICT DO NOTHING
				""")
				.param("userId", userId)
				.param("branchId", branchId)
				.param("accessScope", accessScope)
				.update();
	}

	public record CreateOrganizationRecord(
			UUID organizationId,
			UUID userId,
			String legalName,
			String tradeName,
			String taxId,
			String timezone,
			String defaultCurrency,
			String contactEmail,
			String contactPhone,
			String brandName,
			String logoUrl,
			String primaryColor,
			String accentColor,
			String surfaceColor,
			OffsetDateTime now
	) {
	}

	public record UpdateOrganizationRecord(
			UUID organizationId,
			UUID userId,
			String legalName,
			String tradeName,
			String taxId,
			String timezone,
			String defaultCurrency,
			String contactEmail,
			String contactPhone,
			String brandName,
			String logoUrl,
			String primaryColor,
			String accentColor,
			String surfaceColor,
			OffsetDateTime now
	) {
	}

	public record CreateBranchRecord(
			UUID branchId,
			UUID organizationId,
			UUID userId,
			String name,
			String code,
			String timezone,
			String phone,
			OffsetDateTime now
	) {
	}

	public record UpdateBranchRecord(
			UUID branchId,
			UUID organizationId,
			UUID userId,
			String name,
			String code,
			String timezone,
			String phone,
			OffsetDateTime now
	) {
	}
}
