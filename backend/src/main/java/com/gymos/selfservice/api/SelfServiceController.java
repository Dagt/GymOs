package com.gymos.selfservice.api;

import com.gymos.bookings.api.operations.BookingOperations.BookingAvailability;
import com.gymos.bookings.api.operations.BookingOperations.BookingResource;
import com.gymos.bookings.api.operations.BookingOperations.BookingResult;
import com.gymos.common.api.Correlation;
import com.gymos.memberships.api.operations.ChangeMembershipPlanOperationCommand;
import com.gymos.memberships.api.operations.RenewMembershipOperationCommand;
import com.gymos.selfservice.application.SelfServiceService;
import com.gymos.selfservice.application.command.VerifySessionCommand;
import com.gymos.selfservice.application.result.CreateSessionResult;
import com.gymos.selfservice.application.result.SelfServiceMembershipSaleResult;
import com.gymos.selfservice.application.result.SelfServiceSummaryResult;
import com.gymos.selfservice.infrastructure.SelfServiceRepository.MembershipRecord;
import com.gymos.selfservice.infrastructure.SelfServiceRepository.PendingPaymentRecord;
import com.gymos.selfservice.infrastructure.SelfServiceRepository.PlanOptionRecord;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
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

	public SelfServiceController(SelfServiceService selfServiceService) { this.selfServiceService = selfServiceService; }

	@PostMapping("/terminals/{terminalCode}/sessions")
	CreateSessionResponse createSession(@PathVariable String terminalCode, @RequestHeader(name = "X-GymOS-Organization-Id", required = false) UUID organizationId, @RequestHeader(name = "X-GymOS-Branch-Id", required = false) UUID branchId, HttpServletRequest servletRequest) {
		return toResponse(selfServiceService.createSession(terminalCode, organizationId, branchId, servletRequest.getRemoteAddr(), correlationId(servletRequest)));
	}

	@PostMapping("/sessions/{sessionId}/verify")
	SelfServiceSummaryResponse verify(@PathVariable UUID sessionId, @Valid @RequestBody VerifySessionRequest request, @RequestHeader(name = "X-Self-Service-Session-Token", required = false) String sessionToken, HttpServletRequest servletRequest) {
		return toResponse(selfServiceService.verify(sessionId, sessionToken, request.toCommand(), correlationId(servletRequest), servletRequest.getRemoteAddr()));
	}

	@GetMapping("/sessions/{sessionId}/summary")
	SelfServiceSummaryResponse summary(@PathVariable UUID sessionId, @RequestHeader(name = "X-Self-Service-Session-Token", required = false) String sessionToken) {
		return toResponse(selfServiceService.summary(sessionId, sessionToken));
	}

	@GetMapping("/sessions/{sessionId}/membership-options")
	List<PlanOptionRecord> membershipOptions(@PathVariable UUID sessionId, @RequestHeader(name = "X-Self-Service-Session-Token", required = false) String sessionToken) {
		return selfServiceService.membershipOptions(sessionId, sessionToken);
	}

	@GetMapping("/sessions/{sessionId}/booking-resources")
	List<BookingResource> bookingResources(@PathVariable UUID sessionId, @RequestHeader(name = "X-Self-Service-Session-Token", required = false) String sessionToken) {
		return selfServiceService.bookingResources(sessionId, sessionToken);
	}

	@GetMapping("/sessions/{sessionId}/booking-availability")
	List<BookingAvailability> bookingAvailability(@PathVariable UUID sessionId, @RequestParam UUID resourceId, @RequestParam LocalDate date, @RequestHeader(name = "X-Self-Service-Session-Token", required = false) String sessionToken) {
		return selfServiceService.bookingAvailability(sessionId, sessionToken, resourceId, date);
	}

	@PostMapping("/sessions/{sessionId}/bookings")
	BookingResult createBooking(@PathVariable UUID sessionId, @Valid @RequestBody CreateBookingRequest request, @RequestHeader(name = "X-Self-Service-Session-Token", required = false) String sessionToken, HttpServletRequest servletRequest) {
		return selfServiceService.createBooking(sessionId, sessionToken, request.resourceId(), request.startsAt(), request.endsAt(), request.notes(), correlationId(servletRequest), servletRequest.getRemoteAddr());
	}

	@PostMapping("/sessions/{sessionId}/membership-renewals/{membershipId}")
	SelfServiceMembershipSaleResponse renew(@PathVariable UUID sessionId, @PathVariable UUID membershipId, @Valid @RequestBody RenewMembershipRequest request, @RequestHeader(name = "X-Self-Service-Session-Token", required = false) String sessionToken, HttpServletRequest servletRequest) {
		return toResponse(selfServiceService.renew(sessionId, sessionToken, membershipId, request.toCommand(), correlationId(servletRequest), servletRequest.getRemoteAddr()));
	}

	@PostMapping("/sessions/{sessionId}/membership-change-requests/{membershipId}")
	SelfServiceMembershipSaleResponse changePlan(@PathVariable UUID sessionId, @PathVariable UUID membershipId, @Valid @RequestBody ChangePlanRequest request, @RequestHeader(name = "X-Self-Service-Session-Token", required = false) String sessionToken, HttpServletRequest servletRequest) {
		return toResponse(selfServiceService.changePlan(sessionId, sessionToken, membershipId, request.toCommand(), correlationId(servletRequest), servletRequest.getRemoteAddr()));
	}

	@PostMapping("/sessions/{sessionId}/end") @ResponseStatus(HttpStatus.NO_CONTENT)
	void end(@PathVariable UUID sessionId, @RequestHeader(name = "X-Self-Service-Session-Token", required = false) String sessionToken, HttpServletRequest servletRequest) { selfServiceService.end(sessionId, sessionToken, correlationId(servletRequest)); }

	@PatchMapping("/sessions/{sessionId}/contact-data") @ResponseStatus(HttpStatus.NO_CONTENT)
	void contactDataPlaceholder(@PathVariable UUID sessionId, @RequestHeader(name = "X-Self-Service-Session-Token", required = false) String sessionToken) { selfServiceService.summary(sessionId, sessionToken); }

	private String correlationId(HttpServletRequest request) { Object value = request.getAttribute(Correlation.ATTRIBUTE); return value == null ? "unknown" : value.toString(); }
	private CreateSessionResponse toResponse(CreateSessionResult result) { return new CreateSessionResponse(result.sessionId(), result.sessionToken(), result.terminalName(), result.organizationName(), result.brandName(), result.logoUrl(), result.primaryColor(), result.accentColor(), result.surfaceColor(), result.expiresAt(), result.verified()); }
	private SelfServiceSummaryResponse toResponse(SelfServiceSummaryResult result) { return new SelfServiceSummaryResponse(result.sessionId(), result.expiresAt(), result.clientName(), result.memberships(), result.pendingPayments()); }
	private SelfServiceMembershipSaleResponse toResponse(SelfServiceMembershipSaleResult result) { return new SelfServiceMembershipSaleResponse(result.saleId(), result.saleNumber(), result.saleStatus(), result.totalCents(), result.paidCents(), result.balanceCents(), result.currency(), result.membership()); }

	public record VerifySessionRequest(@NotBlank String identifier, @NotNull LocalDate birthDate) { private VerifySessionCommand toCommand() { return new VerifySessionCommand(identifier, birthDate); } }
	public record CreateBookingRequest(@NotNull UUID resourceId, @NotNull OffsetDateTime startsAt, @NotNull OffsetDateTime endsAt, @Size(max = 2000) String notes) {}
	public record RenewMembershipRequest(@NotBlank @Pattern(regexp = "CASH|TRANSFER|CARD_MANUAL") String method, @Size(max = 160) String reference, boolean validateNow, @Size(max = 2000) String notes, @Size(max = 160) String idempotencyKey) { private RenewMembershipOperationCommand toCommand() { return new RenewMembershipOperationCommand(method, reference, validateNow, notes, idempotencyKey); } }
	public record ChangePlanRequest(@NotNull UUID targetPlanId, @NotBlank @Pattern(regexp = "CASH|TRANSFER|CARD_MANUAL") String method, @Size(max = 160) String reference, boolean validateNow, @Size(max = 2000) String notes, @Size(max = 160) String idempotencyKey) { private ChangeMembershipPlanOperationCommand toCommand() { return new ChangeMembershipPlanOperationCommand(targetPlanId, method, reference, validateNow, notes, idempotencyKey); } }
	public record CreateSessionResponse(UUID sessionId, String sessionToken, String terminalName, String organizationName, String brandName, String logoUrl, String primaryColor, String accentColor, String surfaceColor, OffsetDateTime expiresAt, boolean verified) {}
	public record SelfServiceSummaryResponse(UUID sessionId, OffsetDateTime expiresAt, String clientName, List<MembershipRecord> memberships, List<PendingPaymentRecord> pendingPayments) {}
	public record SelfServiceMembershipSaleResponse(UUID saleId, String saleNumber, String saleStatus, long totalCents, long paidCents, long balanceCents, String currency, com.gymos.selfservice.application.result.SelfServiceMembershipSummaryResult membership) {}
}
