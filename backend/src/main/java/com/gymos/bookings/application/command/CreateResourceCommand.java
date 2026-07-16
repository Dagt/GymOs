package com.gymos.bookings.application.command;

import java.time.LocalTime;

public record CreateResourceCommand(
		String name,
		String resourceType,
		int capacity,
		int slotMinutes,
		LocalTime opensAt,
		LocalTime closesAt,
		String notes
) {
}
