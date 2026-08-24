package com.gymos.security.application.result;

import com.gymos.common.security.AccountScope;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record CurrentUserResult(
		UUID userId,
		AccountScope accountScope,
		UUID organizationId,
		UUID branchId,
		String username,
		String fullName,
		Set<String> permissions,
		Set<String> roles,
		boolean platformAdmin,
		boolean supportMode,
		Instant supportExpiresAt,
		String console,
		String organizationName,
		String brandName,
		String logoUrl,
		String primaryColor,
		String accentColor,
		String surfaceColor
) {
}
