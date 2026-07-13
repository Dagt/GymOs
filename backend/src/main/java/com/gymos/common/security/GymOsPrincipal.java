package com.gymos.common.security;

import java.util.Set;
import java.util.UUID;

public record GymOsPrincipal(
		UUID userId,
		UUID organizationId,
		UUID branchId,
		String username,
		String fullName,
		Set<String> permissions
) {
}
