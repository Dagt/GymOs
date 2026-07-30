package com.gymos.memberships.application;

import com.gymos.common.security.GymOsPrincipal;
import com.gymos.memberships.application.command.ChangeMembershipPlanCommand;
import com.gymos.memberships.application.result.MembershipSaleResult;
import com.gymos.memberships.application.result.MembershipQuoteResult;
import com.gymos.memberships.infrastructure.MembershipRepository.MembershipSummary;
import com.gymos.memberships.infrastructure.MembershipRepository.PlanForRenewal;
import com.gymos.sales.api.operations.CreateMembershipPlanChangeSaleCommand;
import com.gymos.sales.api.operations.SalesOperations;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class MembershipPlanChangeService {
	private final MembershipOperationPolicy policy;
	private final MembershipOperationIdempotency idempotency;
	private final SalesOperations salesOperations;
	private final MembershipSaleResultResolver resultResolver;
	private final MembershipPricingService pricingService;

	MembershipPlanChangeService(
			MembershipOperationPolicy policy,
			MembershipOperationIdempotency idempotency,
			SalesOperations salesOperations,
			MembershipSaleResultResolver resultResolver,
			MembershipPricingService pricingService
	) {
		this.policy = policy;
		this.idempotency = idempotency;
		this.salesOperations = salesOperations;
		this.resultResolver = resultResolver;
		this.pricingService = pricingService;
	}

	@Transactional
	MembershipSaleResult changePlan(
			GymOsPrincipal principal,
			UUID membershipId,
			ChangeMembershipPlanCommand command,
			String correlationId
	) {
		MembershipSummary current = policy.requireActiveMembership(
				principal,
				membershipId,
				"MEMBERSHIP_NOT_CHANGEABLE",
				"Solo puedes cambiar una membresia activa."
		);
		policy.requireDifferentPlan(current, command.targetPlanId());
		PlanForRenewal plan = policy.requirePlanForRenewal(
				principal.organizationId(),
				command.targetPlanId(),
				"El plan seleccionado no esta disponible."
		);
		String method = command.method().trim().toUpperCase();
		policy.requireCompletedPlanChangePayment(command.validateNow(), method);
		MembershipQuoteResult quote = pricingService.quote(
				principal.organizationId(), principal.branchId(), current.clientId(), plan);

		var sale = salesOperations.createMembershipPlanChange(new CreateMembershipPlanChangeSaleCommand(
				principal.organizationId(),
				principal.branchId(),
				principal.userId(),
				current.clientId(),
				current.id(),
				plan.planId(),
				plan.productId(),
				plan.planName(),
				quote.listPriceCents(),
				quote.discountCents(),
				quote.totalCents(),
				plan.currency(),
				MembershipBenefitSnapshotMapper.from(quote),
				command.method(),
				command.reference(),
				command.validateNow(),
				command.notes(),
				idempotency.resolve("membership-plan-change", membershipId, command.idempotencyKey()),
				correlationId
		));
		return resultResolver.resolve(principal, sale, plan);
	}
}
