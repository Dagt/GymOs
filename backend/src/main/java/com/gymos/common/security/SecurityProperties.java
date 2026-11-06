package com.gymos.common.security;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

@ConfigurationProperties(prefix = "gymos.security")
public record SecurityProperties(
		List<String> corsAllowedOrigins,
		String jwtSecret,
		long accessTokenSeconds,
		long refreshTokenDays,
		long platformSupportMinutes
) {
	static final String LOCAL_SECRET = "dev-only-change-this-secret-before-production-please";

	public SecurityProperties(List<String> corsAllowedOrigins, String jwtSecret, long accessTokenSeconds, long refreshTokenDays) {
		this(corsAllowedOrigins, jwtSecret, accessTokenSeconds, refreshTokenDays, 30);
	}

	@ConstructorBinding
	public SecurityProperties {
		if (corsAllowedOrigins == null || corsAllowedOrigins.isEmpty()) {
			corsAllowedOrigins = List.of("http://localhost:3000");
		}
		if (jwtSecret == null || jwtSecret.isBlank()) {
			jwtSecret = LOCAL_SECRET;
		}
		if (accessTokenSeconds <= 0) {
			accessTokenSeconds = 900;
		}
		if (refreshTokenDays <= 0) {
			refreshTokenDays = 7;
		}
		if (platformSupportMinutes <= 0) {
			platformSupportMinutes = 30;
		}
	}

	void requireConfiguredSigningSecret() {
		if (jwtSecret.trim().length() < 32 || LOCAL_SECRET.equals(jwtSecret)
				|| "replace-with-64-plus-random-characters".equals(jwtSecret)) {
			throw new IllegalStateException("Configure a strong GYMOS_JWT_SECRET outside local and test profiles");
		}
	}

	@Override
	public String toString() {
		return "SecurityProperties[signing secret redacted]";
	}
}
