package com.gymos.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JwtServiceTests {
	private static final String TEST_SECRET = "test-secret-with-enough-entropy-for-jwt-signing";

	@Test
	void accessTokenPreservesAuthenticatedTenantAndAuthorities() {
		JwtService jwtService = new JwtService(new SecurityProperties(
				List.of("http://localhost:3000"),
				TEST_SECRET,
				900,
				7
		));
		GymOsPrincipal principal = new GymOsPrincipal(
				UUID.randomUUID(),
				UUID.randomUUID(),
				UUID.randomUUID(),
				"owner@gymos.local",
				"Owner GymOS",
				Set.of("organization.read", "branch.read"),
				Set.of("OWNER"),
				false
		);

		var parsed = jwtService.parseAccessToken(jwtService.accessToken(principal));

		assertThat(parsed).contains(principal);
	}

	@Test
	void alteredAccessTokenIsRejected() {
		JwtService jwtService = new JwtService(new SecurityProperties(
				List.of("http://localhost:3000"),
				TEST_SECRET,
				900,
				7
		));
		GymOsPrincipal principal = new GymOsPrincipal(
				UUID.randomUUID(),
				UUID.randomUUID(),
				null,
				"reception@gymos.local",
				"Reception GymOS",
				Set.of("client.read")
		);
		String token = jwtService.accessToken(principal);
		char replacement = token.charAt(token.length() - 1) == 'A' ? 'B' : 'A';
		String altered = token.substring(0, token.length() - 1) + replacement;

		assertThat(jwtService.parseAccessToken(altered)).isEmpty();
	}

	@Test
	void tokenSignedWithAnotherSecretIsRejected() {
		JwtService issuer = new JwtService(new SecurityProperties(
				List.of("http://localhost:3000"),
				TEST_SECRET,
				900,
				7
		));
		JwtService verifier = new JwtService(new SecurityProperties(
				List.of("http://localhost:3000"),
				"different-test-secret-with-enough-entropy",
				900,
				7
		));
		GymOsPrincipal principal = new GymOsPrincipal(
				UUID.randomUUID(),
				UUID.randomUUID(),
				UUID.randomUUID(),
				"manager@gymos.local",
				"Manager GymOS",
				Set.of("branch.read")
		);

		assertThat(verifier.parseAccessToken(issuer.accessToken(principal))).isEmpty();
	}
}
