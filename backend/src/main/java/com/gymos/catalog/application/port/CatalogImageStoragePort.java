package com.gymos.catalog.application.port;

import java.io.InputStream;

public interface CatalogImageStoragePort {
	String store(String objectKey, String contentType, long size, InputStream content);

	void delete(String objectKey);
}
