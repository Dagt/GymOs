package com.gymos.clients.application.result;

import java.util.UUID;

public record ClientImportRowResult(
		int rowNumber,
		String status,
		String reason,
		UUID clientId,
		String firstName,
		String lastName,
		String email,
		String phone
) {
}
