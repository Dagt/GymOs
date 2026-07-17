package com.gymos.identity.application.command;

import java.util.List;
import java.util.UUID;

public record CreateInternalUserCommand(
		String firstName,
		String lastName,
		String email,
		String phone,
		String username,
		String temporaryPassword,
		UUID roleId,
		List<UUID> branchIds
) {
}
