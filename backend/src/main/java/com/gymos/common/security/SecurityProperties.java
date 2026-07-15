package com.gymos.common.security;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gymos.security")
public record SecurityProperties(
		List<String> corsAllowedOrigins,
		String jwtSecret,
		long accessTokenSeconds,
		long refreshTokenDays
) {
	public SecurityProperties {
		if (corsAllowedOrigins == null || corsAllowedOrigins.isEmpty()) {
			corsAllowedOrigins = List.of("http://localhost:3000");
		}
		if (jwtSecret == null || jwtSecret.isBlank()) {
			jwtSecret = "dev-only-change-this-secret-before-production-please";
		}
		if (accessTokenSeconds <= 0) {
			accessTokenSeconds = 900;
		}
		if (refreshTokenDays <= 0) {
			refreshTokenDays = 7;
		}
	}
}
