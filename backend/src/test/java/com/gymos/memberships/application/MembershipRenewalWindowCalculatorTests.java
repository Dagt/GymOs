package com.gymos.memberships.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.gymos.memberships.infrastructure.MembershipRepository.MembershipSummary;
import com.gymos.memberships.infrastructure.MembershipRepository.PlanForRenewal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MembershipRenewalWindowCalculatorTests {
	private final MembershipRenewalWindowCalculator calculator = new MembershipRenewalWindowCalculator();
	private final PlanForRenewal plan = new PlanForRenewal(
			UUID.randomUUID(), UUID.randomUUID(), "Mensual", 30, 3, false, 80_000, "MXN");

	@Test
	void activeRenewalStartsAfterCurrentEndWithoutOverlap() {
		MembershipSummary current = membership(
				"ACTIVE", LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 30));

		MembershipRenewalWindow window = calculator.calculate(
				current, plan, LocalDate.of(2026, 7, 20));

		assertThat(window.membershipStartsOn()).isEqualTo(LocalDate.of(2026, 7, 1));
		assertThat(window.periodStartsOn()).isEqualTo(LocalDate.of(2026, 7, 31));
		assertThat(window.endsOn()).isEqualTo(LocalDate.of(2026, 8, 29));
		assertThat(window.graceEndsOn()).isEqualTo(LocalDate.of(2026, 9, 1));
	}

	@Test
	void expiredRenewalStartsOnBusinessDateAndCreatesFullInclusivePeriod() {
		MembershipSummary current = membership(
				"EXPIRED", LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 30));

		MembershipRenewalWindow window = calculator.calculate(
				current, plan, LocalDate.of(2026, 7, 20));

		assertThat(window.membershipStartsOn()).isEqualTo(LocalDate.of(2026, 7, 20));
		assertThat(window.periodStartsOn()).isEqualTo(LocalDate.of(2026, 7, 20));
		assertThat(window.endsOn()).isEqualTo(LocalDate.of(2026, 8, 18));
		assertThat(window.graceEndsOn()).isEqualTo(LocalDate.of(2026, 8, 21));
	}

	private MembershipSummary membership(String status, LocalDate startsOn, LocalDate endsOn) {
		return new MembershipSummary(
				UUID.randomUUID(),
				UUID.randomUUID(),
				plan.planId(),
				plan.planName(),
				UUID.randomUUID(),
				"S-TEST",
				status,
				OffsetDateTime.parse("2026-05-01T10:00:00-06:00"),
				startsOn,
				endsOn,
				endsOn.plusDays(plan.graceDays())
		);
	}
}
