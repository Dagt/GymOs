package com.gymos.catalog.application.port;

import java.time.OffsetDateTime;

public interface CatalogStorageCleanupPort {
	void enqueue(String objectKey, OffsetDateTime now);
}
