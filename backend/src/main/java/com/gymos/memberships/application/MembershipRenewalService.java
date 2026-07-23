package com.gymos.memberships.application;

import com.gymos.common.security.GymOsPrincipal;
import com.gymos.memberships.application.command.RenewMembershipCommand;
import com.gymos.memberships.application.result.MembershipSaleResult;
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

	MembershipRenewalService(
			MembershipOperationPolicy policy,
			MembershipOperationIdempotency idempotency,
			SalesOperations salesOperations,
			MembershipSaleResultResolver resultResolver
	) {
		this.policy = policy;
		this.idempotency = idempotency;
		this.salesOperations = salesOperations;
		this.resultResolver = resultResolver;
	}

	@Transactional
	MembershipSaleResult renew(
			GymOsPrincipal principal,
			UUID membershipId,
			RenewMembershipCommand command,
			String correlationId
	) {
		MembershipSummary current = policy.requireActiveMembership(
				principal,
				membershipId,
				"MEMBERSHIP_NOT_RENEWABLE",
				"Solo puedes renovar una membresia activa."
		);
		PlanForRenewal plan = policy.requirePlanForRenewal(
				principal.organizationId(),
				current.planId(),
				"El plan de la membresia no esta disponible para renovacion."
		);

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
				idempotency.resolve("membership-renewal", membershipId, command.idempotencyKey()),
				correlationId
		));
		return resultResolver.resolve(principal, sale, plan);
	}
}
