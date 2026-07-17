package com.gymos.organizations.application;

import com.gymos.audit.api.AuditService;
import com.gymos.common.api.BusinessException;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.organizations.application.command.CreateBranchCommand;
import com.gymos.organizations.application.command.CreateOrganizationCommand;
import com.gymos.organizations.application.command.UpdateBranchCommand;
import com.gymos.organizations.application.command.UpdateOrganizationCommand;
import com.gymos.organizations.infrastructure.OrganizationQueryRepository;
import com.gymos.organizations.infrastructure.OrganizationQueryRepository.BranchSummary;
import com.gymos.organizations.infrastructure.OrganizationQueryRepository.ManagedOrganizationSummary;
import com.gymos.organizations.infrastructure.OrganizationQueryRepository.OrganizationSummary;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrganizationService {
	private final JdbcClient jdbcClient;
	private final OrganizationQueryRepository organizationQueryRepository;
	private final AuditService auditService;

	public OrganizationService(
			JdbcClient jdbcClient,
			OrganizationQueryRepository organizationQueryRepository,
			AuditService auditService
	) {
		this.jdbcClient = jdbcClient;
		this.organizationQueryRepository = organizationQueryRepository;
		this.auditService = auditService;
	}

	@Transactional(readOnly = true)
	public List<ManagedOrganizationSummary> organizations(GymOsPrincipal principal) {
		requirePrincipal(principal);
		return organizationQueryRepository.findOrganizationsForUser(principal.userId());
	}

	@Transactional(readOnly = true)
	public OrganizationSummary current(GymOsPrincipal principal) {
		requirePrincipal(principal);
		return organizationQueryRepository.findCurrentOrganization(principal.organizationId())
				.orElseThrow(() -> new BusinessException(
						"ORGANIZATION_NOT_FOUND",
						"La organizacion de la sesion no esta disponible."
				));
	}

	@Transactional(readOnly = true)
	public List<BranchSummary> branches(GymOsPrincipal principal) {
		requirePrincipal(principal);
		return organizationQueryRepository.findBranchesForUser(principal.userId(), principal.organizationId());
	}

	@Transactional
	public ManagedOrganizationSummary createOrganization(
			GymOsPrincipal principal,
			CreateOrganizationCommand request,
			String correlationId
	) {
		requirePrincipal(principal);
		OffsetDateTime now = OffsetDateTime.now();
		UUID organizationId = UUID.randomUUID();
		UUID branchId = UUID.randomUUID();
		try {
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
					.param("id", organizationId)
					.param("legalName", request.legalName().trim())
					.param("tradeName", request.tradeName().trim())
					.param("taxId", blankToNull(request.taxId()))
					.param("timezone", request.timezone().trim())
					.param("defaultCurrency", request.defaultCurrency().trim().toUpperCase())
					.param("contactEmail", blankToNull(request.contactEmail()))
					.param("contactPhone", blankToNull(request.contactPhone()))
					.param("brandName", defaultBrandName(request.brandName(), request.tradeName()))
					.param("logoUrl", blankToNull(request.logoUrl()))
					.param("primaryColor", defaultColor(request.primaryColor(), "#164E63"))
					.param("accentColor", defaultColor(request.accentColor(), "#F59E0B"))
					.param("surfaceColor", defaultColor(request.surfaceColor(), "#F4F7FB"))
					.param("createdAt", now)
					.param("updatedAt", now)
					.param("createdBy", principal.userId())
					.param("updatedBy", principal.userId())
					.update();
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
					.param("branchId", branchId)
					.param("organizationId", organizationId)
					.param("name", request.firstBranchName().trim())
					.param("code", request.firstBranchCode().trim().toUpperCase())
					.param("timezone", request.timezone().trim())
					.param("phone", blankToNull(request.firstBranchPhone()))
					.param("createdAt", now)
					.param("updatedAt", now)
					.param("createdBy", principal.userId())
					.param("updatedBy", principal.userId())
					.update();
		} catch (DuplicateKeyException exception) {
			throw new BusinessException("ORGANIZATION_ALREADY_EXISTS", "Ya existe un gimnasio con esos datos.");
		}
		jdbcClient.sql("""
				INSERT INTO user_branch_access (user_id, branch_id, access_scope)
				VALUES (:userId, :branchId, 'ADMIN')
				ON CONFLICT DO NOTHING
				""")
				.param("userId", principal.userId())
				.param("branchId", branchId)
				.update();
		auditService.recordSystem(organizationId, branchId, principal.userId(), "ORGANIZATION_CREATED", "ORGANIZATION", organizationId, "ADMIN", null, correlationId);
		return organizationQueryRepository.findOrganizationsForUser(principal.userId()).stream()
				.filter(organization -> organization.id().equals(organizationId))
				.findFirst()
				.orElseThrow(() -> new BusinessException("ORGANIZATION_NOT_FOUND", "El gimnasio creado no esta disponible."));
	}

	@Transactional
	public OrganizationSummary updateOrganization(
			GymOsPrincipal principal,
			UpdateOrganizationCommand request,
			String correlationId
	) {
		requirePrincipal(principal);
		OffsetDateTime now = OffsetDateTime.now();
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
				.param("legalName", request.legalName().trim())
				.param("tradeName", request.tradeName().trim())
				.param("taxId", blankToNull(request.taxId()))
				.param("timezone", request.timezone().trim())
				.param("defaultCurrency", request.defaultCurrency().trim().toUpperCase())
				.param("contactEmail", blankToNull(request.contactEmail()))
				.param("contactPhone", blankToNull(request.contactPhone()))
				.param("brandName", defaultBrandName(request.brandName(), request.tradeName()))
				.param("logoUrl", blankToNull(request.logoUrl()))
				.param("primaryColor", defaultColor(request.primaryColor(), "#164E63"))
				.param("accentColor", defaultColor(request.accentColor(), "#F59E0B"))
				.param("surfaceColor", defaultColor(request.surfaceColor(), "#F4F7FB"))
				.param("updatedAt", now)
				.param("updatedBy", principal.userId())
				.param("organizationId", principal.organizationId())
				.update();
		auditService.record(principal, "ORGANIZATION_UPDATED", "ORGANIZATION", principal.organizationId(), "RECEPTION", null, correlationId);
		return current(principal);
	}

	@Transactional
	public BranchSummary createBranch(
			GymOsPrincipal principal,
			CreateBranchCommand request,
			String correlationId
	) {
		requirePrincipal(principal);
		OffsetDateTime now = OffsetDateTime.now();
		UUID branchId = UUID.randomUUID();
		try {
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
					.param("branchId", branchId)
					.param("organizationId", principal.organizationId())
					.param("name", request.name().trim())
					.param("code", request.code().trim().toUpperCase())
					.param("timezone", request.timezone().trim())
					.param("phone", blankToNull(request.phone()))
					.param("createdAt", now)
					.param("updatedAt", now)
					.param("createdBy", principal.userId())
					.param("updatedBy", principal.userId())
					.update();
		} catch (DuplicateKeyException exception) {
			throw new BusinessException("BRANCH_CODE_ALREADY_EXISTS", "Ya existe una sucursal con ese codigo.");
		}
		jdbcClient.sql("""
				INSERT INTO user_branch_access (user_id, branch_id, access_scope)
				VALUES (:userId, :branchId, 'ADMIN')
				ON CONFLICT DO NOTHING
				""")
				.param("userId", principal.userId())
				.param("branchId", branchId)
				.update();
		auditService.record(principal, "BRANCH_CREATED", "BRANCH", branchId, "ADMIN", null, correlationId);
		return organizationQueryRepository.findBranchForUser(principal.userId(), principal.organizationId(), branchId)
				.orElseThrow(() -> new BusinessException("BRANCH_NOT_FOUND", "La sucursal no esta disponible."));
	}

	@Transactional
	public BranchSummary updateBranch(
			GymOsPrincipal principal,
			UUID branchId,
			UpdateBranchCommand request,
			String correlationId
	) {
		requirePrincipal(principal);
		if (!organizationQueryRepository.userHasBranchAccess(principal.userId(), principal.organizationId(), branchId)) {
			throw new BusinessException("BRANCH_ACCESS_DENIED", "No tienes acceso a la sucursal solicitada.");
		}
		OffsetDateTime now = OffsetDateTime.now();
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
				.param("name", request.name().trim())
				.param("code", request.code().trim().toUpperCase())
				.param("timezone", request.timezone().trim())
				.param("phone", blankToNull(request.phone()))
				.param("updatedAt", now)
				.param("updatedBy", principal.userId())
				.param("branchId", branchId)
				.param("organizationId", principal.organizationId())
				.update();
		auditService.record(principal, "BRANCH_UPDATED", "BRANCH", branchId, "RECEPTION", null, correlationId);
		return organizationQueryRepository.findBranchForUser(principal.userId(), principal.organizationId(), branchId)
				.orElseThrow(() -> new BusinessException("BRANCH_NOT_FOUND", "La sucursal no esta disponible."));
	}

	private void requirePrincipal(GymOsPrincipal principal) {
		if (principal == null) {
			throw new BusinessException("AUTHENTICATION_REQUIRED", "Debes iniciar sesion para continuar.");
		}
	}

	private String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

	private String defaultBrandName(String brandName, String tradeName) {
		String value = blankToNull(brandName);
		return value == null ? tradeName.trim() : value;
	}

	private String defaultColor(String value, String fallback) {
		String normalized = blankToNull(value);
		return normalized == null ? fallback : normalized;
	}
}
