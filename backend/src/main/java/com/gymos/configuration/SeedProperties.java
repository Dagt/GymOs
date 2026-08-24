package com.gymos.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gymos.seed")
public record SeedProperties(
		boolean enabled,
		String ownerEmail,
		String ownerPassword
) {
}
