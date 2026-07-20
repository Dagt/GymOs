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
		if (principal.platformAdmin()) {
			return organizationQueryRepository.findAllOrganizations();
		}
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
		if (principal.platformAdmin() && principal.supportMode()) {
			return organizationQueryRepository.findBranchesForOrganization(principal.organizationId());
		}
		return organizationQueryRepository.findBranchesForUser(principal.userId(), principal.organizationId());
	}

	@Transactional(readOnly = true)
	public List<BranchSummary> branchesForOrganization(GymOsPrincipal principal, UUID organizationId) {
		requirePrincipal(principal);
		if (!principal.platformAdmin()) {
			throw new BusinessException("PLATFORM_ACCESS_REQUIRED", "Solo Admin GymOS puede consultar sucursales por gimnasio.");
		}
		return organizationQueryRepository.findBranchesForOrganization(organizationId);
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
		if (hasPartialBranchRequest(request)) {
			throw new BusinessException("BRANCH_DATA_INCOMPLETE", "Captura nombre y codigo de sucursal, o crea la sucursal despues.");
		}
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
			if (hasBranchRequest(request)) {
				UUID branchId = UUID.randomUUID();
				jdbcClient.sql("""
					INSERT INTO branch (
						    id, organization_id, name, code, timezone, status, phone,
						    brand_name, logo_url, primary_color, accent_color, surface_color,
						    created_at, updated_at, created_by, updated_by, version
						)
						VALUES (
						    :branchId, :organizationId, :name, :code, :timezone, 'ACTIVE', :phone,
						    :brandName, :logoUrl, :primaryColor, :accentColor, :surfaceColor,
						    :createdAt, :updatedAt, :createdBy, :updatedBy, 0
						)
						""")
						.param("branchId", branchId)
						.param("organizationId", organizationId)
						.param("name", request.firstBranchName().trim())
						.param("code", request.firstBranchCode().trim().toUpperCase())
						.param("timezone", request.timezone().trim())
						.param("phone", blankToNull(request.firstBranchPhone()))
						.param("brandName", request.firstBranchName().trim())
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
						INSERT INTO user_branch_access (user_id, branch_id, access_scope)
						VALUES (:userId, :branchId, 'ADMIN')
						ON CONFLICT DO NOTHING
						""")
						.param("userId", principal.userId())
						.param("branchId", branchId)
						.update();
			}
		} catch (DuplicateKeyException exception) {
			throw new BusinessException("ORGANIZATION_ALREADY_EXISTS", "Ya existe un gimnasio con esos datos.");
		}
		auditService.recordSystem(organizationId, null, principal.userId(), "ORGANIZATION_CREATED", "ORGANIZATION", organizationId, "ADMIN", null, correlationId);
		return organizationQueryRepository.findManagedOrganization(organizationId)
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
					    brand_name, logo_url, primary_color, accent_color, surface_color,
					    created_at, updated_at, created_by, updated_by, version
					)
					VALUES (
					    :branchId, :organizationId, :name, :code, :timezone, 'ACTIVE', :phone,
					    :brandName, :logoUrl, :primaryColor, :accentColor, :surfaceColor,
					    :createdAt, :updatedAt, :createdBy, :updatedBy, 0
					)
					""")
					.param("branchId", branchId)
					.param("organizationId", principal.organizationId())
					.param("name", request.name().trim())
					.param("code", request.code().trim().toUpperCase())
					.param("timezone", request.timezone().trim())
					.param("phone", blankToNull(request.phone()))
					.param("brandName", defaultBranchBrandName(request.brandName(), request.name()))
					.param("logoUrl", blankToNull(request.logoUrl()))
					.param("primaryColor", blankToNull(request.primaryColor()))
					.param("accentColor", blankToNull(request.accentColor()))
					.param("surfaceColor", blankToNull(request.surfaceColor()))
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
		if (principal.platformAdmin() && principal.supportMode()) {
			return organizationQueryRepository.findBranch(principal.organizationId(), branchId)
					.orElseThrow(() -> new BusinessException("BRANCH_NOT_FOUND", "La sucursal no esta disponible."));
		}
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
		if (!principal.platformAdmin() && !organizationQueryRepository.userHasBranchAccess(principal.userId(), principal.organizationId(), branchId)) {
			throw new BusinessException("BRANCH_ACCESS_DENIED", "No tienes acceso a la sucursal solicitada.");
		}
		OffsetDateTime now = OffsetDateTime.now();
		jdbcClient.sql("""
				UPDATE branch
				SET name = :name,
				    code = :code,
				    timezone = :timezone,
				    phone = :phone,
				    brand_name = :brandName,
				    logo_url = :logoUrl,
				    primary_color = :primaryColor,
				    accent_color = :accentColor,
				    surface_color = :surfaceColor,
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
				.param("brandName", defaultBranchBrandName(request.brandName(), request.name()))
				.param("logoUrl", blankToNull(request.logoUrl()))
				.param("primaryColor", blankToNull(request.primaryColor()))
				.param("accentColor", blankToNull(request.accentColor()))
				.param("surfaceColor", blankToNull(request.surfaceColor()))
				.param("updatedAt", now)
				.param("updatedBy", principal.userId())
				.param("branchId", branchId)
				.param("organizationId", principal.organizationId())
				.update();
		auditService.record(principal, "BRANCH_UPDATED", "BRANCH", branchId, "RECEPTION", null, correlationId);
		if (principal.platformAdmin() && principal.supportMode()) {
			return organizationQueryRepository.findBranch(principal.organizationId(), branchId)
					.orElseThrow(() -> new BusinessException("BRANCH_NOT_FOUND", "La sucursal no esta disponible."));
		}
		return organizationQueryRepository.findBranchForUser(principal.userId(), principal.organizationId(), branchId)
				.orElseThrow(() -> new BusinessException("BRANCH_NOT_FOUND", "La sucursal no esta disponible."));
	}

	@Transactional
	public ManagedOrganizationSummary updateOrganizationStatus(
			GymOsPrincipal principal,
			UUID organizationId,
			String status,
			String correlationId
	) {
		requirePrincipal(principal);
		if (!principal.platformAdmin()) {
			throw new BusinessException("PLATFORM_ACCESS_REQUIRED", "Solo Admin GymOS puede cambiar el estado de un gimnasio cliente.");
		}
		if (organizationId.equals(UUID.fromString("11111111-1111-1111-1111-111111111111"))) {
			throw new BusinessException("PLATFORM_ORGANIZATION_LOCKED", "La organizacion tecnica de plataforma no se puede suspender.");
		}
		String normalizedStatus = normalizeOrganizationStatus(status);
		organizationQueryRepository.updateOrganizationStatus(organizationId, normalizedStatus, principal.userId(), OffsetDateTime.now());
		auditService.recordSystem(organizationId, null, principal.userId(), "ORGANIZATION_STATUS_UPDATED", "ORGANIZATION", organizationId, "PLATFORM_ADMIN", normalizedStatus, correlationId);
		return organizationQueryRepository.findManagedOrganization(organizationId)
				.orElseThrow(() -> new BusinessException("ORGANIZATION_NOT_FOUND", "El gimnasio no esta disponible."));
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

	private String defaultBranchBrandName(String brandName, String branchName) {
		String value = blankToNull(brandName);
		return value == null ? branchName.trim() : value;
	}

	private String defaultColor(String value, String fallback) {
		String normalized = blankToNull(value);
		return normalized == null ? fallback : normalized;
	}

	private boolean hasBranchRequest(CreateOrganizationCommand request) {
		return blankToNull(request.firstBranchName()) != null
				&& blankToNull(request.firstBranchCode()) != null;
	}

	private boolean hasPartialBranchRequest(CreateOrganizationCommand request) {
		boolean hasName = blankToNull(request.firstBranchName()) != null;
		boolean hasCode = blankToNull(request.firstBranchCode()) != null;
		return hasName != hasCode;
	}

	private String normalizeOrganizationStatus(String status) {
		String normalized = status == null ? "" : status.trim().toUpperCase();
		if (!List.of("ACTIVE", "SUSPENDED", "INACTIVE").contains(normalized)) {
			throw new BusinessException("ORGANIZATION_STATUS_NOT_AVAILABLE", "Usa un estado valido: ACTIVE, SUSPENDED o INACTIVE.");
		}
		return normalized;
	}
}
