package com.gymos.clients.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gymos.audit.api.AuditService;
import com.gymos.clients.api.operations.UpdateSelfServiceContactCommand;
import com.gymos.clients.infrastructure.ClientCommandRepository;
import com.gymos.clients.infrastructure.ClientRepository;
import com.gymos.clients.infrastructure.ClientRepository.SelfServiceClientRecord;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

class ClientSelfServiceContactTests {
	private final ClientRepository queryRepository = mock(ClientRepository.class);
	private final ClientCommandRepository commandRepository = mock(ClientCommandRepository.class);
	private final ClientService service = new ClientService(
			queryRepository, commandRepository, mock(AuditService.class), mock(ApplicationEventPublisher.class));
	private final UUID organizationId = UUID.randomUUID();
	private final UUID clientId = UUID.randomUUID();
	private final UUID personId = UUID.randomUUID();

	@Test
	void omittedPhoneIsPreservedAndReturnedContactIsMasked() {
		var existing = new SelfServiceClientRecord(
				clientId, personId, "Andrea Socia", "andrea@example.com", "+525555551234"
		);
		var updated = new SelfServiceClientRecord(
				clientId, personId, "Andrea Socia", "nuevo@example.com", "+525555551234"
		);
		when(queryRepository.selfServiceProfile(organizationId, clientId))
				.thenReturn(Optional.of(existing), Optional.of(updated));

		var result = service.updateSelfServiceContact(new UpdateSelfServiceContactCommand(
				organizationId, clientId, "nuevo@example.com", null
		));

		ArgumentCaptor<OffsetDateTime> timestamp = ArgumentCaptor.forClass(OffsetDateTime.class);
		verify(commandRepository).updateSelfServiceContact(
				org.mockito.ArgumentMatchers.eq(organizationId),
				org.mockito.ArgumentMatchers.eq(personId),
				org.mockito.ArgumentMatchers.eq("nuevo@example.com"),
				org.mockito.ArgumentMatchers.eq("+525555551234"),
				timestamp.capture()
		);
		assertThat(result.email()).isEqualTo("nu***@example.com");
		assertThat(result.phone()).isEqualTo("***1234");
	}

	@Test
	void profileNeverReturnsFullContactData() {
		when(queryRepository.selfServiceProfile(organizationId, clientId)).thenReturn(Optional.of(
				new SelfServiceClientRecord(clientId, personId, "Andrea Socia", "andrea@example.com", "+525555551234")
		));

		var profile = service.selfServiceProfile(organizationId, clientId).orElseThrow();

		assertThat(profile.email()).isEqualTo("an***@example.com");
		assertThat(profile.phone()).isEqualTo("***1234");
	}
}
