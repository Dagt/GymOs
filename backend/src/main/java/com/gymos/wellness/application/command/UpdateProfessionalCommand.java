package com.gymos.wellness.application.command;

public record UpdateProfessionalCommand(
		String displayName,
		String professionalType,
		String email,
		String phone,
		String bio,
		String officeLocation,
		String engagementType,
		String status
) {
}
