package com.gymos.memberships.application;

import com.gymos.memberships.infrastructure.MembershipRepository.PlanForRenewal;
import com.gymos.sales.api.events.PaymentCompletedV1;
import java.time.LocalDate;
import org.springframework.stereotype.Service;

@Service
class MembershipActivationWindowCalculator {
	MembershipActivationWindow calculate(PaymentCompletedV1 event, PlanForRenewal plan) {
		LocalDate businessDate = MembershipBusinessDate.from(event.occurredAt());
		LocalDate startsOn = businessDate;
		LocalDate endsOn = startsOn.plusDays(plan.durationDays() - 1L);
		return new MembershipActivationWindow(startsOn, endsOn, endsOn.plusDays(plan.graceDays()));
	}
}
