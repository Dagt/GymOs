package com.gymos.memberships.application;

import com.gymos.common.security.GymOsPrincipal;
import com.gymos.memberships.application.command.RenewMembershipCommand;
import com.gymos.memberships.application.result.MembershipSaleResult;
import com.gymos.memberships.application.result.MembershipQuoteResult;
import com.gymos.memberships.infrastructure.MembershipRepository.MembershipSummary;
import com.gymos.memberships.infrastructure.MembershipRepository.PlanForRenewal;
import com.gymos.sales.api.operations.CreateMembershipRenewalSaleCommand;
import com.gymos.sales.api.operations.SalesOperations;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class MembershipRenewalService {
	private final MembershipOperationPolicy policy;
	private final MembershipOperationIdempotency idempotency;
	private final SalesOperations salesOperations;
	private final MembershipSaleResultResolver resultResolver;
	private final MembershipPricingService pricingService;

	MembershipRenewalService(
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
	MembershipSaleResult renew(
			GymOsPrincipal principal,
			UUID membershipId,
			RenewMembershipCommand command,
			String correlationId
	) {
		MembershipSummary current = policy.requireRenewableMembership(
				principal,
				membershipId,
				"MEMBERSHIP_NOT_RENEWABLE",
				"Solo puedes renovar una membresia activa o vencida."
		);
		PlanForRenewal plan = policy.requirePlanForRenewal(
				principal.organizationId(),
				current.planId(),
				"El plan de la membresia no esta disponible para renovacion."
		);
		MembershipQuoteResult quote = pricingService.quote(
				principal.organizationId(), principal.branchId(), current.clientId(), plan);

		var sale = salesOperations.createMembershipRenewal(new CreateMembershipRenewalSaleCommand(
				principal.organizationId(),
				principal.branchId(),
				principal.userId(),
				current.clientId(),
				current.id(),
				plan.planId(),
				plan.productId(),
				plan.planName(),
				quote.planPriceCents(),
				quote.registrationFeeCents(),
				quote.registrationFeeLabel(),
				quote.listPriceCents(),
				quote.discountCents(),
				quote.totalCents(),
				plan.currency(),
				MembershipBenefitSnapshotMapper.from(quote),
				command.method(),
				command.reference(),
				command.validateNow(),
				command.notes(),
				idempotency.resolve("membership-renewal", membershipId, command.idempotencyKey()),
				correlationId
		));
		return resultResolver.resolve(principal, sale, plan);
	}
}
