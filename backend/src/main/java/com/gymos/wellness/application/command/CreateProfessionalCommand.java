package com.gymos.wellness.application.command;

public record CreateProfessionalCommand(
		String displayName,
		String professionalType,
		String email,
		String phone,
		String bio,
		String officeLocation,
		String engagementType
) {
}
