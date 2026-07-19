package com.gymos.clients.application.command;

import java.time.LocalDate;

public record UpdateClientCommand(
		String firstName,
		String lastName,
		String maternalLastName,
		LocalDate birthDate,
		String gender,
		String phone,
		String email,
		String photoReference,
		String status,
		String notes
) {
}
