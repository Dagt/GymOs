package com.gymos.coaching.application.command;

import java.util.UUID;

public record CreateWorkoutCommand(
		UUID coachId,
		String title,
		String goal,
		String level,
		String summary,
		String contentJson
) {
}
