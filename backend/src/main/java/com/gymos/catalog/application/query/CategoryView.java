package com.gymos.catalog.application.query;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CategoryView(
		UUID id,
		UUID parentId,
		String name,
		String slug,
		String description,
		String status,
		int displayOrder,
		OffsetDateTime createdAt,
		OffsetDateTime updatedAt
) {
}
