package com.gymos.clients.api.operations;

import java.time.LocalDate;
import java.util.UUID;

public record VerifyClientIdentityCommand(
		UUID organizationId,
		UUID branchId,
		String identifier,
		LocalDate birthDate
) {
}
