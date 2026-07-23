package com.gymos.memberships.application;

import com.gymos.common.api.BusinessException;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.memberships.application.command.ChangeMembershipPlanCommand;
import com.gymos.memberships.application.result.MembershipSaleResult;
import com.gymos.memberships.infrastructure.MembershipRepository;
import com.gymos.memberships.infrastructure.MembershipRepository.MembershipSummary;
import com.gymos.memberships.infrastructure.MembershipRepository.PlanForRenewal;
import com.gymos.sales.api.operations.CreateMembershipPlanChangeSaleCommand;
import com.gymos.sales.api.operations.SalesOperations;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class MembershipPlanChangeService {
	private final MembershipRepository membershipRepository;
	private final SalesOperations salesOperations;

	MembershipPlanChangeService(MembershipRepository membershipRepository, SalesOperations salesOperations) {
		this.membershipRepository = membershipRepository;
		this.salesOperations = salesOperations;
	}

	@Transactional
	MembershipSaleResult changePlan(
			GymOsPrincipal principal,
			UUID membershipId,
			ChangeMembershipPlanCommand command,
			String correlationId
	) {
		MembershipAccessGuard.requireBranch(principal);
		MembershipSummary current = membershipRepository.byId(principal.organizationId(), principal.branchId(), membershipId)
				.orElseThrow(() -> new BusinessException("MEMBERSHIP_NOT_FOUND", "La membresia no existe o no esta disponible."));
		if (!"ACTIVE".equals(current.status())) {
			throw new BusinessException("MEMBERSHIP_NOT_CHANGEABLE", "Solo puedes cambiar una membresia activa.");
		}
		if (current.planId().equals(command.targetPlanId())) {
			throw new BusinessException("MEMBERSHIP_PLAN_UNCHANGED", "Selecciona un plan diferente.");
		}
		PlanForRenewal plan = membershipRepository.planForRenewal(principal.organizationId(), command.targetPlanId())
				.orElseThrow(() -> new BusinessException("PLAN_NOT_AVAILABLE", "El plan seleccionado no esta disponible."));
		String method = command.method().trim().toUpperCase();
		if (!command.validateNow() && !"CASH".equals(method)) {
			throw new BusinessException("PLAN_CHANGE_REQUIRES_COMPLETED_PAYMENT", "El cambio de plan requiere pago completado.");
		}

		var sale = salesOperations.createMembershipPlanChange(new CreateMembershipPlanChangeSaleCommand(
				principal.organizationId(),
				principal.branchId(),
				principal.userId(),
				current.clientId(),
				current.id(),
				plan.planId(),
				plan.productId(),
				plan.planName(),
				plan.priceCents(),
				plan.currency(),
				command.method(),
				command.reference(),
				command.validateNow(),
				command.notes(),
				defaultIdempotencyKey("membership-plan-change", membershipId, command.idempotencyKey()),
				correlationId
		));
		MembershipSummary changed = sale.paymentCompleted()
				? membershipRepository.bySaleAndPlan(principal.organizationId(), principal.branchId(), sale.saleId(), plan.planId()).orElse(null)
				: null;
		return MembershipResultMapper.toSaleResult(sale, changed);
	}

	private String defaultIdempotencyKey(String operation, UUID membershipId, String provided) {
		return provided == null || provided.isBlank() ? operation + ":" + membershipId + ":" + UUID.randomUUID() : provided.trim();
	}
}
