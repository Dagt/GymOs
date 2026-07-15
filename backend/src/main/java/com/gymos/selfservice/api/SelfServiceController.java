package com.gymos.selfservice.api;

import com.gymos.common.api.Correlation;
import com.gymos.memberships.application.command.ChangeMembershipPlanCommand;
import com.gymos.memberships.application.command.RenewMembershipCommand;
import com.gymos.selfservice.application.SelfServiceService;
import com.gymos.selfservice.application.command.VerifySessionCommand;
import com.gymos.selfservice.application.result.CreateSessionResult;
import com.gymos.selfservice.application.result.SelfServiceMembershipSaleResult;
import com.gymos.selfservice.application.result.SelfServiceMembershipSummaryResult;
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
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/api/v1/self-service")
public class SelfServiceController {
	private final SelfServiceService selfServiceService;

	public SelfServiceController(SelfServiceService selfServiceService) {
		this.selfServiceService = selfServiceService;
	}

	@PostMapping("/terminals/{terminalCode}/sessions")
	CreateSessionResponse createSession(@PathVariable String terminalCode, HttpServletRequest servletRequest) {
		return toResponse(selfServiceService.createSession(terminalCode, servletRequest.getRemoteAddr(), correlationId(servletRequest)));
	}

	@PostMapping("/sessions/{sessionId}/verify")
	SelfServiceSummaryResponse verify(
			@PathVariable UUID sessionId,
			@Valid @RequestBody VerifySessionRequest request,
			HttpServletRequest servletRequest
	) {
		return toResponse(selfServiceService.verify(sessionId, request.toCommand(), correlationId(servletRequest)));
	}

	@GetMapping("/sessions/{sessionId}/summary")
	SelfServiceSummaryResponse summary(@PathVariable UUID sessionId) {
		return toResponse(selfServiceService.summary(sessionId));
	}

	@GetMapping("/sessions/{sessionId}/membership-options")
	List<PlanOptionRecord> membershipOptions(@PathVariable UUID sessionId) {
		return selfServiceService.membershipOptions(sessionId);
	}

	@PostMapping("/sessions/{sessionId}/membership-renewals/{membershipId}")
	SelfServiceMembershipSaleResponse renew(
			@PathVariable UUID sessionId,
			@PathVariable UUID membershipId,
			@Valid @RequestBody RenewMembershipRequest request,
			HttpServletRequest servletRequest
	) {
		return toResponse(selfServiceService.renew(sessionId, membershipId, request.toCommand(), correlationId(servletRequest)));
	}

	@PostMapping("/sessions/{sessionId}/membership-change-requests/{membershipId}")
	SelfServiceMembershipSaleResponse changePlan(
			@PathVariable UUID sessionId,
			@PathVariable UUID membershipId,
			@Valid @RequestBody ChangePlanRequest request,
			HttpServletRequest servletRequest
	) {
		return toResponse(selfServiceService.changePlan(sessionId, membershipId, request.toCommand(), correlationId(servletRequest)));
	}

	@PostMapping("/sessions/{sessionId}/end")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void end(@PathVariable UUID sessionId, HttpServletRequest servletRequest) {
		selfServiceService.end(sessionId, correlationId(servletRequest));
	}

	@PatchMapping("/sessions/{sessionId}/contact-data")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void contactDataPlaceholder(@PathVariable UUID sessionId) {
		selfServiceService.summary(sessionId);
	}

	private String correlationId(HttpServletRequest request) {
		Object value = request.getAttribute(Correlation.ATTRIBUTE);
		return value == null ? "unknown" : value.toString();
	}

	private CreateSessionResponse toResponse(CreateSessionResult result) {
		return new CreateSessionResponse(result.sessionId(), result.terminalName(), result.expiresAt(), result.verified());
	}

	private SelfServiceSummaryResponse toResponse(SelfServiceSummaryResult result) {
		return new SelfServiceSummaryResponse(result.sessionId(), result.expiresAt(), result.clientName(), result.memberships(), result.pendingPayments());
	}

	private SelfServiceMembershipSaleResponse toResponse(SelfServiceMembershipSaleResult result) {
		return new SelfServiceMembershipSaleResponse(
				result.saleId(),
				result.saleNumber(),
				result.saleStatus(),
				result.totalCents(),
				result.paidCents(),
				result.balanceCents(),
				result.currency(),
				result.membership()
		);
	}

	public record VerifySessionRequest(@NotBlank String identifier) {
		private VerifySessionCommand toCommand() {
			return new VerifySessionCommand(identifier);
		}
	}

	public record RenewMembershipRequest(
			@NotBlank @Pattern(regexp = "CASH|TRANSFER|CARD_MANUAL") String method,
			@Size(max = 160) String reference,
			boolean validateNow,
			@Size(max = 2000) String notes,
			@Size(max = 160) String idempotencyKey
	) {
		private RenewMembershipCommand toCommand() {
			return new RenewMembershipCommand(method, reference, validateNow, notes, idempotencyKey);
		}
	}

	public record ChangePlanRequest(
			@NotNull UUID targetPlanId,
			@NotBlank @Pattern(regexp = "CASH|TRANSFER|CARD_MANUAL") String method,
			@Size(max = 160) String reference,
			boolean validateNow,
			@Size(max = 2000) String notes,
			@Size(max = 160) String idempotencyKey
	) {
		private ChangeMembershipPlanCommand toCommand() {
			return new ChangeMembershipPlanCommand(targetPlanId, method, reference, validateNow, notes, idempotencyKey);
		}
	}

	public record CreateSessionResponse(
			UUID sessionId,
			String terminalName,
			OffsetDateTime expiresAt,
			boolean verified
	) {
	}

	public record SelfServiceSummaryResponse(
			UUID sessionId,
			OffsetDateTime expiresAt,
			String clientName,
			List<MembershipRecord> memberships,
			List<PendingPaymentRecord> pendingPayments
	) {
	}

	public record SelfServiceMembershipSaleResponse(
			UUID saleId,
			String saleNumber,
			String saleStatus,
			long totalCents,
			long paidCents,
			long balanceCents,
			String currency,
			SelfServiceMembershipSummaryResult membership
	) {
	}
}
