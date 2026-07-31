package com.gymos.selfservice.application;

import com.gymos.clients.application.SelfServiceIdentifierNormalizer;
import com.gymos.common.security.RateLimiter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class SelfServiceVerificationGuard {
	private final RateLimiter rateLimiter;

	public SelfServiceVerificationGuard(RateLimiter rateLimiter) {
		this.rateLimiter = rateLimiter;
	}

	public void check(UUID organizationId, UUID branchId, String identifier) {
		var normalized = SelfServiceIdentifierNormalizer.normalize(identifier);
		String fingerprint = fingerprint(organizationId, branchId, normalized.type().name(), normalized.value());
		rateLimiter.check("kiosk:verify:identity:" + fingerprint, 10, Duration.ofMinutes(15));
	}

	private String fingerprint(UUID organizationId, UUID branchId, String type, String value) {
		String input = organizationId + ":" + branchId + ":" + type + ":" + value;
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
			return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is required", exception);
		}
	}
}
