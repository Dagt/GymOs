package com.gymos.memberships.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class MembershipBusinessDateTests {
	@Test
	void lateNightPaymentUsesMexicoLocalDateInsteadOfUtcDate() {
		OffsetDateTime paymentCompletedAt = OffsetDateTime.parse("2026-07-22T03:43:00Z");

		LocalDate businessDate = MembershipBusinessDate.from(paymentCompletedAt);

		assertEquals(LocalDate.of(2026, 7, 21), businessDate);
	}

	@Test
	void paymentAfterLocalMidnightUsesFollowingBusinessDate() {
		OffsetDateTime paymentCompletedAt = OffsetDateTime.parse("2026-07-22T06:15:00Z");

		LocalDate businessDate = MembershipBusinessDate.from(paymentCompletedAt);

		assertEquals(LocalDate.of(2026, 7, 22), businessDate);
	}

	@Test
	void paymentTimestampIsRequired() {
		assertThrows(IllegalArgumentException.class, () -> MembershipBusinessDate.from(null));
	}
}
