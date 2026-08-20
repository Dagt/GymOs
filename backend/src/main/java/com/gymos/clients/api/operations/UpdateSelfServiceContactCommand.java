package com.gymos.clients.api.operations;

import java.util.UUID;

public record UpdateSelfServiceContactCommand(
		UUID organizationId,
		UUID clientId,
		String email,
		String phone
) {
}
