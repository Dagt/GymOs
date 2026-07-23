package com.gymos.catalog.application.command;

import java.util.UUID;

public record CategoryCommand(
		UUID parentId,
		String name,
		String description,
		String status,
		int displayOrder
) {
}
