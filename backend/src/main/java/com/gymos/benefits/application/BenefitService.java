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
		return benefitRepository.benefits(principal.organizationId());
	}

	@Transactional
	public BenefitSummary createBenefit(GymOsPrincipal principal, CreateBenefitCommand command, String correlationId) {
		requirePrincipal(principal);
		if (benefitRepository.codeExists(principal.organizationId(), command.code())) {
			throw new BusinessException("BENEFIT_ALREADY_EXISTS", "Ya existe un beneficio con ese codigo.");
		}
		UUID benefitId = benefitRepository.createBenefit(principal, command);
		auditService.record(principal, "BENEFIT_CREATED", "BENEFIT", benefitId, "BACKOFFICE", null, correlationId);
		return benefitRepository.benefit(principal.organizationId(), benefitId)
				.orElseThrow(() -> new BusinessException("BENEFIT_NOT_FOUND", "El beneficio no esta disponible."));
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
}
