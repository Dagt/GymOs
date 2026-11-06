package com.gymos.benefits.application;

import com.gymos.audit.api.AuditService;
import com.gymos.benefits.api.events.AccessBenefitConfigurationChangedV1;
import com.gymos.benefits.application.command.AssignClientBenefitCommand;
import com.gymos.benefits.application.command.CreateBenefitCommand;
import com.gymos.benefits.application.command.UpdateBenefitCommand;
import com.gymos.benefits.infrastructure.BenefitRepository;
import com.gymos.benefits.infrastructure.BenefitRepository.BenefitSummary;
import com.gymos.benefits.infrastructure.BenefitRepository.ClientBenefitSummary;
import com.gymos.common.api.BusinessException;
import com.gymos.common.security.GymOsPrincipal;
import java.util.List;
import java.util.UUID;
import java.time.OffsetDateTime;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BenefitService {
	private final BenefitRepository benefitRepository;
	private final AuditService auditService;
	private final ApplicationEventPublisher eventPublisher;

	public BenefitService(
			BenefitRepository benefitRepository,
			AuditService auditService,
			ApplicationEventPublisher eventPublisher
	) {
		this.benefitRepository = benefitRepository;
		this.auditService = auditService;
		this.eventPublisher = eventPublisher;
	}

	@Transactional(readOnly = true)
	public List<BenefitSummary> benefits(GymOsPrincipal principal) {
		requirePrincipal(principal);
		return benefitRepository.benefitsForBranch(principal.organizationId(), requireSelectedBranch(principal, null));
	}

	@Transactional(readOnly = true)
	public List<BenefitSummary> accessBenefitsForCatalog(GymOsPrincipal principal) {
		requirePrincipal(principal);
		return benefitRepository.benefits(principal.organizationId()).stream()
				.filter(benefit -> "ACCESS".equals(benefit.benefitType()))
				.filter(benefit -> "ACTIVE".equals(benefit.status()))
				.toList();
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
		UUID benefitBranchId = requireSelectedBranch(principal, command.branchId());
		validatePlanLinks(principal, command, benefitBranchId);
		if (benefitRepository.codeExists(principal.organizationId(), benefitBranchId, command.code())) {
			throw new BusinessException("BENEFIT_ALREADY_EXISTS", "Ya existe un beneficio con ese codigo.");
		}
		UUID benefitId = benefitRepository.createBenefit(principal, benefitBranchId, command);
		benefitRepository.replaceMembershipPlans(principal, benefitId, command.planIds());
		benefitRepository.replaceApplicableMembershipPlans(principal, benefitId, command.applicablePlanIds());
		auditService.record(principal, "BENEFIT_CREATED", "BENEFIT", benefitId, "BACKOFFICE", null, correlationId);
		if ("ACCESS".equalsIgnoreCase(command.benefitType())) {
			eventPublisher.publishEvent(new AccessBenefitConfigurationChangedV1(
					"access-benefit-created:" + benefitId,
					principal.organizationId(), benefitBranchId, benefitId, command.planIds(), OffsetDateTime.now()));
		}
		return benefitRepository.benefit(principal.organizationId(), benefitId)
				.orElseThrow(() -> new BusinessException("BENEFIT_NOT_FOUND", "El beneficio no esta disponible."));
	}

	private void validatePlanLinks(
			GymOsPrincipal principal,
			CreateBenefitCommand command,
			UUID benefitBranchId
	) {
		validatePlanLinks(principal, command, benefitBranchId, List.of());
	}

	private void validatePlanLinks(GymOsPrincipal principal, CreateBenefitCommand command,
			UUID benefitBranchId, List<UUID> existingPlanIds) {
		boolean accessBenefit = "ACCESS".equalsIgnoreCase(command.benefitType());
		boolean specialPrice = "SPECIAL_PRICE".equalsIgnoreCase(command.discountType());
		if (!accessBenefit && !command.planIds().isEmpty()) {
			throw new BusinessException("BENEFIT_PLAN_LINK_NOT_ALLOWED", "Solo los beneficios de acceso pueden vincularse con planes.");
		}
		if (accessBenefit && command.planIds().isEmpty()) {
			throw new BusinessException("BENEFIT_PLAN_REQUIRED", "Selecciona al menos un plan para el beneficio de acceso.");
		}
		if (accessBenefit && !command.applicablePlanIds().isEmpty()) {
			throw new BusinessException("BENEFIT_PRICING_PLAN_LINK_NOT_ALLOWED", "Un beneficio de acceso no utiliza planes de precio especial.");
		}
		if (!accessBenefit && !"LOCAL".equalsIgnoreCase(command.accessScope())) {
			throw new BusinessException("BENEFIT_ACCESS_SCOPE_NOT_ALLOWED", "El alcance por cobertura sólo aplica a beneficios de acceso.");
		}
		if (!accessBenefit && specialPrice && command.applicablePlanIds().isEmpty()) {
			throw new BusinessException("BENEFIT_APPLICABLE_PLAN_REQUIRED", "Selecciona al menos un plan para el precio especial.");
		}
		if (!accessBenefit && !specialPrice && !command.applicablePlanIds().isEmpty()) {
			throw new BusinessException("BENEFIT_APPLICABLE_PLAN_NOT_ALLOWED", "Los planes aplicables sólo se configuran para precios especiales.");
		}
		String accessScope = command.accessScope().trim().toUpperCase();
		if (!"LOCAL".equals(accessScope) && !"PLAN_COVERAGE".equals(accessScope)) {
			throw new BusinessException("BENEFIT_ACCESS_SCOPE_INVALID", "Selecciona un alcance de acceso válido.");
		}
		// Existing links can remain for historical memberships after a plan is retired.
		// Only newly linked plans must be currently available; no silent removal of rights.
		List<UUID> newPlans = command.planIds().stream().filter(id -> !existingPlanIds.contains(id)).toList();
		if (!benefitRepository.membershipPlansExist(principal.organizationId(), newPlans)) {
			throw new BusinessException("MEMBERSHIP_PLAN_NOT_AVAILABLE", "Uno o mas planes no existen o no pertenecen a la organizacion.");
		}
		if (!command.applicablePlanIds().isEmpty()
				&& !benefitRepository.membershipPlansExist(principal.organizationId(), command.applicablePlanIds())) {
			throw new BusinessException("MEMBERSHIP_PLAN_NOT_AVAILABLE", "Uno o más planes aplicables no existen o no pertenecen a la organización.");
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

	@Transactional
	public BenefitSummary updateBenefit(
			GymOsPrincipal principal, UUID benefitId, UpdateBenefitCommand command, String correlationId
	) {
		requirePrincipal(principal);
		if (!principal.permissions().contains("benefit.manage")) {
			throw new org.springframework.security.access.AccessDeniedException("benefit.manage required");
		}
		UUID branchId = requireSelectedBranch(principal, null);
		BenefitSummary current = benefitRepository.benefit(principal.organizationId(), benefitId)
				.filter(benefit -> benefit.branchId() == null || benefit.branchId().equals(branchId))
				.orElseThrow(() -> new BusinessException("BENEFIT_NOT_FOUND", "El beneficio no está disponible."));
		CreateBenefitCommand definition = new CreateBenefitCommand(
				current.branchId(), current.code(), command.name(), current.benefitType(), command.discountType(),
				command.discountValueCents(), command.discountPercent(), command.requiresEvidence(), command.combinable(),
				command.priority(), command.validFrom(), command.validUntil(), command.status(), command.planIds(),
				command.accessScope(), command.applicablePlanIds());
		validatePlanLinks(principal, definition, current.branchId() == null ? principal.branchId() : current.branchId(), current.planIds());
		if (!benefitRepository.updateBenefit(principal.organizationId(), benefitId, definition, command.expectedVersion())) {
			throw new BusinessException("BENEFIT_VERSION_CONFLICT", "Otro usuario modificó este beneficio. Actualiza la lista y vuelve a intentarlo.");
		}
		benefitRepository.replaceMembershipPlans(principal, benefitId, command.planIds());
		benefitRepository.replaceApplicableMembershipPlans(principal, benefitId, command.applicablePlanIds());
		auditService.record(principal, "BENEFIT_UPDATED", "BENEFIT", benefitId, "BACKOFFICE", null, correlationId);
		if ("ACCESS".equals(current.benefitType())) {
			// Include removed plans too: their members must lose rights on reconciliation.
			List<UUID> affectedPlans = java.util.stream.Stream.concat(current.planIds().stream(), command.planIds().stream())
					.distinct().toList();
			eventPublisher.publishEvent(new AccessBenefitConfigurationChangedV1(
					"access-benefit-updated:" + benefitId + ":" + (command.expectedVersion() + 1),
					principal.organizationId(), current.branchId(), benefitId, affectedPlans, OffsetDateTime.now()));
		}
		return benefitRepository.benefit(principal.organizationId(), benefitId).orElseThrow();
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
		UUID assignmentBranchId = requireSelectedBranch(principal, command.branchId());
		if (!benefitRepository.clientExists(principal.organizationId(), command.clientId())) {
			throw new BusinessException("CLIENT_NOT_FOUND", "El cliente no esta disponible.");
		}
		BenefitSummary benefit = benefitRepository.benefit(principal.organizationId(), command.benefitId())
				.filter(candidate -> candidate.branchId() == null || candidate.branchId().equals(assignmentBranchId))
				.orElseThrow(() -> new BusinessException("BENEFIT_NOT_FOUND", "El beneficio no esta disponible."));
		if (!"ACTIVE".equals(benefit.status())) {
			throw new BusinessException("BENEFIT_NOT_ELIGIBLE", "El beneficio no esta activo.");
		}
		if (!benefitRepository.branchExists(principal.organizationId(), assignmentBranchId)) {
			throw new BusinessException("BRANCH_NOT_AVAILABLE", "La sucursal de la asignacion no pertenece a la organizacion.");
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

	private UUID requireSelectedBranch(GymOsPrincipal principal, UUID requestedBranchId) {
		UUID branchId = principal.branchId();
		if (branchId == null) {
			throw new BusinessException("BRANCH_REQUIRED", "Selecciona una sucursal.");
		}
		if (requestedBranchId != null && !requestedBranchId.equals(branchId)) {
			throw new org.springframework.security.access.AccessDeniedException("Benefit operation requires the selected branch");
		}
		return branchId;
	}

	private boolean positive(Number value) {
		return value != null && value.doubleValue() > 0;
	}
}
