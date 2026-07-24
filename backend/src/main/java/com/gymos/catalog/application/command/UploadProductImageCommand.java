package com.gymos.catalog.application.command;

import java.io.InputStream;

public record UploadProductImageCommand(
		String filename,
		String contentType,
		long size,
		InputStream content
) {
}
