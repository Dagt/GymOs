package com.gymos.memberships.application;

import com.gymos.common.security.GymOsPrincipal;
import com.gymos.memberships.application.result.MembershipSaleResult;
import com.gymos.memberships.infrastructure.MembershipRepository;
import com.gymos.memberships.infrastructure.MembershipRepository.MembershipSummary;
import com.gymos.memberships.infrastructure.MembershipRepository.PlanForRenewal;
import org.springframework.stereotype.Component;

@Component
class MembershipSaleResultResolver {
	private final MembershipRepository membershipRepository;

	MembershipSaleResultResolver(MembershipRepository membershipRepository) {
		this.membershipRepository = membershipRepository;
	}

	MembershipSaleResult resolve(
			GymOsPrincipal principal,
			com.gymos.sales.api.operations.MembershipSaleResult sale,
			PlanForRenewal plan
	) {
		MembershipSummary membership = sale.paymentCompleted()
				? membershipRepository.bySaleAndPlan(principal.organizationId(), principal.branchId(), sale.saleId(), plan.planId()).orElse(null)
				: null;
		return MembershipResultMapper.toSaleResult(sale, membership);
	}
}
