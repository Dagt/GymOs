package com.gymos.coaching.application.command;

import java.util.List;

public record CreateCoachCommand(
		String displayName,
		String email,
		String phone,
		List<String> specialties,
		String bio
) {
}
