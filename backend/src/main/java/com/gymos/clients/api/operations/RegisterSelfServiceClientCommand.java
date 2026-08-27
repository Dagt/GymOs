package com.gymos.clients.api.operations;

import java.time.LocalDate;
import java.util.UUID;

public record RegisterSelfServiceClientCommand(
		UUID organizationId,
		UUID branchId,
		String firstName,
		String lastName,
		String maternalLastName,
		LocalDate birthDate,
		String gender,
		String phone,
		String email
) {
}
