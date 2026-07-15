package com.gymos.security.application.result;

public record AuthResult(
		String accessToken,
		String refreshToken,
		String tokenType,
		long expiresIn,
		CurrentUserResult user
) {
}
