package com.gymos.memberships.application;

import com.gymos.memberships.infrastructure.MembershipRepository.MembershipSummary;
import com.gymos.memberships.infrastructure.MembershipRepository.PlanForRenewal;
import java.time.LocalDate;
import org.springframework.stereotype.Component;

@Component
class MembershipRenewalWindowCalculator {
	MembershipRenewalWindow calculate(
			MembershipSummary current,
			PlanForRenewal plan,
			LocalDate businessDate
	) {
		boolean extendsCurrentPeriod = current.endsOn() != null && !current.endsOn().isBefore(businessDate);
		LocalDate periodStartsOn = extendsCurrentPeriod
				? current.endsOn().plusDays(1)
				: businessDate;
		LocalDate membershipStartsOn = extendsCurrentPeriod && current.startsOn() != null
				? current.startsOn()
				: periodStartsOn;
		LocalDate endsOn = periodStartsOn.plusDays(plan.durationDays() - 1L);
		return new MembershipRenewalWindow(
				membershipStartsOn,
				periodStartsOn,
				endsOn,
				endsOn.plusDays(plan.graceDays())
		);
	}
}
