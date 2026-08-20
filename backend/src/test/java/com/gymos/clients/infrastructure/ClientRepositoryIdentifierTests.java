package com.gymos.clients.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ClientRepositoryIdentifierTests {
	@Test
	void normalizesEmailForSelfServiceLookup() {
		var identifiers = ClientRepository.selfServiceIdentifiers("  SOCIO@EXAMPLE.COM ");

		assertThat(identifiers.email()).isEqualTo("socio@example.com");
		assertThat(identifiers.phone()).isEmpty();
	}

	@Test
	void acceptsMexicanLocalInternationalAndLegacyPhoneFormats() {
		var local = ClientRepository.selfServiceIdentifiers("55 5555 1234");
		var international = ClientRepository.selfServiceIdentifiers("+52 (55) 5555-1234");
		var legacyMobile = ClientRepository.selfServiceIdentifiers("+52 1 55 5555 1234");

		assertThat(local.phone()).isEqualTo("525555551234");
		assertThat(international).isEqualTo(local);
		assertThat(legacyMobile).isEqualTo(local);
		assertThat(local.localPhone()).isEqualTo("5555551234");
		assertThat(local.legacyPhone()).isEqualTo("5215555551234");
	}
}
