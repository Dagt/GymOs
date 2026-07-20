package com.gymos.bookings.application.command;

import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public record CreateResourceCommand(
		String name,
		String resourceType,
		int capacity,
		int slotMinutes,
		LocalTime opensAt,
		LocalTime closesAt,
		String notes,
		List<UUID> allowedPlanIds
) {
}
