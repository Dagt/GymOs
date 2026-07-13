package com.gymos.common.security;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gymos.security")
public record SecurityProperties(List<String> corsAllowedOrigins) {
	public SecurityProperties {
		if (corsAllowedOrigins == null || corsAllowedOrigins.isEmpty()) {
			corsAllowedOrigins = List.of("http://localhost:3000");
		}
	}
}
