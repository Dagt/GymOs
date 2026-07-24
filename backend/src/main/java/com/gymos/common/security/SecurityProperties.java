package com.gymos.common.security;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

@ConfigurationProperties(prefix = "gymos.security")
public record SecurityProperties(
		List<String> corsAllowedOrigins,
		String jwtSecret,
		long accessTokenSeconds,
		long receptionAccessTokenSeconds,
		long refreshTokenDays
) {
	public SecurityProperties(List<String> corsAllowedOrigins, String jwtSecret, long accessTokenSeconds, long refreshTokenDays) {
		this(corsAllowedOrigins, jwtSecret, accessTokenSeconds, 57_600, refreshTokenDays);
	}

	@ConstructorBinding
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
		if (receptionAccessTokenSeconds <= 0) {
			receptionAccessTokenSeconds = 57_600;
		}
		if (refreshTokenDays <= 0) {
			refreshTokenDays = 7;
		}
	}
}
