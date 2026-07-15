package com.gymos.clients.application.command;

import java.time.LocalDate;

public record CreateClientCommand(
		String firstName,
		String lastName,
		LocalDate birthDate,
		String gender,
		String phone,
		String email,
		String photoReference,
		String notes
) {
}
