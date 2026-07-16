package com.gymos.access.application.command;

import java.util.UUID;

public record AccessRequestCommand(
		UUID clientId,
		String identifier,
		String deviceCode
) {
}
