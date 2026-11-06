package com.gymos.memberships.api;

import com.gymos.clients.api.operations.ClientBranchAccess;
import com.gymos.common.api.Correlation;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.memberships.application.command.ChangeMembershipPlanCommand;
import com.gymos.memberships.application.command.EnrollMembershipCommand;
import com.gymos.memberships.application.command.RenewMembershipCommand;
import com.gymos.memberships.application.result.MembershipSaleResult;
import com.gymos.memberships.application.result.MembershipSummaryResult;
import com.gymos.memberships.application.MembershipService;
import com.gymos.memberships.application.MembershipPricingService;
import com.gymos.memberships.application.result.MembershipQuoteResult;
import com.gymos.memberships.infrastructure.MembershipRepository.MembershipSummary;
import com.gymos.memberships.infrastructure.MembershipRepository.MembershipPeriodSummary;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class MembershipController {
	private final MembershipService membershipService;
	private final MembershipPricingService pricingService;
	private final com.gymos.memberships.application.MembershipEnrollmentService enrollmentService;
	private final ClientBranchAccess clients;

	public MembershipController(
			MembershipService membershipService,
			MembershipPricingService pricingService,
			com.gymos.memberships.application.MembershipEnrollmentService enrollmentService,
			ClientBranchAccess clients
	) {
		this.membershipService = membershipService;
		this.pricingService = pricingService;
		this.enrollmentService = enrollmentService;
		this.clients = clients;
	}

	@PostMapping("/membership-quotes")
	MembershipQuoteResult quote(
			@AuthenticationPrincipal GymOsPrincipal principal,
			@Valid @RequestBody MembershipQuoteRequest request
	) {
		clients.requireVisible(principal, request.clientId());
		return pricingService.quote(principal, request.clientId(), request.planId());
	}

	@PostMapping("/membership-enrollments")
	RenewMembershipResponse enroll(
			@AuthenticationPrincipal GymOsPrincipal principal,
			@Valid @RequestBody MembershipEnrollmentRequest request,
			HttpServletRequest servletRequest
	) {
		clients.requireVisible(principal, request.clientId());
		return toResponse(enrollmentService.enroll(
				principal, request.toCommand(), correlationId(servletRequest)));
	}

	@GetMapping("/clients/{clientId}/memberships")
	List<MembershipSummary> clientMemberships(
			@AuthenticationPrincipal GymOsPrincipal principal,
			@PathVariable UUID clientId
	) {
		clients.requireVisible(principal, clientId);
		return membershipService.forClient(principal, clientId);
	}

	@GetMapping("/memberships/{membershipId}")
	MembershipSummary detail(
			@AuthenticationPrincipal GymOsPrincipal principal,
			@PathVariable UUID membershipId
	) {
		return membershipService.detail(principal, membershipId);
	}

	@GetMapping("/memberships/{membershipId}/periods")
	List<MembershipPeriodSummary> periods(
			@AuthenticationPrincipal GymOsPrincipal principal,
			@PathVariable UUID membershipId
	) {
		return membershipService.periods(principal, membershipId);
	}

	@PostMapping("/memberships/{membershipId}/renew")
	RenewMembershipResponse renew(
			@AuthenticationPrincipal GymOsPrincipal principal,
			@PathVariable UUID membershipId,
			@Valid @RequestBody RenewMembershipRequest request,
			HttpServletRequest servletRequest
	) {
		return toResponse(membershipService.renew(principal, membershipId, request.toCommand(), correlationId(servletRequest)));
	}

	@PostMapping("/memberships/{membershipId}/change-plan")
	RenewMembershipResponse changePlan(
			@AuthenticationPrincipal GymOsPrincipal principal,
			@PathVariable UUID membershipId,
			@Valid @RequestBody ChangePlanRequest request,
			HttpServletRequest servletRequest
	) {
		return toResponse(membershipService.changePlan(principal, membershipId, request.toCommand(), correlationId(servletRequest)));
	}

	private String correlationId(HttpServletRequest request) {
		Object value = request.getAttribute(Correlation.ATTRIBUTE);
		return value == null ? "unknown" : value.toString();
	}

	public record RenewMembershipRequest(
			@NotNull @Pattern(regexp = "CASH|TRANSFER|CARD_MANUAL|CARD_MERCADO_PAGO") String method,
			@Size(max = 160) String reference,
			boolean validateNow,
			@Size(max = 2000) String notes,
			@Size(max = 160) String idempotencyKey
	) {
		RenewMembershipCommand toCommand() {
			return new RenewMembershipCommand(method, reference, validateNow, notes, idempotencyKey);
		}
	}

	public record ChangePlanRequest(
			@NotNull UUID targetPlanId,
			@NotNull @Pattern(regexp = "CASH|TRANSFER|CARD_MANUAL|CARD_MERCADO_PAGO") String method,
			@Size(max = 160) String reference,
			boolean validateNow,
			@Size(max = 2000) String notes,
			@Size(max = 160) String idempotencyKey
	) {
		ChangeMembershipPlanCommand toCommand() {
			return new ChangeMembershipPlanCommand(targetPlanId, method, reference, validateNow, notes, idempotencyKey);
		}
	}

	public record MembershipQuoteRequest(
			@NotNull UUID clientId,
			@NotNull UUID planId
	) {
	}

	public record MembershipEnrollmentRequest(
			@NotNull UUID clientId,
			@NotNull UUID planId,
			@NotNull @Pattern(regexp = "CASH|TRANSFER|CARD_MANUAL|CARD_MERCADO_PAGO") String method,
			@Size(max = 160) String reference,
			boolean validateNow,
			@Size(max = 2000) String notes,
			@Size(max = 160) String idempotencyKey
	) {
		EnrollMembershipCommand toCommand() {
			return new EnrollMembershipCommand(
					clientId, planId, method, reference, validateNow, notes, idempotencyKey);
		}
	}

	public record RenewMembershipResponse(
			UUID saleId,
			String saleNumber,
			String saleStatus,
			long totalCents,
			long paidCents,
			long balanceCents,
			String currency,
			MembershipSummaryResult membership,
			UUID paymentId
	) {
	}

	private RenewMembershipResponse toResponse(MembershipSaleResult result) {
		return new RenewMembershipResponse(
				result.saleId(),
				result.saleNumber(),
				result.saleStatus(),
				result.totalCents(),
				result.paidCents(),
				result.balanceCents(),
				result.currency(),
				result.membership(),
				result.paymentId()
		);
	}
}
