package com.gymos.common.security;

import java.util.Set;
import java.util.UUID;

public record GymOsPrincipal(
		UUID userId,
		UUID organizationId,
		UUID branchId,
		String username,
		String fullName,
		Set<String> permissions,
		Set<String> roles,
		boolean supportMode
) {
	public GymOsPrincipal(
			UUID userId,
			UUID organizationId,
			UUID branchId,
			String username,
			String fullName,
			Set<String> permissions
	) {
		this(userId, organizationId, branchId, username, fullName, permissions, Set.of(), false);
	}

	public boolean platformAdmin() {
		return permissions.contains("platform.admin");
	}
}
