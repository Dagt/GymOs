package com.gymos.selfservice.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymos.audit.api.AuditService;
import com.gymos.bookings.api.operations.BookingOperations;
import com.gymos.clients.api.operations.ClientLookupOperations;
import com.gymos.clients.api.operations.SelfServiceClientProfile;
import com.gymos.clients.api.operations.UpdateSelfServiceContactCommand;
import com.gymos.common.api.BusinessException;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.common.security.RateLimiter;
import com.gymos.memberships.api.operations.MembershipOperations;
import com.gymos.memberships.api.operations.RenewMembershipOperationCommand;
import com.gymos.selfservice.application.command.VerifySessionCommand;
import com.gymos.selfservice.infrastructure.SelfServiceRepository;
import com.gymos.selfservice.infrastructure.SelfServiceRepository.SessionRecord;
import com.gymos.selfservice.infrastructure.SelfServiceRepository.TerminalRecord;
import com.gymos.selfservice.infrastructure.SelfServiceSessionStatusRepository;
import com.gymos.selfservice.infrastructure.SelfServiceSessionStatusRepository.SessionStatusRecord;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SelfServiceServiceTests {
	private final SelfServiceRepository repository = mock(SelfServiceRepository.class);
	private final SelfServiceSessionStatusRepository sessionStatuses = mock(SelfServiceSessionStatusRepository.class);
	private final MembershipOperations memberships = mock(MembershipOperations.class);
	private final ClientLookupOperations clients = mock(ClientLookupOperations.class);
	private final BookingOperations bookings = mock(BookingOperations.class);
	private final AuditService audit = mock(AuditService.class);
	private final RateLimiter rateLimiter = mock(RateLimiter.class);
	private final SelfServiceVerificationGuard verificationGuard = mock(SelfServiceVerificationGuard.class);
	private final SelfServiceCapabilityPolicy capabilityPolicy =
			new SelfServiceCapabilityPolicy(new ObjectMapper());

	private final UUID sessionId = UUID.randomUUID();
	private final UUID organizationId = UUID.randomUUID();
	private final UUID branchId = UUID.randomUUID();
	private final UUID clientId = UUID.randomUUID();
	private final UUID userId = UUID.randomUUID();
	private final String token = "kiosk-test-token";
	private SelfServiceService service;

	@BeforeEach
	void setUp() {
		service = new SelfServiceService(
				repository,
				sessionStatuses,
				memberships,
				clients,
				bookings,
				audit,
				rateLimiter,
				capabilityPolicy,
				verificationGuard
		);
	}

	@Test
	void receptionSessionUsesAuthenticatedOrganizationAndBranch() {
		String configurationCode = "KIOSK-BRANCH-" + branchId;
		TerminalRecord terminal = new TerminalRecord(
				UUID.randomUUID(),
				organizationId,
				branchId,
				"Autoservicio sucursal",
				configurationCode,
				"ACTIVE",
				"{\"profileUpdate\":true}",
				"Gimnasio Prueba",
				"Marca Prueba",
				null,
				"#111111",
				"#eeeeee",
				"#ffffff"
		);
		when(repository.terminal(configurationCode, organizationId, branchId))
				.thenReturn(Optional.of(terminal));
		GymOsPrincipal principal = new GymOsPrincipal(
				userId,
				organizationId,
				branchId,
				"recepcion",
				"Recepción",
				Set.of("sale.create")
		);

		var result = service.createReceptionSession(principal, "127.0.0.1", "correlation");

		assertThat(result.organizationName()).isEqualTo("Gimnasio Prueba");
		assertThat(result.brandName()).isEqualTo("Marca Prueba");
		verify(repository).terminal(configurationCode, organizationId, branchId);
		verify(repository).createSession(
				any(UUID.class),
				org.mockito.ArgumentMatchers.eq(organizationId),
				org.mockito.ArgumentMatchers.eq(branchId),
				org.mockito.ArgumentMatchers.eq(terminal.id()),
				any(OffsetDateTime.class),
				any(OffsetDateTime.class),
				org.mockito.ArgumentMatchers.eq("127.0.0.1"),
				any(String.class)
		);
		verify(audit).recordSystem(
				org.mockito.ArgumentMatchers.eq(organizationId),
				org.mockito.ArgumentMatchers.eq(branchId),
				org.mockito.ArgumentMatchers.eq(userId),
				org.mockito.ArgumentMatchers.eq("SELF_SERVICE_SESSION_STARTED"),
				org.mockito.ArgumentMatchers.eq("SELF_SERVICE_SESSION"),
				any(UUID.class),
				org.mockito.ArgumentMatchers.eq("SELF_SERVICE_KIOSK"),
				org.mockito.ArgumentMatchers.eq("RECEPTION"),
				org.mockito.ArgumentMatchers.eq("correlation")
		);
	}

	@Test
	void receptionSessionRequiresActiveBranchContext() {
		GymOsPrincipal principal = new GymOsPrincipal(
				userId,
				organizationId,
				null,
				"recepcion",
				"Recepción",
				Set.of("sale.create")
		);

		assertThatThrownBy(() -> service.createReceptionSession(principal, "127.0.0.1", "correlation"))
				.isInstanceOfSatisfying(
						BusinessException.class,
						exception -> assertThat(exception.code()).isEqualTo("BRANCH_REQUIRED")
				);

		verifyNoInteractions(repository);
	}

	@Test
	void renewalCannotCreateManualSaleBeforeCardGatewayIsReady() {
		givenSession("""
				{"membershipRenewal":true}
				""");

		assertThatThrownBy(() -> service.renew(
				sessionId,
				token,
				UUID.randomUUID(),
				new RenewMembershipOperationCommand(
						"CARD_ONLINE",
						null,
						false,
						null,
						"kiosk:test"
				),
				"correlation",
				"127.0.0.1"
		))
				.isInstanceOfSatisfying(
						BusinessException.class,
						exception -> assertThat(exception.code())
								.isEqualTo("SELF_SERVICE_CARD_PAYMENTS_NOT_READY")
				);

		verifyNoInteractions(memberships);
	}

	@Test
	void resourceEndpointEnforcesTerminalCapabilities() {
		givenSession("""
				{"resourceBooking":false}
				""");

		assertThatThrownBy(() -> service.bookingResources(sessionId, token))
				.isInstanceOfSatisfying(
						BusinessException.class,
						exception -> assertThat(exception.code())
								.isEqualTo("SELF_SERVICE_ACTION_NOT_ALLOWED")
				);

		verifyNoInteractions(bookings);
	}

	@Test
	void verifiedMemberCanUpdateContactWhenTerminalAllowsIt() {
		givenSession("""
				{"profileUpdate":true}
				""");
		var updated = new SelfServiceClientProfile(
				clientId,
				"Socio Prueba",
				"socio@example.com",
				"+521234567890"
		);
		when(clients.updateSelfServiceContact(any(UpdateSelfServiceContactCommand.class)))
				.thenReturn(updated);

		var result = service.updateContact(
				sessionId,
				token,
				updated.email(),
				updated.phone(),
				"correlation",
				"127.0.0.1"
		);

		assertThat(result).isEqualTo(updated);
		verify(clients).updateSelfServiceContact(any(UpdateSelfServiceContactCommand.class));
		verify(audit).recordSystem(
				organizationId,
				branchId,
				null,
				"SELF_SERVICE_CONTACT_UPDATED",
				"CLIENT",
				clientId,
				"SELF_SERVICE_KIOSK",
				null,
				"correlation"
		);
	}

	@Test
	void verifyAppliesAggregateIdentityGuard() {
		givenUnverifiedSession();
		when(clients.verifyForSelfService(any())).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.verify(
				sessionId,
				token,
				new VerifySessionCommand("+52 492 290 0619", LocalDate.of(1992, 9, 15)),
				"correlation",
				"127.0.0.1"
		)).isInstanceOf(BusinessException.class);

		verify(verificationGuard).check("+52 492 290 0619");
	}

	@Test
	void closedSessionHasDedicatedError() {
		givenStatus("ENDED", clientId, OffsetDateTime.now().plusMinutes(10), OffsetDateTime.now());

		assertSessionCode("SELF_SERVICE_SESSION_CLOSED");
		verifyNoInteractions(repository);
	}

	@Test
	void expiredSessionHasDedicatedError() {
		givenStatus("ACTIVE", clientId, OffsetDateTime.now().minusSeconds(1), OffsetDateTime.now());

		assertSessionCode("SELF_SERVICE_SESSION_EXPIRED");
		verifyNoInteractions(repository);
	}

	@Test
	void inactiveVerifiedSessionHasDedicatedError() {
		givenStatus("ACTIVE", clientId, OffsetDateTime.now().plusMinutes(10), OffsetDateTime.now().minusMinutes(4));

		assertSessionCode("SELF_SERVICE_SESSION_INACTIVE");
		verifyNoInteractions(repository);
	}

	@Test
	void invalidTokenIsRejectedBeforeSessionStateIsRevealed() {
		givenStatus("ENDED", clientId, OffsetDateTime.now().minusMinutes(1), OffsetDateTime.now().minusMinutes(10));

		assertThatThrownBy(() -> service.summary(sessionId, "incorrect-token"))
				.isInstanceOfSatisfying(
						BusinessException.class,
						exception -> assertThat(exception.code()).isEqualTo("SELF_SERVICE_SESSION_INVALID")
				);
		verifyNoInteractions(repository);
	}

	private void assertSessionCode(String expectedCode) {
		assertThatThrownBy(() -> service.summary(sessionId, token))
				.isInstanceOfSatisfying(
						BusinessException.class,
						exception -> assertThat(exception.code()).isEqualTo(expectedCode)
				);
	}

	private void givenUnverifiedSession() {
		OffsetDateTime now = OffsetDateTime.now();
		when(sessionStatuses.find(sessionId)).thenReturn(Optional.of(new SessionStatusRecord(
				sessionId,
				null,
				"ACTIVE",
				now.plusMinutes(10),
				null,
				now,
				tokenHash(token)
		)));
		when(repository.session(sessionId)).thenReturn(Optional.of(new SessionRecord(
				sessionId,
				organizationId,
				branchId,
				UUID.randomUUID(),
				null,
				"ACTIVE",
				now.minusMinutes(1),
				now.plusMinutes(10),
				null,
				null,
				tokenHash(token),
				0,
				null,
				"{}"
		)));
	}

	private void givenSession(String allowedActionsJson) {
		OffsetDateTime now = OffsetDateTime.now();
		givenStatus("ACTIVE", clientId, now.plusMinutes(10), now);
		when(repository.session(sessionId)).thenReturn(Optional.of(new SessionRecord(
				sessionId,
				organizationId,
				branchId,
				UUID.randomUUID(),
				clientId,
				"ACTIVE",
				now.minusMinutes(1),
				now.plusMinutes(10),
				null,
				"CONTACT_BIRTH_DATE",
				tokenHash(token),
				0,
				null,
				allowedActionsJson
		)));
	}

	private void givenStatus(String status, UUID verifiedClientId, OffsetDateTime expiresAt, OffsetDateTime updatedAt) {
		when(sessionStatuses.find(sessionId)).thenReturn(Optional.of(new SessionStatusRecord(
				sessionId,
				verifiedClientId,
				status,
				expiresAt,
				"ENDED".equals(status) ? updatedAt : null,
				updatedAt,
				tokenHash(token)
		)));
	}

	private String tokenHash(String value) {
		try {
			return Base64.getEncoder().encodeToString(
					MessageDigest.getInstance("SHA-256")
							.digest(value.getBytes(StandardCharsets.UTF_8))
			);
		} catch (Exception exception) {
			throw new IllegalStateException(exception);
		}
	}
}
