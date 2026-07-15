package com.gymos.coaching.application.command;

public record CreateWorkoutVersionCommand(
		String summary,
		String contentJson
) {
}
