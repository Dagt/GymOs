package com.gymos.memberships.application;

import com.gymos.memberships.infrastructure.MembershipRepository;
import com.gymos.memberships.infrastructure.MembershipRepository.PlanForRenewal;
import com.gymos.sales.api.events.PaymentCompletedV1;
import java.time.LocalDate;
import org.springframework.stereotype.Service;

@Service
class MembershipActivationWindowCalculator {
	private final MembershipRepository membershipRepository;

	MembershipActivationWindowCalculator(MembershipRepository membershipRepository) {
		this.membershipRepository = membershipRepository;
	}

	MembershipActivationWindow calculate(PaymentCompletedV1 event, PlanForRenewal plan, String action) {
		LocalDate businessDate = MembershipBusinessDate.from(event.occurredAt());
		LocalDate latestEnd = membershipRepository.latestMembershipEnd(event.organizationId(), event.clientId(), plan.planId())
				.orElse(null);
		LocalDate startsOn = latestEnd != null && !latestEnd.isBefore(businessDate) ? latestEnd.plusDays(1) : businessDate;
		if ("MEMBERSHIP_PLAN_CHANGED".equals(action)) {
			startsOn = businessDate;
		}
		LocalDate endsOn = startsOn.plusDays(plan.durationDays() - 1L);
		return new MembershipActivationWindow(startsOn, endsOn, endsOn.plusDays(plan.graceDays()));
	}
}
