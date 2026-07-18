package com.gymos.security.application.result;

import java.util.Set;
import java.util.UUID;

public record CurrentUserResult(
		UUID userId,
		UUID organizationId,
		UUID branchId,
		String username,
		String fullName,
		Set<String> permissions,
		Set<String> roles,
		boolean platformAdmin,
		boolean supportMode,
		String console
) {
}
