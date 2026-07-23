package com.gymos.catalog.infrastructure;

import com.gymos.catalog.application.port.CatalogImageStoragePort;
import java.time.Duration;
import java.time.OffsetDateTime;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class CatalogStorageCleanupWorker {
	private static final int BATCH_SIZE = 20;
	private static final Duration MAX_BACKOFF = Duration.ofHours(24);

	private final CatalogStorageCleanupRepository cleanups;
	private final CatalogImageStoragePort storage;

	CatalogStorageCleanupWorker(CatalogStorageCleanupRepository cleanups, CatalogImageStoragePort storage) {
		this.cleanups = cleanups;
		this.storage = storage;
	}

	@Scheduled(fixedDelayString = "${gymos.catalog.storage-cleanup-delay-ms:60000}")
	@Transactional
	public void processPending() {
		OffsetDateTime now = OffsetDateTime.now();
		for (CatalogStorageCleanupRepository.CleanupTask task : cleanups.pending(now, BATCH_SIZE)) {
			try {
				storage.delete(task.objectKey());
				cleanups.complete(task.id(), OffsetDateTime.now());
			} catch (RuntimeException exception) {
				int attempts = task.attempts() + 1;
				Duration backoff = Duration.ofMinutes(1L << Math.min(attempts - 1, 10));
				if (backoff.compareTo(MAX_BACKOFF) > 0) backoff = MAX_BACKOFF;
				OffsetDateTime failedAt = OffsetDateTime.now();
				cleanups.retry(task.id(), attempts, failedAt.plus(backoff), exception.getMessage(), failedAt);
			}
		}
	}
}
