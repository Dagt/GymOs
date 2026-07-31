package com.gymos.memberships.application;

import com.gymos.common.api.BusinessException;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.common.time.BusinessTime;
import com.gymos.memberships.application.command.EnrollMembershipCommand;
import com.gymos.memberships.application.result.MembershipQuoteResult;
import com.gymos.memberships.application.result.MembershipSaleResult;
import com.gymos.memberships.infrastructure.MembershipRepository;
import com.gymos.memberships.infrastructure.MembershipRepository.PlanForRenewal;
import com.gymos.sales.api.operations.CreateMembershipEnrollmentSaleCommand;
import com.gymos.sales.api.operations.SalesOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MembershipEnrollmentService {
	private final MembershipRepository membershipRepository;
	private final MembershipPricingService pricingService;
	private final MembershipOperationIdempotency idempotency;
	private final SalesOperations salesOperations;
	private final MembershipSaleResultResolver resultResolver;

	public MembershipEnrollmentService(
			MembershipRepository membershipRepository,
			MembershipPricingService pricingService,
			MembershipOperationIdempotency idempotency,
			SalesOperations salesOperations,
			MembershipSaleResultResolver resultResolver
	) {
		this.membershipRepository = membershipRepository;
		this.pricingService = pricingService;
		this.idempotency = idempotency;
		this.salesOperations = salesOperations;
		this.resultResolver = resultResolver;
	}

	@Transactional
	public MembershipSaleResult enroll(
			GymOsPrincipal principal,
			EnrollMembershipCommand command,
			String correlationId
	) {
		MembershipAccessGuard.requireBranch(principal);
		if (!membershipRepository.clientExists(principal.organizationId(), command.clientId())) {
			throw new BusinessException("CLIENT_NOT_FOUND", "El cliente no existe o no esta disponible.");
		}
		membershipRepository.expireDueMemberships(
				principal.organizationId(), command.clientId(), BusinessTime.today());
		if (membershipRepository.hasOtherActiveMembership(principal.organizationId(), command.clientId(), null)) {
			throw new BusinessException("MEMBERSHIP_ACTIVE_CONFLICT", "El socio ya tiene una membresia activa.");
		}
		PlanForRenewal plan = membershipRepository.planForRenewal(principal.organizationId(), command.planId())
				.orElseThrow(() -> new BusinessException("PLAN_NOT_AVAILABLE", "El plan no esta disponible."));
		MembershipQuoteResult quote = pricingService.quote(
				principal.organizationId(), principal.branchId(), command.clientId(), plan);
		var sale = salesOperations.createMembershipEnrollment(new CreateMembershipEnrollmentSaleCommand(
				principal.organizationId(),
				principal.branchId(),
				principal.userId(),
				command.clientId(),
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
				idempotency.resolve("membership-enrollment", command.clientId(), command.idempotencyKey()),
				correlationId
		));
		return resultResolver.resolve(principal, sale, plan);
	}
}
