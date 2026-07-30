package com.gymos.benefits.application;

import com.gymos.audit.api.AuditService;
import com.gymos.benefits.application.command.AssignClientBenefitCommand;
import com.gymos.benefits.application.command.CreateBenefitCommand;
import com.gymos.benefits.infrastructure.BenefitRepository;
import com.gymos.benefits.infrastructure.BenefitRepository.BenefitSummary;
import com.gymos.benefits.infrastructure.BenefitRepository.ClientBenefitSummary;
import com.gymos.common.api.BusinessException;
import com.gymos.common.security.GymOsPrincipal;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BenefitService {
	private final BenefitRepository benefitRepository;
	private final AuditService auditService;

	public BenefitService(BenefitRepository benefitRepository, AuditService auditService) {
		this.benefitRepository = benefitRepository;
		this.auditService = auditService;
	}

	@Transactional(readOnly = true)
	public List<BenefitSummary> benefits(GymOsPrincipal principal) {
		requirePrincipal(principal);
		return benefitRepository.benefitsForBranch(principal.organizationId(), principal.branchId());
	}

	@Transactional(readOnly = true)
	public List<ClientBenefitSummary> clientBenefits(GymOsPrincipal principal, UUID clientId) {
		requirePrincipal(principal);
		if (!benefitRepository.clientExists(principal.organizationId(), clientId)) {
			throw new BusinessException("CLIENT_NOT_FOUND", "El cliente no esta disponible.");
		}
		return benefitRepository.clientBenefits(principal.organizationId(), principal.branchId(), clientId);
	}

	@Transactional
	public BenefitSummary createBenefit(GymOsPrincipal principal, CreateBenefitCommand command, String correlationId) {
		requirePrincipal(principal);
		UUID benefitBranchId = command.branchId() == null ? principal.branchId() : command.branchId();
		if (benefitBranchId == null) {
			throw new BusinessException("BRANCH_REQUIRED", "Selecciona una sucursal para crear el beneficio.");
		}
		validatePlanLinks(principal, command, benefitBranchId);
		if (benefitRepository.codeExists(principal.organizationId(), benefitBranchId, command.code())) {
			throw new BusinessException("BENEFIT_ALREADY_EXISTS", "Ya existe un beneficio con ese codigo.");
		}
		UUID benefitId = benefitRepository.createBenefit(principal, benefitBranchId, command);
		benefitRepository.replaceMembershipPlans(principal, benefitId, command.planIds());
		auditService.record(principal, "BENEFIT_CREATED", "BENEFIT", benefitId, "BACKOFFICE", null, correlationId);
		return benefitRepository.benefit(principal.organizationId(), benefitId)
				.orElseThrow(() -> new BusinessException("BENEFIT_NOT_FOUND", "El beneficio no esta disponible."));
	}

	private void validatePlanLinks(
			GymOsPrincipal principal,
			CreateBenefitCommand command,
			UUID benefitBranchId
	) {
		boolean accessBenefit = "ACCESS".equalsIgnoreCase(command.benefitType());
		if (!accessBenefit && !command.planIds().isEmpty()) {
			throw new BusinessException("BENEFIT_PLAN_LINK_NOT_ALLOWED", "Solo los beneficios de acceso pueden vincularse con planes.");
		}
		if (accessBenefit && command.planIds().isEmpty()) {
			throw new BusinessException("BENEFIT_PLAN_REQUIRED", "Selecciona al menos un plan para el beneficio de acceso.");
		}
		if (!benefitRepository.membershipPlansExist(principal.organizationId(), command.planIds())) {
			throw new BusinessException("MEMBERSHIP_PLAN_NOT_AVAILABLE", "Uno o mas planes no existen o no pertenecen a la organizacion.");
		}
		validateDiscountDefinition(command, accessBenefit);
		if (command.validFrom() != null && command.validUntil() != null
				&& command.validUntil().isBefore(command.validFrom())) {
			throw new BusinessException("BENEFIT_DATE_RANGE_INVALID", "La vigencia final no puede ser anterior a la inicial.");
		}
		if (!benefitRepository.branchExists(principal.organizationId(), benefitBranchId)) {
			throw new BusinessException("BRANCH_NOT_AVAILABLE", "La sucursal del beneficio no pertenece a la organizacion.");
		}
	}

	private void validateDiscountDefinition(CreateBenefitCommand command, boolean accessBenefit) {
		String discountType = command.discountType().trim().toUpperCase();
		if (accessBenefit) {
			if (!"NONE".equals(discountType)
					|| positive(command.discountPercent())
					|| positive(command.discountValueCents())) {
				throw new BusinessException(
						"BENEFIT_ACCESS_DISCOUNT_NOT_ALLOWED",
						"Un beneficio de acceso no puede modificar el precio del plan."
				);
			}
			return;
		}
		switch (discountType) {
			case "PERCENT" -> {
				if (!positive(command.discountPercent())) {
					throw new BusinessException("BENEFIT_DISCOUNT_INVALID", "El porcentaje de descuento debe ser mayor a cero.");
				}
			}
			case "FIXED", "SPECIAL_PRICE" -> {
				if (!positive(command.discountValueCents())) {
					throw new BusinessException("BENEFIT_DISCOUNT_INVALID", "El importe del beneficio debe ser mayor a cero.");
				}
			}
			default -> throw new BusinessException("BENEFIT_DISCOUNT_INVALID", "Selecciona una regla de precio valida.");
		}
	}

	@Transactional
	public ClientBenefitSummary assignToClient(
			GymOsPrincipal principal,
			AssignClientBenefitCommand command,
			String correlationId
	) {
		requirePrincipal(principal);
		if (!benefitRepository.clientExists(principal.organizationId(), command.clientId())) {
			throw new BusinessException("CLIENT_NOT_FOUND", "El cliente no esta disponible.");
		}
		BenefitSummary benefit = benefitRepository.benefit(principal.organizationId(), command.benefitId())
				.orElseThrow(() -> new BusinessException("BENEFIT_NOT_FOUND", "El beneficio no esta disponible."));
		if (!"ACTIVE".equals(benefit.status())) {
			throw new BusinessException("BENEFIT_NOT_ELIGIBLE", "El beneficio no esta activo.");
		}
		UUID assignmentBranchId = command.branchId() == null ? principal.branchId() : command.branchId();
		if (assignmentBranchId == null
				|| !benefitRepository.branchExists(principal.organizationId(), assignmentBranchId)) {
			throw new BusinessException("BRANCH_NOT_AVAILABLE", "La sucursal de la asignacion no pertenece a la organizacion.");
		}
		if (benefit.branchId() != null && !benefit.branchId().equals(assignmentBranchId)) {
			throw new BusinessException("BENEFIT_BRANCH_MISMATCH", "El beneficio no esta disponible en la sucursal seleccionada.");
		}
		if ("ACCESS".equalsIgnoreCase(benefit.benefitType())) {
			throw new BusinessException(
					"BENEFIT_ACCESS_INHERITED",
					"Los beneficios de acceso se heredan del plan y no se asignan manualmente."
			);
		}
		if (command.validUntil() == null) {
			throw new BusinessException("BENEFIT_ASSIGNMENT_EXPIRATION_REQUIRED", "El beneficio de precio necesita fecha de expiracion.");
		}
		if (command.validFrom() != null && command.validUntil().isBefore(command.validFrom())) {
			throw new BusinessException("BENEFIT_DATE_RANGE_INVALID", "La vigencia final no puede ser anterior a la inicial.");
		}
		UUID clientBenefitId = benefitRepository.assignClientBenefit(principal, command);
		auditService.record(principal, "CLIENT_BENEFIT_ASSIGNED", "CLIENT_BENEFIT", clientBenefitId, "BACKOFFICE", null, correlationId);
		return benefitRepository.clientBenefit(principal.organizationId(), clientBenefitId)
				.orElseThrow(() -> new BusinessException("CLIENT_BENEFIT_NOT_FOUND", "El beneficio asignado no esta disponible."));
	}

	@Transactional
	public ClientBenefitSummary verify(GymOsPrincipal principal, UUID clientBenefitId, String correlationId) {
		requirePrincipal(principal);
		ClientBenefitSummary current = benefitRepository.clientBenefit(principal.organizationId(), clientBenefitId)
				.orElseThrow(() -> new BusinessException("CLIENT_BENEFIT_NOT_FOUND", "El beneficio asignado no esta disponible."));
		if (principal.branchId() == null || !principal.branchId().equals(current.branchId())) {
			throw new BusinessException(
					"CLIENT_BENEFIT_BRANCH_MISMATCH",
					"El beneficio asignado no pertenece a la sucursal seleccionada."
			);
		}
		if ("EXPIRED".equals(current.status()) || "REJECTED".equals(current.status())) {
			throw new BusinessException("BENEFIT_EXPIRED", "El beneficio no se puede verificar en su estado actual.");
		}
		benefitRepository.verifyClientBenefit(principal, clientBenefitId);
		auditService.record(principal, "CLIENT_BENEFIT_VERIFIED", "CLIENT_BENEFIT", clientBenefitId, "BACKOFFICE", null, correlationId);
		return benefitRepository.clientBenefit(principal.organizationId(), clientBenefitId)
				.orElseThrow(() -> new BusinessException("CLIENT_BENEFIT_NOT_FOUND", "El beneficio asignado no esta disponible."));
	}

	private void requirePrincipal(GymOsPrincipal principal) {
		if (principal == null) {
			throw new BusinessException("AUTHENTICATION_REQUIRED", "Debes iniciar sesion para continuar.");
		}
	}

	private boolean positive(Number value) {
		return value != null && value.doubleValue() > 0;
	}
}
