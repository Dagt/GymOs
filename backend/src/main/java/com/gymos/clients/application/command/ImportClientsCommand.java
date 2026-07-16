package com.gymos.clients.application.command;

public record ImportClientsCommand(
		String fileName,
		String csvText,
		boolean dryRun
) {
}
