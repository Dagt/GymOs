package com.gymos.common.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gymos.storage")
public record StorageProperties(
		String endpoint,
		String accessKey,
		String secretKey,
		String bucket
) {
}
