package com.gymos.memberships.application;

import static org.assertj.core.api.Assertions.assertThat;
import com.gymos.memberships.infrastructure.MembershipRepository.PlanForRenewal;
import com.gymos.sales.api.events.PaymentCompletedV1;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MembershipActivationWindowCalculatorTests {
	@Test
	void thirtyDayPlanExpiresThirtyCalendarDaysAfterActivation() {
		MembershipActivationWindowCalculator calculator = new MembershipActivationWindowCalculator();
		PaymentCompletedV1 event = event(OffsetDateTime.parse("2026-07-22T12:00:00-06:00"));
		PlanForRenewal plan = plan(30, 0);

		MembershipActivationWindow window = calculator.calculate(event, plan);

		assertThat(window.startsOn()).isEqualTo(LocalDate.of(2026, 7, 22));
		assertThat(window.endsOn()).isEqualTo(LocalDate.of(2026, 8, 20));
		assertThat(window.graceEndsOn()).isEqualTo(LocalDate.of(2026, 8, 20));
	}

	@Test
	void planChangeStartsImmediatelyAndKeepsInclusiveDuration() {
		MembershipActivationWindowCalculator calculator = new MembershipActivationWindowCalculator();
		PaymentCompletedV1 event = event(OffsetDateTime.parse("2026-07-22T12:00:00-06:00"));
		PlanForRenewal plan = plan(30, 3);

		MembershipActivationWindow window = calculator.calculate(event, plan);

		assertThat(window.startsOn()).isEqualTo(LocalDate.of(2026, 7, 22));
		assertThat(window.endsOn()).isEqualTo(LocalDate.of(2026, 8, 20));
		assertThat(window.graceEndsOn()).isEqualTo(LocalDate.of(2026, 8, 23));
	}

	private static PaymentCompletedV1 event(OffsetDateTime occurredAt) {
		return new PaymentCompletedV1(
				"payment-completed:test",
				UUID.randomUUID(),
				UUID.randomUUID(),
				UUID.randomUUID(),
				UUID.randomUUID(),
				"S-TEST",
				null,
				null,
				null,
				List.of(),
				occurredAt
		);
	}

	private static PlanForRenewal plan(int durationDays, int graceDays) {
		return new PlanForRenewal(
				UUID.randomUUID(),
				UUID.randomUUID(),
				"Plan",
				durationDays,
				graceDays,
				false,
				600_00,
				"MXN"
		);
	}
}
