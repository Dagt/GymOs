package com.gymos.selfservice.api;

import com.gymos.bookings.api.operations.BookingOperations.BookingAvailability;
import com.gymos.bookings.api.operations.BookingOperations.BookingResource;
import com.gymos.bookings.api.operations.BookingOperations.BookingResult;
import com.gymos.clients.api.operations.SelfServiceClientProfile;
import com.gymos.clients.api.operations.RegisterSelfServiceClientCommand;
import com.gymos.common.api.Correlation;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.common.security.ClientIpResolver;
import com.gymos.memberships.api.operations.ChangeMembershipPlanOperationCommand;
import com.gymos.memberships.api.operations.RenewMembershipOperationCommand;
import com.gymos.memberships.api.operations.MembershipPricingOperations.MembershipQuoteView;
import com.gymos.payments.api.StripeRecurringOperations.MandateSetupResult;
import com.gymos.payments.api.StripeRecurringOperations.RecurringMandateView;
import com.gymos.payments.api.StripeRecurringOperations.RecurringTerms;
import com.gymos.selfservice.application.SelfServiceService;
import com.gymos.selfservice.application.command.VerifySessionCommand;
import com.gymos.selfservice.application.result.CreateSessionResult;
import com.gymos.selfservice.application.result.SelfServiceMembershipSaleResult;
import com.gymos.selfservice.application.result.SelfServiceCapabilities;
import com.gymos.selfservice.application.result.SelfServiceSummaryResult;
import com.gymos.selfservice.infrastructure.SelfServiceRepository.MembershipRecord;
import com.gymos.selfservice.infrastructure.SelfServiceRepository.PendingPaymentRecord;
import com.gymos.selfservice.infrastructure.SelfServiceRepository.SelfServicePaymentRecord;
import com.gymos.selfservice.infrastructure.SelfServiceRepository.PlanOptionRecord;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/self-service")
public class SelfServiceController {
	private final SelfServiceService selfServiceService;
	private final ClientIpResolver clientIpResolver;

	public SelfServiceController(SelfServiceService selfServiceService, ClientIpResolver clientIpResolver) {
		this.selfServiceService = selfServiceService;
		this.clientIpResolver = clientIpResolver;
	}

	@PostMapping("/kiosk-sessions")
	CreateSessionResponse launchKiosk(
			@AuthenticationPrincipal GymOsPrincipal principal,
			HttpServletRequest servletRequest
	) {
		return toResponse(selfServiceService.launchSession(
				principal,
				clientIpResolver.resolve(servletRequest),
				correlationId(servletRequest)
		));
	}

	@PostMapping("/sessions/{sessionId}/verify")
	SelfServiceSummaryResponse verify(@PathVariable UUID sessionId, @Valid @RequestBody VerifySessionRequest request, @RequestHeader(name = "X-Self-Service-Session-Token", required = false) String sessionToken, HttpServletRequest servletRequest) {
		return toResponse(selfServiceService.verify(sessionId, sessionToken, request.toCommand(), correlationId(servletRequest), clientIpResolver.resolve(servletRequest)));
	}

	@PostMapping("/sessions/{sessionId}/registrations")
	SelfServiceSummaryResponse register(
			@PathVariable UUID sessionId,
			@Valid @RequestBody RegisterClientRequest request,
			@RequestHeader(name = "X-Self-Service-Session-Token", required = false) String sessionToken,
			HttpServletRequest servletRequest
	) {
		return toResponse(selfServiceService.register(
				sessionId, sessionToken, request.toCommand(), correlationId(servletRequest),
				clientIpResolver.resolve(servletRequest)
		));
	}

	@GetMapping("/sessions/{sessionId}/summary")
	SelfServiceSummaryResponse summary(@PathVariable UUID sessionId, @RequestHeader(name = "X-Self-Service-Session-Token", required = false) String sessionToken) {
		return toResponse(selfServiceService.summary(sessionId, sessionToken));
	}

	@GetMapping("/sessions/{sessionId}/membership-options")
	List<PlanOptionRecord> membershipOptions(@PathVariable UUID sessionId, @RequestHeader(name = "X-Self-Service-Session-Token", required = false) String sessionToken) {
		return selfServiceService.membershipOptions(sessionId, sessionToken);
	}

	@GetMapping("/sessions/{sessionId}/membership-quotes/{planId}")
	MembershipQuoteView membershipQuote(
			@PathVariable UUID sessionId,
			@PathVariable UUID planId,
			@RequestHeader(name = "X-Self-Service-Session-Token", required = false) String sessionToken
	) {
		return selfServiceService.membershipQuote(sessionId, sessionToken, planId);
	}

	@GetMapping("/sessions/{sessionId}/booking-resources")
	List<BookingResource> bookingResources(@PathVariable UUID sessionId, @RequestParam LocalDate date, @RequestHeader(name = "X-Self-Service-Session-Token", required = false) String sessionToken) {
		return selfServiceService.bookingResources(sessionId, sessionToken, date);
	}

	@GetMapping("/sessions/{sessionId}/booking-availability")
	List<BookingAvailability> bookingAvailability(@PathVariable UUID sessionId, @RequestParam UUID resourceId, @RequestParam LocalDate date, @RequestHeader(name = "X-Self-Service-Session-Token", required = false) String sessionToken) {
		return selfServiceService.bookingAvailability(sessionId, sessionToken, resourceId, date);
	}

	@PostMapping("/sessions/{sessionId}/bookings")
	BookingResult createBooking(@PathVariable UUID sessionId, @Valid @RequestBody CreateBookingRequest request, @RequestHeader(name = "X-Self-Service-Session-Token", required = false) String sessionToken, HttpServletRequest servletRequest) {
		return selfServiceService.createBooking(sessionId, sessionToken, request.resourceId(), request.startsAt(), request.endsAt(), request.notes(), correlationId(servletRequest), clientIpResolver.resolve(servletRequest));
	}

	@PostMapping("/sessions/{sessionId}/membership-renewals/{membershipId}")
	SelfServiceMembershipSaleResponse renew(@PathVariable UUID sessionId, @PathVariable UUID membershipId, @Valid @RequestBody RenewMembershipRequest request, @RequestHeader(name = "X-Self-Service-Session-Token", required = false) String sessionToken, HttpServletRequest servletRequest) {
		return toResponse(selfServiceService.renew(sessionId, sessionToken, membershipId, request.toCommand(), correlationId(servletRequest), clientIpResolver.resolve(servletRequest)));
	}

	@PostMapping("/sessions/{sessionId}/membership-enrollments")
	SelfServiceMembershipSaleResponse enroll(
			@PathVariable UUID sessionId,
			@Valid @RequestBody EnrollMembershipRequest request,
			@RequestHeader(name = "X-Self-Service-Session-Token", required = false) String sessionToken,
			HttpServletRequest servletRequest
	) {
		return toResponse(selfServiceService.enroll(
				sessionId,
				sessionToken,
				request.planId(),
				request.idempotencyKey(),
				correlationId(servletRequest),
				clientIpResolver.resolve(servletRequest)
		));
	}

	@PostMapping("/sessions/{sessionId}/membership-change-requests/{membershipId}")
	SelfServiceMembershipSaleResponse changePlan(@PathVariable UUID sessionId, @PathVariable UUID membershipId, @Valid @RequestBody ChangePlanRequest request, @RequestHeader(name = "X-Self-Service-Session-Token", required = false) String sessionToken, HttpServletRequest servletRequest) {
		return toResponse(selfServiceService.changePlan(sessionId, sessionToken, membershipId, request.toCommand(), correlationId(servletRequest), clientIpResolver.resolve(servletRequest)));
	}

	@GetMapping("/sessions/{sessionId}/autopay/{membershipId}/terms")
	RecurringTerms autopayTerms(
			@PathVariable UUID sessionId,
			@PathVariable UUID membershipId,
			@RequestHeader(name = "X-Self-Service-Session-Token", required = false) String sessionToken
	) {
		return selfServiceService.autopayTerms(sessionId, sessionToken, membershipId);
	}

	@GetMapping("/sessions/{sessionId}/autopay/{membershipId}")
	ResponseEntity<RecurringMandateView> autopayMandate(
			@PathVariable UUID sessionId,
			@PathVariable UUID membershipId,
			@RequestHeader(name = "X-Self-Service-Session-Token", required = false) String sessionToken
	) {
		return ResponseEntity.ok(selfServiceService.autopayMandate(
				sessionId, sessionToken, membershipId
		).orElse(null));
	}

	@PostMapping("/sessions/{sessionId}/autopay/{membershipId}/setup")
	MandateSetupResult setupAutopay(
			@PathVariable UUID sessionId,
			@PathVariable UUID membershipId,
			@Valid @RequestBody SetupAutopayRequest request,
			@RequestHeader(name = "X-Self-Service-Session-Token", required = false) String sessionToken,
			HttpServletRequest servletRequest
	) {
		return selfServiceService.setupAutopay(
				sessionId, sessionToken, membershipId, request.termsVersion(), request.accepted(), request.replaceExisting(),
				request.idempotencyKey(), correlationId(servletRequest), clientIpResolver.resolve(servletRequest)
		);
	}

	@PostMapping("/sessions/{sessionId}/autopay/{membershipId}/revoke")
	RecurringMandateView revokeAutopay(
			@PathVariable UUID sessionId,
			@PathVariable UUID membershipId,
			@Valid @RequestBody RevokeAutopayRequest request,
			@RequestHeader(name = "X-Self-Service-Session-Token", required = false) String sessionToken,
			HttpServletRequest servletRequest
	) {
		return selfServiceService.revokeAutopay(
				sessionId, sessionToken, membershipId, request.reason(),
				correlationId(servletRequest), clientIpResolver.resolve(servletRequest)
		);
	}

	@PostMapping("/sessions/{sessionId}/end") @ResponseStatus(HttpStatus.NO_CONTENT)
	void end(@PathVariable UUID sessionId, @RequestHeader(name = "X-Self-Service-Session-Token", required = false) String sessionToken, HttpServletRequest servletRequest) { selfServiceService.end(sessionId, sessionToken, correlationId(servletRequest)); }

	@PostMapping("/sessions/{sessionId}/reset")
	CreateSessionResponse reset(
			@PathVariable UUID sessionId,
			@RequestHeader(name = "X-Self-Service-Session-Token", required = false) String sessionToken,
			HttpServletRequest servletRequest
	) {
		return toResponse(selfServiceService.reset(sessionId, sessionToken, correlationId(servletRequest)));
	}

	@PatchMapping("/sessions/{sessionId}/contact-data")
	SelfServiceClientProfile updateContact(
			@PathVariable UUID sessionId,
			@Valid @RequestBody UpdateContactRequest request,
			@RequestHeader(name = "X-Self-Service-Session-Token", required = false) String sessionToken,
			HttpServletRequest servletRequest
	) {
		return selfServiceService.updateContact(
				sessionId,
				sessionToken,
				request.email(),
				request.phone(),
				correlationId(servletRequest),
				clientIpResolver.resolve(servletRequest)
		);
	}

	private String correlationId(HttpServletRequest request) { Object value = request.getAttribute(Correlation.ATTRIBUTE); return value == null ? "unknown" : value.toString(); }
	private CreateSessionResponse toResponse(CreateSessionResult result) { return new CreateSessionResponse(result.sessionId(), result.sessionToken(), result.terminalName(), result.organizationName(), result.brandName(), result.logoUrl(), result.primaryColor(), result.accentColor(), result.surfaceColor(), result.expiresAt(), result.verified(), result.capabilities()); }
	private SelfServiceSummaryResponse toResponse(SelfServiceSummaryResult result) { return new SelfServiceSummaryResponse(result.sessionId(), result.expiresAt(), result.clientName(), result.profile(), result.capabilities(), result.memberships(), result.pendingPayments(), result.paymentActivity()); }
	private SelfServiceMembershipSaleResponse toResponse(SelfServiceMembershipSaleResult result) { return new SelfServiceMembershipSaleResponse(result.saleId(), result.saleNumber(), result.saleStatus(), result.totalCents(), result.paidCents(), result.balanceCents(), result.currency(), result.membership(), result.paymentId(), result.providerOperationId(), result.checkoutUrl(), result.checkoutStatus()); }

	public record VerifySessionRequest(@NotBlank String identifier, @NotNull LocalDate birthDate) { private VerifySessionCommand toCommand() { return new VerifySessionCommand(identifier, birthDate); } }
	public record RegisterClientRequest(
			@NotBlank @Size(max = 120) String firstName,
			@NotBlank @Size(max = 120) String lastName,
			@Size(max = 120) String maternalLastName,
			@NotNull LocalDate birthDate,
			@Pattern(regexp = "^$|MALE|FEMALE$") String gender,
			@Pattern(regexp = "^$|^\\+52\\d{10}$") String phone,
			@Email @Size(max = 180) String email
	) {
		private RegisterSelfServiceClientCommand toCommand() {
			return new RegisterSelfServiceClientCommand(
					null, null, firstName, lastName, maternalLastName, birthDate,
					gender, phone, email
			);
		}
	}
	public record CreateBookingRequest(@NotNull UUID resourceId, @NotNull OffsetDateTime startsAt, @NotNull OffsetDateTime endsAt, @Size(max = 2000) String notes) {}
	public record RenewMembershipRequest(@NotBlank @Size(max = 160) String idempotencyKey) { private RenewMembershipOperationCommand toCommand() { return new RenewMembershipOperationCommand("CARD_ONLINE", null, false, "Renovacion con tarjeta desde kiosco", idempotencyKey); } }
	public record EnrollMembershipRequest(@NotNull UUID planId, @NotBlank @Size(max = 160) String idempotencyKey) {}
	public record ChangePlanRequest(@NotNull UUID targetPlanId, @NotBlank @Size(max = 160) String idempotencyKey) { private ChangeMembershipPlanOperationCommand toCommand() { return new ChangeMembershipPlanOperationCommand(targetPlanId, "CARD_ONLINE", null, false, "Cambio de plan con tarjeta desde kiosco", idempotencyKey); } }
	public record SetupAutopayRequest(
			@NotBlank @Size(max = 40) String termsVersion,
			boolean accepted,
			@NotBlank @Size(max = 150) String idempotencyKey,
			boolean replaceExisting
	) {}
	public record RevokeAutopayRequest(@NotBlank @Size(max = 500) String reason) {}
	public record UpdateContactRequest(
			@Email @Size(max = 180) String email,
			@Pattern(regexp = "^$|^\\+52\\d{10}$") String phone
	) {}
	public record CreateSessionResponse(UUID sessionId, String sessionToken, String terminalName, String organizationName, String brandName, String logoUrl, String primaryColor, String accentColor, String surfaceColor, OffsetDateTime expiresAt, boolean verified, SelfServiceCapabilities capabilities) {}
	public record SelfServiceSummaryResponse(UUID sessionId, OffsetDateTime expiresAt, String clientName, SelfServiceClientProfile profile, SelfServiceCapabilities capabilities, List<MembershipRecord> memberships, List<PendingPaymentRecord> pendingPayments, List<SelfServicePaymentRecord> paymentActivity) {}
	public record SelfServiceMembershipSaleResponse(UUID saleId, String saleNumber, String saleStatus, long totalCents, long paidCents, long balanceCents, String currency, com.gymos.selfservice.application.result.SelfServiceMembershipSummaryResult membership, UUID paymentId, UUID providerOperationId, String checkoutUrl, String checkoutStatus) {}
}
