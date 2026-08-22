package com.gymos.notifications.application;

import java.util.List;
import java.util.Locale;
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
			String providerName,
			String host,
			int port,
			String username,
			String password,
			List<String> allowedFromAddresses,
			boolean auth,
			boolean startTls,
			boolean startTlsRequired,
			boolean ssl,
			boolean checkServerIdentity,
			int connectionTimeoutMs,
			int readTimeoutMs,
			int writeTimeoutMs
	) {
		public Smtp {
			providerName = normalize(providerName);
			host = normalize(host);
			username = normalize(username);
			password = password == null ? "" : password;
			allowedFromAddresses = allowedFromAddresses == null ? List.of() : allowedFromAddresses.stream()
					.map(Smtp::normalizeEmail)
					.filter(value -> value != null)
					.distinct()
					.toList();
			port = port <= 0 ? 587 : port;
			connectionTimeoutMs = positive(connectionTimeoutMs);
			readTimeoutMs = positive(readTimeoutMs);
			writeTimeoutMs = positive(writeTimeoutMs);
		}

		public boolean available() {
			return enabled && host != null && (!auth || (username != null && !password.isBlank()));
		}

		public String displayName() {
			if (providerName != null) return providerName;
			if (host != null && host.toLowerCase(Locale.ROOT).contains("zeptomail")) return "ZeptoMail";
			if (host != null && host.toLowerCase(Locale.ROOT).contains("zoho")) return "Zoho Mail";
			return "Servidor SMTP";
		}

		public String preferredFromAddress() {
			if (!allowedFromAddresses.isEmpty()) return allowedFromAddresses.getFirst();
			return username != null && username.contains("@") ? username.toLowerCase(Locale.ROOT) : null;
		}

		public boolean restrictsFromAddresses() {
			return !allowedFromAddresses.isEmpty();
		}

		public boolean allowsFromAddress(String address) {
			if (!restrictsFromAddresses()) return true;
			String normalized = normalizeEmail(address);
			return normalized != null && allowedFromAddresses.contains(normalized);
		}

		private static Smtp disabled() {
			return new Smtp(
					false, "ZeptoMail", null, 587, null, null, List.of(),
					true, true, true, false, true, 10000, 10000, 10000
			);
		}

		private static int positive(int value) {
			return value <= 0 ? 10000 : value;
		}

		private static String normalize(String value) {
			return value == null || value.isBlank() ? null : value.trim();
		}

		private static String normalizeEmail(String value) {
			String normalized = normalize(value);
			return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
		}
	}
}
