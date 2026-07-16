package com.gymos.wellness.application.command;

import java.time.LocalTime;

public record CreateProfessionalAvailabilityCommand(
		int dayOfWeek,
		LocalTime opensAt,
		LocalTime closesAt
) {
}
