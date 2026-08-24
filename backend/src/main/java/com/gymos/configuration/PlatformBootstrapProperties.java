package com.gymos.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gymos.platform-bootstrap")
public record PlatformBootstrapProperties(
		boolean enabled,
		String email,
		String password,
		String name
) {
}
