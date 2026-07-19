package com.gymos.clients.application.command;

import java.time.LocalDate;

public record CreateClientCommand(
		String firstName,
		String lastName,
		String maternalLastName,
		LocalDate birthDate,
		String gender,
		String phone,
		String email,
		String photoReference,
		String notes
) {
}
