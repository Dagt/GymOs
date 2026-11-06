package com.gymos.selfservice.application;

import com.gymos.audit.api.AuditService;
import com.gymos.bookings.api.operations.BookingOperations;
import com.gymos.bookings.api.operations.BookingOperations.BookingAvailability;
import com.gymos.bookings.api.operations.BookingOperations.BookingResource;
import com.gymos.bookings.api.operations.BookingOperations.BookingResult;
import com.gymos.catalog.api.VisitPackagePlanOperations;
import com.gymos.catalog.api.VisitPackagePlanOperations.VisitPackagePlanSnapshot;
import com.gymos.clients.api.operations.ClientLookupOperations;
import com.gymos.clients.api.operations.RegisterSelfServiceClientCommand;
import com.gymos.clients.api.operations.SelfServiceClientProfile;
import com.gymos.clients.api.operations.UpdateSelfServiceContactCommand;
import com.gymos.clients.api.operations.VerifyClientIdentityCommand;
import com.gymos.common.api.BusinessException;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.common.security.RateLimiter;
import com.gymos.memberships.api.operations.ChangeMembershipPlanOperationCommand;
import com.gymos.memberships.api.operations.EnrollMembershipOperationCommand;
import com.gymos.memberships.api.operations.MembershipEnrollmentOperations;
import com.gymos.memberships.api.operations.MembershipOperations;
import com.gymos.memberships.api.operations.MembershipPricingOperations;
import com.gymos.memberships.api.operations.MembershipPricingOperations.MembershipQuoteView;
import com.gymos.memberships.api.operations.MembershipSaleOperationResult;
import com.gymos.memberships.api.operations.RenewMembershipOperationCommand;
import com.gymos.payments.api.PaymentConfigurationOperations;
import com.gymos.payments.api.StripeCheckoutOperations;
import com.gymos.payments.api.StripeRecurringOperations;
import com.gymos.payments.api.StripeRecurringOperations.BeginMandateSetupCommand;
import com.gymos.payments.api.StripeRecurringOperations.MandateSetupResult;
import com.gymos.payments.api.StripeRecurringOperations.RecurringMandateView;
import com.gymos.payments.api.StripeRecurringOperations.RecurringMembershipContext;
import com.gymos.payments.api.StripeRecurringOperations.RecurringTerms;
import com.gymos.selfservice.application.command.VerifySessionCommand;
import com.gymos.selfservice.application.result.CreateSessionResult;
import com.gymos.selfservice.application.result.SelfServiceMembershipSaleResult;
import com.gymos.selfservice.application.result.SelfServiceMembershipSummaryResult;
import com.gymos.selfservice.application.result.SelfServiceCapabilities;
import com.gymos.selfservice.application.result.SelfServiceSummaryResult;
import com.gymos.selfservice.application.result.SelfServiceVisitPackageSaleResult;
import com.gymos.selfservice.infrastructure.SelfServiceRepository;
import com.gymos.selfservice.infrastructure.SelfServiceRepository.MembershipRecord;
import com.gymos.selfservice.infrastructure.SelfServiceRepository.AutopayMembershipRecord;
import com.gymos.selfservice.infrastructure.SelfServiceRepository.PendingPaymentRecord;
import com.gymos.selfservice.infrastructure.SelfServiceRepository.PlanOptionRecord;
import com.gymos.selfservice.infrastructure.SelfServiceRepository.SessionRecord;
import com.gymos.selfservice.infrastructure.SelfServiceRepository.TerminalRecord;
import com.gymos.visitpackages.api.operations.VisitPackageOperations;
import com.gymos.visitpackages.api.operations.VisitPackageOperations.PurchaseVisitPackageCommand;
import com.gymos.visitpackages.api.operations.VisitPackageOperations.VisitPackageEntitlementView;
import com.gymos.visitpackages.api.operations.VisitPackageOperations.VisitPackagePurchaseResult;
import com.gymos.visitpackages.api.operations.VisitPackageOperations.VisitPackageQuoteView;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SelfServiceService {
	private static final Duration VERIFIED_INACTIVITY_TIMEOUT = Duration.ofMinutes(3);
	private static final Duration BROWSER_MODE_MAX_DURATION = Duration.ofHours(12);
	private static final int MAX_VERIFICATION_ATTEMPTS = 5;
	private static final Duration BLOCK_DURATION = Duration.ofMinutes(10);
	private static final SecureRandom SECURE_RANDOM = new SecureRandom();

	private final SelfServiceRepository repository;
	private final MembershipOperations membershipOperations;
	private final MembershipEnrollmentOperations membershipEnrollmentOperations;
	private final MembershipPricingOperations membershipPricingOperations;
	private final ClientLookupOperations clientLookupOperations;
	private final BookingOperations bookingOperations;
	private final AuditService auditService;
	private final RateLimiter rateLimiter;
	private final SelfServiceCapabilityPolicy capabilityPolicy;
	private final PaymentConfigurationOperations paymentConfigurationService;
	private final StripeCheckoutOperations stripeCheckoutOperations;
	private final StripeRecurringOperations stripeRecurringOperations;
	private final VisitPackagePlanOperations visitPackagePlans;
	private final VisitPackageOperations visitPackages;

	@Autowired
	public SelfServiceService(
			SelfServiceRepository repository,
			MembershipOperations membershipOperations,
			MembershipEnrollmentOperations membershipEnrollmentOperations,
			MembershipPricingOperations membershipPricingOperations,
			ClientLookupOperations clientLookupOperations,
			BookingOperations bookingOperations,
			AuditService auditService,
			RateLimiter rateLimiter,
			SelfServiceCapabilityPolicy capabilityPolicy,
			PaymentConfigurationOperations paymentConfigurationService,
			StripeCheckoutOperations stripeCheckoutOperations,
			StripeRecurringOperations stripeRecurringOperations,
			VisitPackagePlanOperations visitPackagePlans,
			VisitPackageOperations visitPackages
	) {
		this.repository = repository;
		this.membershipOperations = membershipOperations;
		this.membershipEnrollmentOperations = membershipEnrollmentOperations;
		this.membershipPricingOperations = membershipPricingOperations;
		this.clientLookupOperations = clientLookupOperations;
		this.bookingOperations = bookingOperations;
		this.auditService = auditService;
		this.rateLimiter = rateLimiter;
		this.capabilityPolicy = capabilityPolicy;
		this.paymentConfigurationService = paymentConfigurationService;
		this.stripeCheckoutOperations = stripeCheckoutOperations;
		this.stripeRecurringOperations = stripeRecurringOperations;
		this.visitPackagePlans = visitPackagePlans;
		this.visitPackages = visitPackages;
	}

	SelfServiceService(
			SelfServiceRepository repository,
			MembershipOperations membershipOperations,
			MembershipEnrollmentOperations membershipEnrollmentOperations,
			ClientLookupOperations clientLookupOperations,
			BookingOperations bookingOperations,
			AuditService auditService,
			RateLimiter rateLimiter,
			SelfServiceCapabilityPolicy capabilityPolicy,
			PaymentConfigurationOperations paymentConfigurationService,
			StripeCheckoutOperations stripeCheckoutOperations,
			StripeRecurringOperations stripeRecurringOperations
	) {
		this(repository, membershipOperations, membershipEnrollmentOperations,
				(principal, clientId, planId) -> { throw new UnsupportedOperationException("Pricing not configured in test."); },
				clientLookupOperations, bookingOperations, auditService, rateLimiter, capabilityPolicy,
				paymentConfigurationService, stripeCheckoutOperations, stripeRecurringOperations, null, null);
	}

	@Transactional
	public CreateSessionResult launchSession(GymOsPrincipal principal, String originIp, String correlationId) {
		requireLaunchContext(principal);
		rateLimiter.check("kiosk:launch:user:" + principal.userId(), 10, Duration.ofMinutes(1));
		repository.ensureDefaultTerminal(principal.organizationId(), principal.branchId(), principal.userId());
		TerminalRecord terminal = repository.defaultTerminal(principal.organizationId(), principal.branchId())
				.orElseThrow(() -> new BusinessException("SELF_SERVICE_TERMINAL_NOT_FOUND", "El kiosco no esta configurado para esta sucursal."));
		return createSession(terminal, principal.userId(), originIp, correlationId);
	}

	private CreateSessionResult createSession(TerminalRecord terminal, UUID launchedByUserId, String originIp, String correlationId) {
		OffsetDateTime now = OffsetDateTime.now();
		OffsetDateTime browserExpiresAt = now.plus(BROWSER_MODE_MAX_DURATION);
		UUID sessionId = UUID.randomUUID();
		String sessionToken = randomToken();
		repository.createSession(sessionId, terminal.organizationId(), terminal.branchId(), terminal.id(), launchedByUserId, now, browserExpiresAt, originIp, tokenHash(sessionToken));
		auditService.recordSystem(terminal.organizationId(), terminal.branchId(), launchedByUserId, "SELF_SERVICE_SESSION_STARTED", "SELF_SERVICE_SESSION", sessionId, "SELF_SERVICE_KIOSK", "BROWSER_MODE", correlationId);
		return new CreateSessionResult(
				sessionId,
				sessionToken,
				terminal.name(),
				terminal.tradeName(),
				terminal.brandName(),
				terminal.logoUrl(),
				terminal.primaryColor(),
				terminal.accentColor(),
				terminal.surfaceColor(),
				browserExpiresAt,
				false,
				capabilities(terminal.organizationId(), terminal.branchId(), terminal.allowedActionsJson())
		);
	}

	@Transactional
	public SelfServiceSummaryResult verify(UUID sessionId, String sessionToken, VerifySessionCommand request, String correlationId, String originIp) {
		rateLimiter.check("kiosk:verify:ip:" + originIp, 60, Duration.ofMinutes(1));
		rateLimiter.check("kiosk:verify:session:" + sessionId, 10, Duration.ofMinutes(1));
		SessionRecord session = activeSession(sessionId, sessionToken);
		rateLimiter.check(
				"kiosk:verify:identity:" + session.terminalId() + ":" + tokenHash(request.identifier().trim().toLowerCase()),
				10,
				Duration.ofMinutes(10)
		);
		ensureNotBlocked(session);
		if (session.clientId() != null) {
			return summary(sessionId, sessionToken);
		}
		var client = clientLookupOperations.verifyForSelfService(new VerifyClientIdentityCommand(session.organizationId(), session.branchId(), request.identifier(), request.birthDate())).orElse(null);
		if (client == null) {
			recordFailedVerification(session, correlationId);
			throw new BusinessException("SELF_SERVICE_VERIFICATION_FAILED", "No fue posible verificar la sesion con los datos proporcionados.");
		}
		OffsetDateTime now = OffsetDateTime.now();
		repository.markVerified(
				sessionId, client.clientId(), session.startedAt().plus(BROWSER_MODE_MAX_DURATION),
				"CONTACT_BIRTH_DATE"
		);
		auditService.recordSystem(session.organizationId(), session.branchId(), null, "SELF_SERVICE_CLIENT_VERIFIED", "CLIENT", client.clientId(), "SELF_SERVICE_KIOSK", null, correlationId);
		return summary(sessionId, sessionToken);
	}

	@Transactional
	public SelfServiceSummaryResult register(
			UUID sessionId,
			String sessionToken,
			RegisterSelfServiceClientCommand command,
			String correlationId,
			String originIp
	) {
		rateLimiter.check("kiosk:register:ip:" + originIp, 20, Duration.ofMinutes(10));
		rateLimiter.check("kiosk:register:session:" + sessionId, 5, Duration.ofMinutes(10));
		SessionRecord session = activeSession(sessionId, sessionToken);
		capabilityPolicy.require(capabilities(session), SelfServiceCapability.CLIENT_REGISTRATION);
		if (session.clientId() != null) {
			throw new BusinessException("SELF_SERVICE_SESSION_ALREADY_VERIFIED", "La sesión ya pertenece a un socio.");
		}
		var client = clientLookupOperations.registerForSelfService(new RegisterSelfServiceClientCommand(
				session.organizationId(), session.branchId(), command.firstName(), command.lastName(),
				command.maternalLastName(), command.birthDate(), command.gender(), command.phone(), command.email()
		));
		repository.markVerified(
				sessionId, client.clientId(), session.startedAt().plus(BROWSER_MODE_MAX_DURATION),
				"SELF_REGISTRATION"
		);
		auditService.recordSystem(
				session.organizationId(), session.branchId(), null,
				"SELF_SERVICE_CLIENT_REGISTERED", "CLIENT", client.clientId(),
				"SELF_SERVICE_KIOSK", null, correlationId
		);
		return summary(sessionId, sessionToken);
	}

	@Transactional
	public SelfServiceSummaryResult summary(UUID sessionId, String sessionToken) {
		SessionRecord session = verifiedSession(sessionId, sessionToken);
		SelfServiceClientProfile client = clientLookupOperations.selfServiceProfile(session.organizationId(), session.clientId()).orElse(null);
		SelfServiceCapabilities capabilities = capabilities(session);
		List<MembershipRecord> memberships = repository.memberships(session.organizationId(), session.branchId(), session.clientId());
		List<PendingPaymentRecord> pendingPayments = repository.pendingPayments(session.organizationId(), session.branchId(), session.clientId());
		var paymentActivity = repository.paymentActivity(session.organizationId(), session.branchId(), session.clientId());
		return new SelfServiceSummaryResult(
				session.id(),
				session.expiresAt(),
				client == null ? "Socio" : client.displayName(),
				client,
				capabilities,
				memberships,
				pendingPayments,
				paymentActivity
		);
	}

	@Transactional
	public List<PlanOptionRecord> membershipOptions(UUID sessionId, String sessionToken) {
		SessionRecord session = verifiedSession(sessionId, sessionToken);
		capabilityPolicy.requireMembershipOptions(capabilities(session));
		return repository.planOptions(session.organizationId(), session.branchId());
	}

	@Transactional
	public MembershipQuoteView membershipQuote(
			UUID sessionId, String sessionToken, UUID planId, String operation, UUID membershipId
	) {
		SessionRecord session = verifiedSession(sessionId, sessionToken);
		SelfServiceCapabilities capabilities = capabilities(session);
		String normalizedOperation = operation == null ? "ENROLLMENT" : operation.trim().toUpperCase();
		switch (normalizedOperation) {
			case "ENROLLMENT" -> {
				capabilityPolicy.require(capabilities, SelfServiceCapability.MEMBERSHIP_ENROLLMENT);
				requirePlanAvailableForSelfService(session, planId);
			}
			case "PLAN_CHANGE" -> {
				capabilityPolicy.require(capabilities, SelfServiceCapability.MEMBERSHIP_PLAN_CHANGE);
				requireMembershipQuoteContext(session, membershipId, planId, false);
				requirePlanAvailableForSelfService(session, planId);
			}
			case "RENEWAL" -> {
				capabilityPolicy.require(capabilities, SelfServiceCapability.MEMBERSHIP_RENEWAL);
				requireMembershipQuoteContext(session, membershipId, planId, true);
			}
			default -> throw new BusinessException(
					"SELF_SERVICE_MEMBERSHIP_OPERATION_INVALID",
					"Selecciona una operación de membresía válida."
			);
		}
		return membershipPricingOperations.quoteForCheckout(
				kioskPrincipal(session), session.clientId(), planId
		);
	}

	@Transactional
	public MembershipQuoteView membershipQuote(UUID sessionId, String sessionToken, UUID planId) {
		return membershipQuote(sessionId, sessionToken, planId, "ENROLLMENT", null);
	}

	@Transactional
	public List<VisitPackagePlanSnapshot> visitPackageOptions(UUID sessionId, String sessionToken) {
		SessionRecord session = verifiedSession(sessionId, sessionToken);
		capabilityPolicy.require(capabilities(session), SelfServiceCapability.VISIT_PACKAGE_PURCHASE);
		return visitPackagePlans.selfServicePlans(session.organizationId(), session.branchId());
	}

	@Transactional
	public VisitPackageQuoteView visitPackageQuote(UUID sessionId, String sessionToken, UUID packagePlanId) {
		SessionRecord session = verifiedSession(sessionId, sessionToken);
		capabilityPolicy.require(capabilities(session), SelfServiceCapability.VISIT_PACKAGE_PURCHASE);
		requireVisitPackageAvailableForSelfService(session, packagePlanId);
		return visitPackages.quote(kioskPrincipal(session), session.clientId(), packagePlanId);
	}

	@Transactional
	public VisitPackageEntitlementView currentVisitPackage(UUID sessionId, String sessionToken) {
		SessionRecord session = verifiedSession(sessionId, sessionToken);
		return visitPackages.current(kioskPrincipal(session), session.clientId()).orElse(null);
	}

	public SelfServiceVisitPackageSaleResult purchaseVisitPackage(
			UUID sessionId, String sessionToken, UUID packagePlanId, String idempotencyKey,
			String correlationId, String originIp
	) {
		rateLimiter.check("kiosk:visit-package:ip:" + originIp, 30, Duration.ofMinutes(1));
		rateLimiter.check("kiosk:visit-package:session:" + sessionId, 5, Duration.ofMinutes(1));
		SessionRecord session = verifiedSession(sessionId, sessionToken);
		SelfServiceCapabilities capabilities = capabilities(session);
		capabilityPolicy.require(capabilities, SelfServiceCapability.VISIT_PACKAGE_PURCHASE);
		capabilityPolicy.requireOnlineCardPayments(capabilities);
		requireVisitPackageAvailableForSelfService(session, packagePlanId);
		VisitPackagePurchaseResult purchase = visitPackages.purchase(
				kioskPrincipal(session),
				new PurchaseVisitPackageCommand(
						session.clientId(), packagePlanId, "CARD_ONLINE", null, false,
						"Paquete de visitas con tarjeta desde kiosco", idempotencyKey),
				correlationId);
		StripeCheckoutOperations.CheckoutSessionResult checkout = purchase.balanceCents() > 0
				? stripeCheckout(session, purchase, "Paquete de visitas", idempotencyKey)
				: null;
		auditService.recordSystem(
				session.organizationId(), session.branchId(), null,
				"SELF_SERVICE_VISIT_PACKAGE_CHECKOUT_STARTED", "SALE", purchase.saleId(),
				"SELF_SERVICE_KIOSK", null, correlationId);
		return new SelfServiceVisitPackageSaleResult(
				purchase.entitlementId(), purchase.saleId(), purchase.paymentId(), purchase.saleNumber(),
				purchase.saleStatus(), purchase.entitlementStatus(), purchase.totalCents(), purchase.paidCents(),
				purchase.balanceCents(), purchase.currency(), checkout == null ? null : checkout.operationId(),
				checkout == null ? null : checkout.checkoutUrl(), checkout == null ? null : checkout.status());
	}

	@Transactional
	public List<BookingResource> bookingResources(UUID sessionId, String sessionToken, LocalDate date) {
		SessionRecord session = verifiedSession(sessionId, sessionToken);
		capabilityPolicy.require(capabilities(session), SelfServiceCapability.RESOURCE_BOOKING);
		return bookingOperations.eligibleResources(
				session.organizationId(), session.branchId(), session.clientId(), date
		);
	}

	@Transactional
	public List<BookingAvailability> bookingAvailability(UUID sessionId, String sessionToken, UUID resourceId, LocalDate date) {
		SessionRecord session = verifiedSession(sessionId, sessionToken);
		capabilityPolicy.require(capabilities(session), SelfServiceCapability.RESOURCE_BOOKING);
		return bookingOperations.eligibleAvailability(
				session.organizationId(), session.branchId(), session.clientId(), resourceId, date
		);
	}

	@Transactional
	public BookingResult createBooking(UUID sessionId, String sessionToken, UUID resourceId, OffsetDateTime startsAt, OffsetDateTime endsAt, String notes, String correlationId, String originIp) {
		rateLimiter.check("kiosk:booking:ip:" + originIp, 30, Duration.ofMinutes(1));
		rateLimiter.check("kiosk:booking:session:" + sessionId, 10, Duration.ofMinutes(1));
		SessionRecord session = verifiedSession(sessionId, sessionToken);
		capabilityPolicy.require(capabilities(session), SelfServiceCapability.RESOURCE_BOOKING);
		BookingResult booking = bookingOperations.createBooking(
				session.organizationId(),
				session.branchId(),
				session.clientId(),
				resourceId,
				startsAt,
				endsAt,
				notes,
				correlationId
		);
		auditService.recordSystem(session.organizationId(), session.branchId(), null, "SELF_SERVICE_BOOKING_CREATED", "BOOKING", booking.id(), "SELF_SERVICE_KIOSK", null, correlationId);
		return booking;
	}

	public SelfServiceMembershipSaleResult renew(UUID sessionId, String sessionToken, UUID membershipId, RenewMembershipOperationCommand request, String correlationId, String originIp) {
		rateLimiter.check("kiosk:renew:ip:" + originIp, 30, Duration.ofMinutes(1));
		rateLimiter.check("kiosk:renew:session:" + sessionId, 5, Duration.ofMinutes(1));
		SessionRecord session = verifiedSession(sessionId, sessionToken);
		SelfServiceCapabilities capabilities = capabilities(session);
		capabilityPolicy.require(capabilities, SelfServiceCapability.MEMBERSHIP_RENEWAL);
		capabilityPolicy.requireOnlineCardPayments(capabilities);
		requireMembershipManagedAtCurrentBranch(session, membershipId);
		MembershipSaleOperationResult response = membershipOperations.renew(kioskPrincipal(session), membershipId, request, correlationId);
		var checkout = stripeCheckout(session, response, "Renovacion de membresia", request.idempotencyKey());
		auditService.recordSystem(session.organizationId(), session.branchId(), null, "SELF_SERVICE_MEMBERSHIP_CHECKOUT_STARTED", "SALE", response.saleId(), "SELF_SERVICE_KIOSK", null, correlationId);
		return toResponse(response, checkout);
	}

	public SelfServiceMembershipSaleResult enroll(
			UUID sessionId,
			String sessionToken,
			UUID planId,
			String idempotencyKey,
			String correlationId,
			String originIp
	) {
		rateLimiter.check("kiosk:enroll:ip:" + originIp, 30, Duration.ofMinutes(1));
		rateLimiter.check("kiosk:enroll:session:" + sessionId, 5, Duration.ofMinutes(1));
		SessionRecord session = verifiedSession(sessionId, sessionToken);
		SelfServiceCapabilities capabilities = capabilities(session);
		capabilityPolicy.require(capabilities, SelfServiceCapability.MEMBERSHIP_ENROLLMENT);
		capabilityPolicy.requireOnlineCardPayments(capabilities);
		requirePlanAvailableForSelfService(session, planId);
		MembershipSaleOperationResult response = membershipEnrollmentOperations.enroll(
				kioskPrincipal(session),
				new EnrollMembershipOperationCommand(
						session.clientId(),
						planId,
						"CARD_ONLINE",
						null,
						false,
						"Contratacion de membresia con tarjeta desde kiosco",
						idempotencyKey
				),
				correlationId
		);
		var checkout = stripeCheckout(session, response, "Contratacion de membresia", idempotencyKey);
		auditService.recordSystem(session.organizationId(), session.branchId(), null,
				"SELF_SERVICE_MEMBERSHIP_CHECKOUT_STARTED", "SALE", response.saleId(),
				"SELF_SERVICE_KIOSK", "ENROLLMENT", correlationId);
		return toResponse(response, checkout);
	}

	public SelfServiceMembershipSaleResult changePlan(UUID sessionId, String sessionToken, UUID membershipId, ChangeMembershipPlanOperationCommand request, String correlationId, String originIp) {
		rateLimiter.check("kiosk:change-plan:ip:" + originIp, 30, Duration.ofMinutes(1));
		rateLimiter.check("kiosk:change-plan:session:" + sessionId, 5, Duration.ofMinutes(1));
		SessionRecord session = verifiedSession(sessionId, sessionToken);
		SelfServiceCapabilities capabilities = capabilities(session);
		capabilityPolicy.require(capabilities, SelfServiceCapability.MEMBERSHIP_PLAN_CHANGE);
		capabilityPolicy.requireOnlineCardPayments(capabilities);
		requireMembershipManagedAtCurrentBranch(session, membershipId);
		requirePlanAvailableForSelfService(session, request.targetPlanId());
		MembershipSaleOperationResult response = membershipOperations.changePlan(kioskPrincipal(session), membershipId, request, correlationId);
		var checkout = stripeCheckout(session, response, "Cambio de plan de membresia", request.idempotencyKey());
		auditService.recordSystem(session.organizationId(), session.branchId(), null, "SELF_SERVICE_MEMBERSHIP_CHECKOUT_STARTED", "SALE", response.saleId(), "SELF_SERVICE_KIOSK", null, correlationId);
		return toResponse(response, checkout);
	}

	@Transactional
	public RecurringTerms autopayTerms(UUID sessionId, String sessionToken, UUID membershipId) {
		SessionRecord session = verifiedSession(sessionId, sessionToken);
		capabilityPolicy.requireRecurringCardPayments(capabilities(session));
		return stripeRecurringOperations.terms(recurringContext(session, membershipId));
	}

	@Transactional
	public java.util.Optional<RecurringMandateView> autopayMandate(
			UUID sessionId,
			String sessionToken,
			UUID membershipId
	) {
		SessionRecord session = verifiedSession(sessionId, sessionToken);
		capabilityPolicy.require(capabilities(session), SelfServiceCapability.AUTOPAY);
		return stripeRecurringOperations.mandate(
				session.organizationId(), session.branchId(), session.clientId(), membershipId
		);
	}

	public MandateSetupResult setupAutopay(
			UUID sessionId,
			String sessionToken,
			UUID membershipId,
			String termsVersion,
			boolean accepted,
			boolean replaceExisting,
			String idempotencyKey,
			String correlationId,
			String originIp
	) {
		rateLimiter.check("kiosk:autopay:ip:" + originIp, 20, Duration.ofMinutes(1));
		rateLimiter.check("kiosk:autopay:session:" + sessionId, 5, Duration.ofMinutes(1));
		SessionRecord session = verifiedSession(sessionId, sessionToken);
		capabilityPolicy.requireRecurringCardPayments(capabilities(session));
		return stripeRecurringOperations.beginSetup(new BeginMandateSetupCommand(
				recurringContext(session, membershipId), termsVersion, accepted, idempotencyKey,
				"SELF_SERVICE_KIOSK", correlationId, replaceExisting
		));
	}

	MandateSetupResult setupAutopay(
			UUID sessionId, String sessionToken, UUID membershipId, String termsVersion,
			boolean accepted, String idempotencyKey, String correlationId, String originIp
	) {
		return setupAutopay(
				sessionId, sessionToken, membershipId, termsVersion, accepted, false,
				idempotencyKey, correlationId, originIp
		);
	}

	@Transactional
	public RecurringMandateView revokeAutopay(
			UUID sessionId,
			String sessionToken,
			UUID membershipId,
			String reason,
			String correlationId,
			String originIp
	) {
		rateLimiter.check("kiosk:autopay-revoke:ip:" + originIp, 20, Duration.ofMinutes(1));
		SessionRecord session = verifiedSession(sessionId, sessionToken);
		capabilityPolicy.require(capabilities(session), SelfServiceCapability.AUTOPAY);
		return stripeRecurringOperations.revoke(
				session.organizationId(), session.branchId(), session.clientId(), membershipId,
				reason, correlationId
		);
	}

	@Transactional
	public SelfServiceClientProfile updateContact(
			UUID sessionId,
			String sessionToken,
			String email,
			String phone,
			String correlationId,
			String originIp
	) {
		rateLimiter.check("kiosk:contact:ip:" + originIp, 30, Duration.ofMinutes(1));
		rateLimiter.check("kiosk:contact:session:" + sessionId, 10, Duration.ofMinutes(1));
		SessionRecord session = verifiedSession(sessionId, sessionToken);
		capabilityPolicy.require(capabilities(session), SelfServiceCapability.PROFILE_UPDATE);
		SelfServiceClientProfile profile = clientLookupOperations.updateSelfServiceContact(
				new UpdateSelfServiceContactCommand(
						session.organizationId(),
						session.clientId(),
						email,
						phone
				)
		);
		auditService.recordSystem(
				session.organizationId(),
				session.branchId(),
				null,
				"SELF_SERVICE_CONTACT_UPDATED",
				"CLIENT",
				session.clientId(),
				"SELF_SERVICE_KIOSK",
				null,
				correlationId
		);
		return profile;
	}

	private StripeCheckoutOperations.CheckoutSessionResult stripeCheckout(
			SessionRecord session,
			MembershipSaleOperationResult sale,
			String description,
			String idempotencyKey
	) {
		return stripeCheckoutOperations.createCheckout(new StripeCheckoutOperations.CreateCheckoutSessionCommand(
				session.organizationId(), session.branchId(), session.clientId(), sale.saleId(), sale.paymentId(),
				sale.balanceCents(), sale.currency(), false, description, "stripe:" + idempotencyKey
		));
	}

	private StripeCheckoutOperations.CheckoutSessionResult stripeCheckout(
			SessionRecord session,
			VisitPackagePurchaseResult sale,
			String description,
			String idempotencyKey
	) {
		return stripeCheckoutOperations.createCheckout(new StripeCheckoutOperations.CreateCheckoutSessionCommand(
				session.organizationId(), session.branchId(), session.clientId(), sale.saleId(), sale.paymentId(),
				sale.balanceCents(), sale.currency(), false, description, "stripe:" + idempotencyKey
		));
	}

	private RecurringMembershipContext recurringContext(SessionRecord session, UUID membershipId) {
		AutopayMembershipRecord membership = repository.autopayMembership(
				session.organizationId(), session.branchId(), session.clientId(), membershipId
		).orElseThrow(() -> new BusinessException(
				"RECURRING_MEMBERSHIP_NOT_ELIGIBLE",
				"La membresia no esta activa o no pertenece al socio verificado."
		));
		return new RecurringMembershipContext(
				session.organizationId(), session.branchId(), session.clientId(), membership.id(),
				membership.planId(), membership.planName(), membership.currentPriceCents(),
				membership.currency(), membership.endsOn()
		);
	}

	private SelfServiceMembershipSaleResult toResponse(
			MembershipSaleOperationResult result,
			StripeCheckoutOperations.CheckoutSessionResult checkout
	) {
		return new SelfServiceMembershipSaleResult(
				result.saleId(), result.saleNumber(), result.saleStatus(), result.totalCents(), result.paidCents(),
				result.balanceCents(), result.currency(),
				result.membership() == null ? null : new SelfServiceMembershipSummaryResult(
						result.membership().id(), result.membership().clientId(), result.membership().planId(),
						result.membership().planName(), result.membership().saleId(), result.membership().saleNumber(),
						result.membership().status(), result.membership().activatedAt(), result.membership().startsOn(),
						result.membership().endsOn(), result.membership().graceEndsOn()
				),
				result.paymentId(), checkout.operationId(), checkout.checkoutUrl(), checkout.status()
		);
	}

	@Transactional
	public void end(UUID sessionId, String sessionToken, String correlationId) {
		SessionRecord session = activeSession(sessionId, sessionToken);
		repository.endSession(sessionId);
		auditService.recordSystem(session.organizationId(), session.branchId(), null, "SELF_SERVICE_SESSION_ENDED", "SELF_SERVICE_SESSION", sessionId, "SELF_SERVICE_KIOSK", null, correlationId);
	}

	@Transactional
	public CreateSessionResult reset(UUID sessionId, String sessionToken, String correlationId) {
		SessionRecord session = kioskBrowserSession(sessionId, sessionToken);
		OffsetDateTime now = OffsetDateTime.now();
		OffsetDateTime browserExpiresAt = session.startedAt().plus(BROWSER_MODE_MAX_DURATION);
		repository.resetSession(session.id(), now, browserExpiresAt);
		auditService.recordSystem(
				session.organizationId(),
				session.branchId(),
				session.launchedByUserId(),
				"SELF_SERVICE_SESSION_RESET",
				"SELF_SERVICE_SESSION",
				session.id(),
				"SELF_SERVICE_KIOSK",
				null,
				correlationId
		);
		TerminalRecord terminal = repository.defaultTerminal(session.organizationId(), session.branchId())
				.orElseThrow(() -> new BusinessException("SELF_SERVICE_TERMINAL_NOT_FOUND", "El kiosco ya no esta disponible."));
		return new CreateSessionResult(
				session.id(),
				sessionToken,
				terminal.name(),
				terminal.tradeName(),
				terminal.brandName(),
				terminal.logoUrl(),
				terminal.primaryColor(),
				terminal.accentColor(),
				terminal.surfaceColor(),
				browserExpiresAt,
				false,
				capabilities(terminal.organizationId(), terminal.branchId(), terminal.allowedActionsJson())
		);
	}

	private SessionRecord verifiedSession(UUID sessionId, String sessionToken) {
		SessionRecord session = activeSession(sessionId, sessionToken);
		if (session.clientId() == null) throw new BusinessException("SELF_SERVICE_SESSION_NOT_VERIFIED", "Primero verifica tu identidad.");
		return session;
	}

	@Transactional
	public SessionRecord verifiedSession(
			UUID sessionId, String sessionToken, SelfServiceCapability capability
	) {
		SessionRecord session = verifiedSession(sessionId, sessionToken);
		capabilityPolicy.require(capabilities(session), capability);
		return session;
	}

	private SelfServiceCapabilities capabilities(SessionRecord session) {
		return capabilities(session.organizationId(), session.branchId(), session.allowedActionsJson());
	}

	private SelfServiceCapabilities capabilities(UUID organizationId, UUID branchId, String allowedActionsJson) {
		var paymentConfiguration = paymentConfigurationService.configuration(organizationId, branchId);
		return capabilityPolicy.resolve(allowedActionsJson).withPaymentReadiness(
				paymentConfiguration.stripeCheckoutReady(),
				paymentConfiguration.stripeRecurringReady()
		);
	}

	private void requireMembershipManagedAtCurrentBranch(SessionRecord session, UUID membershipId) {
		if (!repository.membershipBelongsToBranch(
				session.organizationId(), session.branchId(), session.clientId(), membershipId
		)) {
			throw new BusinessException(
					"MEMBERSHIP_HOME_BRANCH_REQUIRED",
					"Esta membresía no puede administrarse desde la sucursal actual."
			);
		}
	}

	private void requirePlanAvailableForSelfService(SessionRecord session, UUID planId) {
		if (!repository.planAvailableForSelfService(session.organizationId(), session.branchId(), planId)) {
			throw new BusinessException(
					"SELF_SERVICE_MEMBERSHIP_PLAN_NOT_AVAILABLE",
					"Este plan no está disponible para nuevas operaciones en el kiosco."
			);
		}
	}

	private void requireVisitPackageAvailableForSelfService(SessionRecord session, UUID packagePlanId) {
		boolean available = visitPackagePlans.selfServicePlans(session.organizationId(), session.branchId()).stream()
				.anyMatch(plan -> plan.id().equals(packagePlanId));
		if (!available) {
			throw new BusinessException(
					"SELF_SERVICE_VISIT_PACKAGE_NOT_AVAILABLE",
					"Este paquete de visitas no está disponible en el kiosco.");
		}
	}

	private void requireMembershipQuoteContext(
			SessionRecord session, UUID membershipId, UUID planId, boolean samePlanRequired
	) {
		if (membershipId == null) {
			throw new BusinessException(
					"SELF_SERVICE_MEMBERSHIP_REQUIRED",
					"Selecciona la membresía que deseas administrar."
			);
		}
		requireMembershipManagedAtCurrentBranch(session, membershipId);
		if (samePlanRequired && !repository.membershipUsesPlan(
				session.organizationId(), session.clientId(), membershipId, planId
		)) {
			throw new BusinessException(
					"SELF_SERVICE_MEMBERSHIP_PLAN_MISMATCH",
					"La renovación debe utilizar el plan actual de la membresía."
			);
		}
	}

	private SessionRecord activeSession(UUID sessionId, String sessionToken) {
		SessionRecord session = kioskBrowserSession(sessionId, sessionToken);
		OffsetDateTime now = OffsetDateTime.now();
		boolean inactive = session.clientId() != null
				&& session.lastRequestAt() != null
				&& session.lastRequestAt().plus(VERIFIED_INACTIVITY_TIMEOUT).isBefore(now);
		if (!"ACTIVE".equals(session.status()) || session.expiresAt().isBefore(now) || inactive) {
			throw new BusinessException("SELF_SERVICE_SESSION_EXPIRED", "La sesion del kiosco expiro.");
		}
		repository.touchSession(session.id(), now);
		return session;
	}

	private SessionRecord kioskBrowserSession(UUID sessionId, String sessionToken) {
		SessionRecord session = repository.session(sessionId).orElseThrow(this::invalidKioskSession);
		if (session.sessionTokenHash() == null || sessionToken == null || !MessageDigest.isEqual(session.sessionTokenHash().getBytes(StandardCharsets.UTF_8), tokenHash(sessionToken).getBytes(StandardCharsets.UTF_8))) {
			throw invalidKioskSession();
		}
		if (!"ACTIVE".equals(session.status())) {
			throw new BusinessException("SELF_SERVICE_SESSION_ENDED", "El modo kiosco ya no esta activo.");
		}
		if (session.startedAt().plus(BROWSER_MODE_MAX_DURATION).isBefore(OffsetDateTime.now())) {
			throw new BusinessException("SELF_SERVICE_BROWSER_MODE_EXPIRED", "El modo kiosco debe ser abierto nuevamente por recepcion.");
		}
		return session;
	}

	private BusinessException invalidKioskSession() {
		return new BusinessException("SELF_SERVICE_SESSION_INVALID", "La sesion del kiosco no es valida.");
	}

	private void ensureNotBlocked(SessionRecord session) {
		if (session.blockedUntil() != null && session.blockedUntil().isAfter(OffsetDateTime.now())) throw new BusinessException("SELF_SERVICE_SESSION_BLOCKED", "Demasiados intentos. Espera un momento y vuelve a intentar.");
	}

	private void recordFailedVerification(SessionRecord session, String correlationId) {
		OffsetDateTime now = OffsetDateTime.now();
		repository.recordFailedVerification(session.id(), MAX_VERIFICATION_ATTEMPTS, now.plus(BLOCK_DURATION), now);
		auditService.recordSystem(session.organizationId(), session.branchId(), null, "SELF_SERVICE_VERIFICATION_FAILED", "SELF_SERVICE_SESSION", session.id(), "SELF_SERVICE_KIOSK", null, correlationId);
	}

	private GymOsPrincipal kioskPrincipal(SessionRecord session) {
		return new GymOsPrincipal(null, session.organizationId(), session.branchId(), "self-service-kiosk", "Kiosco", Set.of());
	}

	private void requireLaunchContext(GymOsPrincipal principal) {
		if (principal == null || principal.userId() == null || principal.organizationId() == null) {
			throw new BusinessException("AUTHENTICATION_REQUIRED", "Inicia sesion para abrir el kiosco.");
		}
		boolean receptionRole = principal.roles().stream()
				.map(role -> role.toLowerCase(Locale.ROOT))
				.anyMatch(role -> role.contains("recepcion") || role.contains("recepción") || role.contains("reception"));
		boolean elevatedRole = principal.roles().stream()
				.map(role -> role.toLowerCase(Locale.ROOT))
				.anyMatch(role -> role.contains("dueno")
						|| role.contains("dueño")
						|| role.contains("owner")
						|| role.contains("administrador")
						|| role.contains("admin")
						|| role.contains("gerente")
						|| role.contains("manager"));
		if (!receptionRole || elevatedRole) {
			throw new AccessDeniedException("Solo el personal de recepcion puede abrir el kiosco.");
		}
		if (principal.branchId() == null) {
			throw new BusinessException("BRANCH_REQUIRED", "Selecciona una sucursal antes de abrir el kiosco.");
		}
	}

	private String randomToken() {
		byte[] bytes = new byte[32]; SECURE_RANDOM.nextBytes(bytes); return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	private String tokenHash(String token) {
		try { return Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))); }
		catch (NoSuchAlgorithmException exception) { throw new IllegalStateException("SHA-256 is required", exception); }
	}
}
