package com.gymos.identity.application.result;

import java.util.UUID;

public record RoleOption(
		UUID id,
		String name,
		String description,
		boolean systemRole,
		int permissionCount
) {
}
