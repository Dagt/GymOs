package com.gymos.clients.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.gymos.clients.application.SelfServiceIdentifierNormalizer.IdentifierType;
import org.junit.jupiter.api.Test;

class SelfServiceIdentifierNormalizerTests {
	@Test
	void normalizesEmailIgnoringCaseAndOuterWhitespace() {
		var result = SelfServiceIdentifierNormalizer.normalize("  SOCIO@Example.COM ");

		assertThat(result.type()).isEqualTo(IdentifierType.EMAIL);
		assertThat(result.value()).isEqualTo("socio@example.com");
		assertThat(result.valid()).isTrue();
	}

	@Test
	void normalizesCommonMexicanPhoneFormatsToTenDigits() {
		assertThat(SelfServiceIdentifierNormalizer.normalize("492 290 0619").value()).isEqualTo("4922900619");
		assertThat(SelfServiceIdentifierNormalizer.normalize("+52 492-290-0619").value()).isEqualTo("4922900619");
		assertThat(SelfServiceIdentifierNormalizer.normalize("+52 1 492 290 0619").value()).isEqualTo("4922900619");
	}

	@Test
	void rejectsIncompletePhoneValues() {
		assertThat(SelfServiceIdentifierNormalizer.normalize("492290").valid()).isFalse();
	}
}
