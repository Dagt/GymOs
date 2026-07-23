package com.gymos.catalog.infrastructure;

import com.gymos.catalog.application.port.CatalogImageStoragePort;
import com.gymos.common.storage.ObjectStorageService;
import java.io.InputStream;
import org.springframework.stereotype.Component;

@Component
class CatalogImageStorageAdapter implements CatalogImageStoragePort {
	private final ObjectStorageService storage;

	CatalogImageStorageAdapter(ObjectStorageService storage) {
		this.storage = storage;
	}

	@Override
	public String store(String objectKey, String contentType, long size, InputStream content) {
		return storage.putObject(objectKey, contentType, size, content);
	}

	@Override
	public void delete(String objectKey) {
		storage.removeObject(objectKey);
	}
}
