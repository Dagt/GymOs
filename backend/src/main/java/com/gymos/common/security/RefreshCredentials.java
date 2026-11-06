package com.gymos.common.security;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
public class RefreshCredentials {
	private final byte[] signingKey;

	public RefreshCredentials(SecurityProperties properties) {
		this.signingKey = properties.jwtSecret().getBytes(StandardCharsets.UTF_8);
	}

	public String issue(UUID sessionId, long generation) {
		String value = sessionId + "." + generation;
		return value + "." + sign(value);
	}

	public Optional<Credential> parse(String token) {
		if (token == null || token.length() > 100) return Optional.empty();
		String[] parts = token.split("\\.", -1);
		if (parts.length != 3 || parts[0].length() != 36 || parts[2].length() != 43) return Optional.empty();
		try {
			UUID id = UUID.fromString(parts[0]);
			long generation = Long.parseLong(parts[1]);
			if (generation < 0 || !MessageDigest.isEqual(issue(id, generation).getBytes(StandardCharsets.US_ASCII),
					token.getBytes(StandardCharsets.US_ASCII))) return Optional.empty();
			return Optional.of(new Credential(id, generation));
		} catch (IllegalArgumentException invalid) {
			return Optional.empty();
		}
	}

	private String sign(String value) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(signingKey, "HmacSHA256"));
			return Base64.getUrlEncoder().withoutPadding()
					.encodeToString(mac.doFinal(("gymfort-refresh-v3:" + value).getBytes(StandardCharsets.US_ASCII)));
		} catch (GeneralSecurityException error) {
			throw new IllegalStateException("Refresh signing is unavailable", error);
		}
	}

	public record Credential(UUID sessionId, long generation) {
	}
}
