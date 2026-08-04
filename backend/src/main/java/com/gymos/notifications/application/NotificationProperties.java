package com.gymos.notifications.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gymos.notifications")
public record NotificationProperties(
		long dispatchDelayMs,
		long operationsMonitorDelayMs,
		int batchSize,
		int leaseSeconds,
		int maxAttempts,
		String frontendBaseUrl,
		String preferenceKey,
		Smtp smtp
) {
	public NotificationProperties {
		batchSize = batchSize <= 0 ? 50 : Math.min(batchSize, 500);
		leaseSeconds = leaseSeconds <= 0 ? 120 : leaseSeconds;
		maxAttempts = maxAttempts <= 0 ? 5 : Math.min(maxAttempts, 20);
		frontendBaseUrl = frontendBaseUrl == null || frontendBaseUrl.isBlank()
				? "http://localhost:3000" : frontendBaseUrl.stripTrailing();
		preferenceKey = preferenceKey == null || preferenceKey.isBlank()
				? "dev-only-notification-preference-key-change-in-production" : preferenceKey;
		smtp = smtp == null ? Smtp.disabled() : smtp;
	}

	public record Smtp(
			boolean enabled,
			String host,
			int port,
			String username,
			String password,
			boolean auth,
			boolean startTls,
			boolean ssl,
			int connectionTimeoutMs,
			int readTimeoutMs,
			int writeTimeoutMs
	) {
		public Smtp {
			host = normalize(host);
			username = normalize(username);
			password = password == null ? "" : password;
			port = port <= 0 ? 587 : port;
			connectionTimeoutMs = positive(connectionTimeoutMs);
			readTimeoutMs = positive(readTimeoutMs);
			writeTimeoutMs = positive(writeTimeoutMs);
		}

		public boolean available() {
			return enabled && host != null && (!auth || (username != null && !password.isBlank()));
		}

		private static Smtp disabled() {
			return new Smtp(false, null, 587, null, null, true, true, false, 10000, 10000, 10000);
		}

		private static int positive(int value) {
			return value <= 0 ? 10000 : value;
		}

		private static String normalize(String value) {
			return value == null || value.isBlank() ? null : value.trim();
		}
	}
}
