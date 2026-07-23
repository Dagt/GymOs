package com.gymos.common.time;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class BusinessTimeTests {
	@Test
	void utcNextDayStillUsesPreviousMexicoBusinessDate() {
		OffsetDateTime occurredAt = OffsetDateTime.parse("2026-07-22T03:43:00Z");

		assertThat(BusinessTime.localDate(occurredAt)).isEqualTo(LocalDate.of(2026, 7, 21));
	}

	@Test
	void localMidnightStartsNewBusinessDate() {
		OffsetDateTime occurredAt = OffsetDateTime.parse("2026-07-22T06:00:00Z");

		assertThat(BusinessTime.localDate(occurredAt)).isEqualTo(LocalDate.of(2026, 7, 22));
	}

	@Test
	void missingOccurrenceIsRejected() {
		assertThatThrownBy(() -> BusinessTime.localDate(null))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("occurredAt is required");
	}
}
