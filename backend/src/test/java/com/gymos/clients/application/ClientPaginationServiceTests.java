package com.gymos.clients.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.gymos.audit.api.AuditService;
import com.gymos.clients.infrastructure.ClientCommandRepository;
import com.gymos.clients.infrastructure.ClientRepository;
import com.gymos.common.api.BusinessException;
import com.gymos.common.security.GymOsPrincipal;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class ClientPaginationServiceTests {
	private final ClientRepository repository = mock(ClientRepository.class);
	private final ClientService service = new ClientService(repository, mock(ClientCommandRepository.class), mock(AuditService.class), mock(ApplicationEventPublisher.class));

	@Test
	void paginationUsesThePrincipalsTenantAndBranch() {
		var principal = new GymOsPrincipal(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "reader", "Reader", Set.of("client.read"));
		service.searchPage(principal, "Socio", "ACTIVE", 4, 25);
		verify(repository).searchPage(principal.organizationId(), principal.branchId(), "Socio", "ACTIVE", 4, 25);
	}

	@Test
	void rejectsMissingSessionOrBranchBeforeQuerying() {
		assertThatThrownBy(() -> service.searchPage(null, null, null, 0, 25)).isInstanceOf(BusinessException.class);
		var noBranch = new GymOsPrincipal(UUID.randomUUID(), UUID.randomUUID(), null, "reader", "Reader", Set.of("client.read"));
		assertThatThrownBy(() -> service.searchPage(noBranch, null, null, 0, 25)).isInstanceOf(BusinessException.class).hasMessageContaining("sucursal");
		verifyNoInteractions(repository);
	}
}
