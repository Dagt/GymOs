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
import com.gymos.bookings.api.operations.BookingOperations.BookingResource;
import com.gymos.clients.api.operations.ClientLookupOperations;
import com.gymos.clients.api.operations.SelfServiceClientProfile;
import com.gymos.clients.api.operations.UpdateSelfServiceContactCommand;
import com.gymos.common.api.BusinessException;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.common.security.RateLimiter;
import com.gymos.memberships.api.operations.MembershipOperations;
import com.gymos.memberships.api.operations.MembershipEnrollmentOperations;
import com.gymos.memberships.api.operations.MembershipSaleOperationResult;
import com.gymos.memberships.api.operations.RenewMembershipOperationCommand;
import com.gymos.payments.api.PaymentConfigurationView;
import com.gymos.payments.api.PaymentConfigurationOperations;
import com.gymos.payments.api.StripeCheckoutOperations;
import com.gymos.payments.api.StripeRecurringOperations;
import com.gymos.payments.api.StripeRecurringOperations.BeginMandateSetupCommand;
import com.gymos.payments.api.StripeRecurringOperations.MandateSetupResult;
import com.gymos.selfservice.infrastructure.SelfServiceRepository;
import com.gymos.selfservice.infrastructure.SelfServiceRepository.AutopayMembershipRecord;
import com.gymos.selfservice.infrastructure.SelfServiceRepository.SessionRecord;
import com.gymos.selfservice.infrastructure.SelfServiceRepository.TerminalRecord;
import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.time.LocalDate;
import java.util.Base64;
import java.util.Optional;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.annotation.Transactional;

class SelfServiceServiceTests {
	private final SelfServiceRepository repository = mock(SelfServiceRepository.class);
	private final MembershipOperations memberships = mock(MembershipOperations.class);
	private final MembershipEnrollmentOperations membershipEnrollments = mock(MembershipEnrollmentOperations.class);
	private final ClientLookupOperations clients = mock(ClientLookupOperations.class);
	private final BookingOperations bookings = mock(BookingOperations.class);
	private final AuditService audit = mock(AuditService.class);
	private final RateLimiter rateLimiter = mock(RateLimiter.class);
	private final SelfServiceCapabilityPolicy capabilityPolicy =
			new SelfServiceCapabilityPolicy(new ObjectMapper());
	private final PaymentConfigurationOperations paymentConfigurationService = mock(PaymentConfigurationOperations.class);
	private final StripeCheckoutOperations stripeCheckoutOperations = mock(StripeCheckoutOperations.class);
	private final StripeRecurringOperations stripeRecurringOperations = mock(StripeRecurringOperations.class);

	private final UUID sessionId = UUID.randomUUID();
	private final UUID organizationId = UUID.randomUUID();
	private final UUID branchId = UUID.randomUUID();
	private final UUID clientId = UUID.randomUUID();
	private final String token = "kiosk-test-token";
	private SelfServiceService service;

	@BeforeEach
	void setUp() {
		when(paymentConfigurationService.configuration(organizationId)).thenReturn(new PaymentConfigurationView(
				true, false, null, null, false, false, false, null, false
		));
		service = new SelfServiceService(
				repository,
				memberships,
				membershipEnrollments,
				clients,
				bookings,
				audit,
				rateLimiter,
				capabilityPolicy,
				paymentConfigurationService,
				stripeCheckoutOperations,
				stripeRecurringOperations
		);
	}

	@Test
	void membershipQuoteUsesWritableTransactionBecauseSessionValidationUpdatesActivity() throws Exception {
		Transactional transaction = SelfServiceService.class
				.getMethod("membershipQuote", UUID.class, String.class, UUID.class)
				.getAnnotation(Transactional.class);

		assertThat(transaction).isNotNull();
		assertThat(transaction.readOnly()).isFalse();
	}

	@Test
	void verifiedMemberWithoutMembershipCanStartEnrollmentCheckout() {
		givenSession("""
				{"membershipEnrollment":true}
				""");
		when(paymentConfigurationService.configuration(organizationId)).thenReturn(new PaymentConfigurationView(
				true, false, null, null, false, true, false, "acct_test", true
		));
		UUID planId = UUID.randomUUID();
		UUID saleId = UUID.randomUUID();
		UUID paymentId = UUID.randomUUID();
		UUID operationId = UUID.randomUUID();
		when(membershipEnrollments.enroll(any(), any(), any())).thenReturn(new MembershipSaleOperationResult(
				saleId, "V-001", "PENDING_PAYMENT", 75000, 0, 75000, "MXN", null, paymentId
		));
		when(stripeCheckoutOperations.createCheckout(any())).thenReturn(
				new StripeCheckoutOperations.CheckoutSessionResult(
						operationId, "cs_test", "https://checkout.stripe.test/session", "OPEN"
				)
		);

		var result = service.enroll(
				sessionId,
				token,
				planId,
				"kiosk:enroll:test",
				"correlation",
				"127.0.0.1"
		);

		assertThat(result.saleId()).isEqualTo(saleId);
		assertThat(result.paymentId()).isEqualTo(paymentId);
		assertThat(result.providerOperationId()).isEqualTo(operationId);
		assertThat(result.checkoutUrl()).isEqualTo("https://checkout.stripe.test/session");
		verify(membershipEnrollments).enroll(any(), any(), any());
		verify(stripeCheckoutOperations).createCheckout(any());
	}

	@Test
	void launchCreatesLogicalBranchProfileAndKeepsBrowserModeForTwelveHours() {
		UUID userId = UUID.randomUUID();
		GymOsPrincipal principal = new GymOsPrincipal(
				userId,
				organizationId,
				branchId,
				"recepcion@example.com",
				"Recepcion",
				Set.of("selfservice.session.launch")
		);
		when(repository.defaultTerminal(organizationId, branchId)).thenReturn(Optional.of(new TerminalRecord(
				UUID.randomUUID(), organizationId, branchId, "Kiosco de sucursal", "KIOSK-MAIN", "ACTIVE",
				"{\"profileUpdate\":true}", "Gym", "Gym", null, "#112233", "#445566", "#ffffff"
		)));
		OffsetDateTime before = OffsetDateTime.now();

		var result = service.launchSession(principal, "127.0.0.1", "correlation");

		verify(repository).ensureDefaultTerminal(organizationId, branchId, userId);
		assertThat(Duration.between(before, result.expiresAt()).toHours()).isEqualTo(12);
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
	void renewalOfMultiBranchMembershipMustBeManagedAtItsOriginBranch() {
		givenSession("""
				{"membershipRenewal":true}
				""");
		when(paymentConfigurationService.configuration(organizationId)).thenReturn(new PaymentConfigurationView(
				true, false, null, null, false, true, false, "acct_test", true
		));
		UUID membershipId = UUID.randomUUID();
		when(repository.membershipBelongsToBranch(organizationId, branchId, clientId, membershipId))
				.thenReturn(false);

		assertThatThrownBy(() -> service.renew(
				sessionId,
				token,
				membershipId,
				new RenewMembershipOperationCommand(
						"CARD_ONLINE", null, false, null, "kiosk:test:remote"
				),
				"correlation",
				"127.0.0.1"
		))
				.isInstanceOfSatisfying(
						BusinessException.class,
						exception -> assertThat(exception.code())
								.isEqualTo("MEMBERSHIP_HOME_BRANCH_REQUIRED")
				);

		verifyNoInteractions(memberships);
	}

	@Test
	void resourceEndpointEnforcesTerminalCapabilities() {
		givenSession("""
				{"resourceBooking":false}
				""");

		assertThatThrownBy(() -> service.bookingResources(sessionId, token, LocalDate.now()))
				.isInstanceOfSatisfying(
						BusinessException.class,
						exception -> assertThat(exception.code())
								.isEqualTo("SELF_SERVICE_ACTION_NOT_ALLOWED")
				);

		verifyNoInteractions(bookings);
	}

	@Test
	void resourceCatalogIsRequestedForTheVerifiedClientAndSelectedDate() {
		givenSession("""
				{"resourceBooking":true}
				""");
		LocalDate selectedDate = LocalDate.now().plusDays(2);
		when(bookings.eligibleResources(organizationId, branchId, clientId, selectedDate))
				.thenReturn(List.<BookingResource>of());

		assertThat(service.bookingResources(sessionId, token, selectedDate)).isEmpty();

		verify(bookings).eligibleResources(organizationId, branchId, clientId, selectedDate);
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
	void verifiedMemberCanAuthorizeTokenizedAutopayForOwnActiveMembership() {
		givenSession("""
				{"autopay":true}
				""");
		when(paymentConfigurationService.configuration(organizationId)).thenReturn(new PaymentConfigurationView(
				true, false, null, null, false, true, true, "acct_test", true
		));
		UUID membershipId = UUID.randomUUID();
		UUID planId = UUID.randomUUID();
		LocalDate nextChargeOn = LocalDate.now().plusDays(20);
		when(repository.autopayMembership(organizationId, branchId, clientId, membershipId))
				.thenReturn(Optional.of(new AutopayMembershipRecord(
						membershipId, planId, "Mensual", "ACTIVE", nextChargeOn, 75_000, "MXN"
				)));
		MandateSetupResult expected = new MandateSetupResult(
				UUID.randomUUID(), "PENDING_SETUP", UUID.randomUUID(), "cs_setup",
				"https://checkout.stripe.test/setup", "2026-08-03", nextChargeOn
		);
		when(stripeRecurringOperations.beginSetup(any())).thenReturn(expected);

		MandateSetupResult result = service.setupAutopay(
				sessionId, token, membershipId, "2026-08-03", true,
				"kiosk-autopay:test", "correlation", "127.0.0.1"
		);

		assertThat(result).isEqualTo(expected);
		ArgumentCaptor<BeginMandateSetupCommand> command = ArgumentCaptor.forClass(BeginMandateSetupCommand.class);
		verify(stripeRecurringOperations).beginSetup(command.capture());
		assertThat(command.getValue().accepted()).isTrue();
		assertThat(command.getValue().context().clientId()).isEqualTo(clientId);
		assertThat(command.getValue().context().membershipId()).isEqualTo(membershipId);
		assertThat(command.getValue().context().nextChargeOn()).isEqualTo(nextChargeOn);
	}

	private void givenSession(String allowedActionsJson) {
		when(repository.session(sessionId)).thenReturn(Optional.of(new SessionRecord(
				sessionId,
				organizationId,
				branchId,
				UUID.randomUUID(),
				clientId,
				"ACTIVE",
				OffsetDateTime.now().minusMinutes(1),
				OffsetDateTime.now().plusMinutes(10),
				null,
				"CONTACT_BIRTH_DATE",
				tokenHash(token),
				0,
				null,
				OffsetDateTime.now(),
				UUID.randomUUID(),
				allowedActionsJson
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
