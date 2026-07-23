package com.gymos.selfservice.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SelfServiceSessionSecurityTests {
	private final SelfServiceSessionSecurity security = new SelfServiceSessionSecurity();

	@Test
	void generatesUrlSafeRandomTokensAndMatchesOnlyTheirHash() {
		String token = security.newToken();
		String hash = security.hash(token);

		assertThat(token).hasSize(43).matches("[A-Za-z0-9_-]+");
		assertThat(hash).isNotEqualTo(token);
		assertThat(security.matches(token, hash)).isTrue();
		assertThat(security.matches(token + "x", hash)).isFalse();
		assertThat(security.matches(null, hash)).isFalse();
		assertThat(security.matches(token, null)).isFalse();
	}
}
