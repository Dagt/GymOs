package com.gymos.common.security;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record GymOsPrincipal(
		UUID userId,
		AccountScope accountScope,
		UUID organizationId,
		UUID branchId,
		String username,
		String fullName,
		Set<String> permissions,
		Set<String> roles,
		boolean supportMode,
		Instant supportExpiresAt
) {
	public GymOsPrincipal(
			UUID userId,
			UUID organizationId,
			UUID branchId,
			String username,
			String fullName,
			Set<String> permissions
	) {
		this(userId, AccountScope.TENANT, organizationId, branchId, username, fullName, permissions, Set.of(), false, null);
	}

	public GymOsPrincipal(
			UUID userId,
			UUID organizationId,
			UUID branchId,
			String username,
			String fullName,
			Set<String> permissions,
			Set<String> roles,
			boolean supportMode
	) {
		this(userId, AccountScope.TENANT, organizationId, branchId, username, fullName, permissions, roles, supportMode, null);
	}

	public boolean platformAdmin() {
		return accountScope == AccountScope.PLATFORM
				&& !supportMode
				&& permissions.contains("platform.admin");
	}

	public boolean platformIdentity() {
		return accountScope == AccountScope.PLATFORM;
	}

	public boolean platformSupport() {
		return accountScope == AccountScope.PLATFORM && supportMode;
	}

	public boolean tenantIdentity() {
		return accountScope == AccountScope.TENANT;
	}
}
