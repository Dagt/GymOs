package com.gymos.coaching.application.command;

import java.time.LocalDate;
import java.util.UUID;

public record AssignWorkoutCommand(
		UUID clientId,
		UUID workoutVersionId,
		LocalDate startsOn,
		LocalDate endsOn,
		String notes
) {
}
