package com.gymos.organizations.application;

import com.gymos.audit.api.AuditService;
import com.gymos.common.api.BusinessException;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.organizations.application.command.UpdateBranchCommand;
import com.gymos.organizations.application.command.UpdateOrganizationCommand;
import com.gymos.organizations.infrastructure.OrganizationQueryRepository;
import com.gymos.organizations.infrastructure.OrganizationQueryRepository.BranchSummary;
import com.gymos.organizations.infrastructure.OrganizationQueryRepository.OrganizationSummary;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
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
				.param("updatedAt", now)
				.param("updatedBy", principal.userId())
				.param("organizationId", principal.organizationId())
				.update();
		auditService.record(principal, "ORGANIZATION_UPDATED", "ORGANIZATION", principal.organizationId(), "RECEPTION", null, correlationId);
		return current(principal);
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
}
