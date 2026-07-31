package com.gymos.clients.api.operations;

import java.util.UUID;

public record SelfServiceClientProfile(
		UUID clientId,
		String displayName,
		String email,
		String phone
) {
}
