package com.gymos.memberships.application;

import com.gymos.common.api.BusinessException;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.memberships.application.command.RenewMembershipCommand;
import com.gymos.memberships.application.result.MembershipSaleResult;
import com.gymos.memberships.infrastructure.MembershipRepository;
import com.gymos.memberships.infrastructure.MembershipRepository.MembershipSummary;
import com.gymos.memberships.infrastructure.MembershipRepository.PlanForRenewal;
import com.gymos.sales.api.operations.CreateMembershipRenewalSaleCommand;
import com.gymos.sales.api.operations.SalesOperations;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class MembershipRenewalService {
	private final MembershipRepository membershipRepository;
	private final SalesOperations salesOperations;

	MembershipRenewalService(MembershipRepository membershipRepository, SalesOperations salesOperations) {
		this.membershipRepository = membershipRepository;
		this.salesOperations = salesOperations;
	}

	@Transactional
	MembershipSaleResult renew(
			GymOsPrincipal principal,
			UUID membershipId,
			RenewMembershipCommand command,
			String correlationId
	) {
		MembershipAccessGuard.requireBranch(principal);
		MembershipSummary current = membershipRepository.byId(principal.organizationId(), principal.branchId(), membershipId)
				.orElseThrow(() -> new BusinessException("MEMBERSHIP_NOT_FOUND", "La membresia no existe o no esta disponible."));
		if (!"ACTIVE".equals(current.status())) {
			throw new BusinessException("MEMBERSHIP_NOT_RENEWABLE", "Solo puedes renovar una membresia activa.");
		}
		PlanForRenewal plan = membershipRepository.planForRenewal(principal.organizationId(), current.planId())
				.orElseThrow(() -> new BusinessException("PLAN_NOT_AVAILABLE", "El plan de la membresia no esta disponible para renovacion."));

		var sale = salesOperations.createMembershipRenewal(new CreateMembershipRenewalSaleCommand(
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
				defaultIdempotencyKey("membership-renewal", membershipId, command.idempotencyKey()),
				correlationId
		));
		MembershipSummary renewed = sale.paymentCompleted()
				? membershipRepository.bySaleAndPlan(principal.organizationId(), principal.branchId(), sale.saleId(), plan.planId()).orElse(null)
				: null;
		return MembershipResultMapper.toSaleResult(sale, renewed);
	}

	private String defaultIdempotencyKey(String operation, UUID membershipId, String provided) {
		return provided == null || provided.isBlank() ? operation + ":" + membershipId + ":" + UUID.randomUUID() : provided.trim();
	}
}
