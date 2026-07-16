package com.gymos.access.application.command;

import java.util.UUID;

public record ManualOpenCommand(
		UUID clientId,
		String deviceCode,
		String reason
) {
}
