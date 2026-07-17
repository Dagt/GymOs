package com.gymos.identity.application.result;

import java.util.List;
import java.util.UUID;

public record InternalUserSummary(
		UUID id,
		String fullName,
		String username,
		String email,
		String phone,
		String status,
		List<String> roles,
		List<String> branches
) {
}
