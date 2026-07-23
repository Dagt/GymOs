package com.gymos.selfservice.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.stereotype.Component;

@Component
class SelfServiceSessionSecurity {
	private static final SecureRandom SECURE_RANDOM = new SecureRandom();

	String newToken() {
		byte[] bytes = new byte[32];
		SECURE_RANDOM.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	String hash(String token) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256")
					.digest(token.getBytes(StandardCharsets.UTF_8));
			return Base64.getEncoder().encodeToString(digest);
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is required", exception);
		}
	}

	boolean matches(String presentedToken, String storedHash) {
		if (presentedToken == null || storedHash == null) {
			return false;
		}
		return MessageDigest.isEqual(
				storedHash.getBytes(StandardCharsets.UTF_8),
				hash(presentedToken).getBytes(StandardCharsets.UTF_8)
		);
	}
}
