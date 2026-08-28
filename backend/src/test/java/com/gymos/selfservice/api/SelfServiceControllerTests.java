package com.gymos.selfservice.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.gymos.common.security.ClientIpResolver;
import com.gymos.selfservice.application.SelfServiceService;
import com.gymos.visitpackages.api.operations.VisitPackageOperations.VisitPackageEntitlementView;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class SelfServiceControllerTests {
	private final SelfServiceService service = mock(SelfServiceService.class);
	private final SelfServiceController controller = new SelfServiceController(
			service, mock(ClientIpResolver.class));

	@Test
	void currentVisitPackageReturnsNoContentWhenClientHasNoPackage() {
		UUID sessionId = UUID.randomUUID();
		when(service.currentVisitPackage(sessionId, "session-token")).thenReturn(null);

		var response = controller.currentVisitPackage(sessionId, "session-token");

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
		assertThat(response.getBody()).isNull();
	}

	@Test
	void currentVisitPackageReturnsEntitlementWhenOneIsOpen() {
		UUID sessionId = UUID.randomUUID();
		VisitPackageEntitlementView entitlement = new VisitPackageEntitlementView(
				UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
				"10 visitas", "PENDING_PAYMENT", 10, 10, null, null,
				OffsetDateTime.now().plusMinutes(30), List.of(UUID.randomUUID()), List.of(),
				OffsetDateTime.now(), OffsetDateTime.now());
		when(service.currentVisitPackage(sessionId, "session-token")).thenReturn(entitlement);

		var response = controller.currentVisitPackage(sessionId, "session-token");

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).isSameAs(entitlement);
	}
}
