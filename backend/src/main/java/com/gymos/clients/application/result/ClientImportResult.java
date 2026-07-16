package com.gymos.clients.application.result;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record ClientImportResult(
		UUID batchId,
		boolean dryRun,
		String status,
		int totalRows,
		int createdCount,
		int skippedCount,
		int errorCount,
		OffsetDateTime createdAt,
		List<ClientImportRowResult> rows
) {
}
