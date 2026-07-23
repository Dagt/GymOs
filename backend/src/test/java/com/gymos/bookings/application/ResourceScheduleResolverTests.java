package com.gymos.bookings.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymos.bookings.infrastructure.BookingResourceRepository.ResourceRecord;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ResourceScheduleResolverTests {
	private final ResourceScheduleResolver resolver = new ResourceScheduleResolver(new ObjectMapper());

	@Test
	void usesDefaultHoursWhenScheduleIsMissing() {
		var hours = resolver.resolve(resource(null), LocalDate.of(2026, 7, 22)).orElseThrow();

		assertEquals(LocalTime.of(6, 0), hours.opensAt());
		assertEquals(LocalTime.of(22, 0), hours.closesAt());
	}

	@Test
	void resolvesHoursForRequestedWeekday() {
		String notes = """
				{"schedule":[{"day":"WEDNESDAY","opensAt":"08:30","closesAt":"19:15"}]}
				""";

		var hours = resolver.resolve(resource(notes), LocalDate.of(2026, 7, 22)).orElseThrow();

		assertEquals(LocalTime.of(8, 30), hours.opensAt());
		assertEquals(LocalTime.of(19, 15), hours.closesAt());
	}

	@Test
	void returnsClosedWhenWeekdayIsNotConfigured() {
		String notes = """
				{"schedule":[{"day":"THURSDAY","opensAt":"08:30","closesAt":"19:15"}]}
				""";

		assertTrue(resolver.resolve(resource(notes), LocalDate.of(2026, 7, 22)).isEmpty());
	}

	@Test
	void fallsBackToDefaultHoursWhenScheduleJsonIsInvalid() {
		var hours = resolver.resolve(resource("not-json"), LocalDate.of(2026, 7, 22)).orElseThrow();

		assertEquals(LocalTime.of(6, 0), hours.opensAt());
		assertEquals(LocalTime.of(22, 0), hours.closesAt());
	}

	private ResourceRecord resource(String notes) {
		return new ResourceRecord(
				UUID.randomUUID(),
				"Sala grupal",
				"ROOM",
				10,
				60,
				"ACTIVE",
				LocalTime.of(6, 0),
				LocalTime.of(22, 0),
				notes,
				List.of(),
				""
		);
	}
}
