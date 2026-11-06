package com.gymos.security.application.result;

public record AuthResult(
		String accessToken,
		String refreshToken,
		String tokenType,
		long expiresIn,
		java.time.Instant sessionExpiresAt,
		CurrentUserResult user
) {
	@Override
	public String toString() {
		return "AuthResult[credentials redacted]";
	}
}
