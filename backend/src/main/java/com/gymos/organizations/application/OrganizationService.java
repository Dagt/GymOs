package com.gymos.organizations.application;

import com.gymos.audit.api.AuditService;
import com.gymos.common.api.BusinessException;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.organizations.application.command.CreateBranchCommand;
import com.gymos.organizations.application.command.CreateOrganizationCommand;
import com.gymos.organizations.application.command.UpdateBranchCommand;
import com.gymos.organizations.application.command.UpdateOrganizationCommand;
import com.gymos.organizations.infrastructure.OrganizationCommandRepository;
import com.gymos.organizations.infrastructure.OrganizationCommandRepository.CreateBranchRecord;
import com.gymos.organizations.infrastructure.OrganizationCommandRepository.CreateOrganizationRecord;
import com.gymos.organizations.infrastructure.OrganizationCommandRepository.UpdateBranchRecord;
import com.gymos.organizations.infrastructure.OrganizationCommandRepository.UpdateOrganizationRecord;
import com.gymos.organizations.infrastructure.OrganizationQueryRepository;
import com.gymos.organizations.infrastructure.OrganizationQueryRepository.BranchSummary;
import com.gymos.organizations.infrastructure.OrganizationQueryRepository.ManagedOrganizationSummary;
import com.gymos.organizations.infrastructure.OrganizationQueryRepository.OrganizationSummary;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrganizationService {
	private final OrganizationCommandRepository commandRepository;
	private final OrganizationQueryRepository organizationQueryRepository;
	private final AuditService auditService;

	public OrganizationService(
			OrganizationCommandRepository commandRepository,
			OrganizationQueryRepository organizationQueryRepository,
			AuditService auditService
	) {
		this.commandRepository = commandRepository;
		this.organizationQueryRepository = organizationQueryRepository;
		this.auditService = auditService;
	}

	@Transactional(readOnly = true)
	public List<ManagedOrganizationSummary> organizations(GymOsPrincipal principal) {
		requirePrincipal(principal);
		if (principal.platformAdmin()) {
			return organizationQueryRepository.findAllOrganizations();
		}
		if (principal.platformIdentity()) {
			throw new BusinessException("PLATFORM_SUPPORT_SCOPE_ACTIVE", "Vuelve al panel de plataforma para consultar la cartera.");
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
		if (principal.platformSupport()) {
			return organizationQueryRepository.findBranchesForOrganization(principal.organizationId());
		}
		return organizationQueryRepository.findBranchesForUser(principal.userId(), principal.organizationId());
	}

	@Transactional(readOnly = true)
	public List<BranchSummary> allOrganizationBranches(GymOsPrincipal principal) {
		requirePrincipal(principal);
		return organizationQueryRepository.findBranchesForOrganization(principal.organizationId());
	}

	@Transactional(readOnly = true)
	public List<BranchSummary> branchesForOrganization(GymOsPrincipal principal, UUID organizationId) {
		requirePrincipal(principal);
		if (!principal.platformIdentity()
				|| (principal.supportMode() && !organizationId.equals(principal.organizationId()))) {
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
		if (principal.platformIdentity()) {
			throw new BusinessException(
					"PLATFORM_CUSTOMER_PROVISIONING_REQUIRED",
					"Crea el gimnasio y su dueño desde la operacion de clientes de plataforma.");
		}
		OffsetDateTime now = OffsetDateTime.now();
		UUID organizationId = UUID.randomUUID();
		if (hasPartialBranchRequest(request)) {
			throw new BusinessException("BRANCH_DATA_INCOMPLETE", "Captura nombre y codigo de sucursal, o crea la sucursal despues.");
		}
		try {
			commandRepository.createOrganization(new CreateOrganizationRecord(
					organizationId,
					principal.userId(),
					request.legalName().trim(),
					request.tradeName().trim(),
					blankToNull(request.taxId()),
					request.timezone().trim(),
					request.defaultCurrency().trim().toUpperCase(),
					blankToNull(request.contactEmail()),
					blankToNull(request.contactPhone()),
					defaultBrandName(request.brandName(), request.tradeName()),
					blankToNull(request.logoUrl()),
					defaultColor(request.primaryColor(), "#093D3A"),
					defaultColor(request.accentColor(), "#18C8B2"),
					defaultColor(request.surfaceColor(), "#F2F8F7"),
					now
			));
			if (hasBranchRequest(request)) {
				UUID branchId = UUID.randomUUID();
				commandRepository.createBranch(new CreateBranchRecord(
						branchId,
						organizationId,
						principal.userId(),
						request.firstBranchName().trim(),
						request.firstBranchCode().trim().toUpperCase(),
						request.timezone().trim(),
						blankToNull(request.firstBranchPhone()),
						now
				));
				commandRepository.grantUserBranchAccess(principal.userId(), branchId, "ADMIN");
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
		commandRepository.updateOrganization(new UpdateOrganizationRecord(
				principal.organizationId(),
				principal.userId(),
				request.legalName().trim(),
				request.tradeName().trim(),
				blankToNull(request.taxId()),
				request.timezone().trim(),
				request.defaultCurrency().trim().toUpperCase(),
				blankToNull(request.contactEmail()),
				blankToNull(request.contactPhone()),
				defaultBrandName(request.brandName(), request.tradeName()),
				blankToNull(request.logoUrl()),
				defaultColor(request.primaryColor(), "#093D3A"),
				defaultColor(request.accentColor(), "#18C8B2"),
				defaultColor(request.surfaceColor(), "#F2F8F7"),
				now
		));
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
			commandRepository.createBranch(new CreateBranchRecord(
					branchId,
					principal.organizationId(),
					principal.userId(),
					request.name().trim(),
					request.code().trim().toUpperCase(),
					request.timezone().trim(),
					blankToNull(request.phone()),
					now
			));
		} catch (DuplicateKeyException exception) {
			throw new BusinessException("BRANCH_CODE_ALREADY_EXISTS", "Ya existe una sucursal con ese codigo.");
		}
		if (principal.tenantIdentity()) {
			commandRepository.grantUserBranchAccess(principal.userId(), branchId, "ADMIN");
		}
		auditService.record(principal, "BRANCH_CREATED", "BRANCH", branchId, "ADMIN", null, correlationId);
		if (principal.platformSupport()) {
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
		if (!principal.platformSupport()
				&& !organizationQueryRepository.userHasBranchAccess(principal.userId(), principal.organizationId(), branchId)) {
			throw new BusinessException("BRANCH_ACCESS_DENIED", "No tienes acceso a la sucursal solicitada.");
		}
		OffsetDateTime now = OffsetDateTime.now();
		commandRepository.updateBranch(new UpdateBranchRecord(
				branchId,
				principal.organizationId(),
				principal.userId(),
				request.name().trim(),
				request.code().trim().toUpperCase(),
				request.timezone().trim(),
				blankToNull(request.phone()),
				now
		));
		auditService.record(principal, "BRANCH_UPDATED", "BRANCH", branchId, "RECEPTION", null, correlationId);
		if (principal.platformSupport()) {
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
		if (organizationQueryRepository.isDemoOrganization(organizationId)) {
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
