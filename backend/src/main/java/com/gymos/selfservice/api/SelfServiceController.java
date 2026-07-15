package com.gymos.selfservice.api;

import com.gymos.common.api.Correlation;
import com.gymos.memberships.api.MembershipController.ChangePlanRequest;
import com.gymos.memberships.api.MembershipController.RenewMembershipRequest;
import com.gymos.memberships.api.MembershipController.RenewMembershipResponse;
import com.gymos.selfservice.application.SelfServiceService;
import com.gymos.selfservice.infrastructure.SelfServiceRepository.MembershipRecord;
import com.gymos.selfservice.infrastructure.SelfServiceRepository.PendingPaymentRecord;
import com.gymos.selfservice.infrastructure.SelfServiceRepository.PlanOptionRecord;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
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
		return selfServiceService.createSession(terminalCode, servletRequest.getRemoteAddr(), correlationId(servletRequest));
	}

	@PostMapping("/sessions/{sessionId}/verify")
	SelfServiceSummaryResponse verify(
			@PathVariable UUID sessionId,
			@Valid @RequestBody VerifySessionRequest request,
			HttpServletRequest servletRequest
	) {
		return selfServiceService.verify(sessionId, request, correlationId(servletRequest));
	}

	@GetMapping("/sessions/{sessionId}/summary")
	SelfServiceSummaryResponse summary(@PathVariable UUID sessionId) {
		return selfServiceService.summary(sessionId);
	}

	@GetMapping("/sessions/{sessionId}/membership-options")
	List<PlanOptionRecord> membershipOptions(@PathVariable UUID sessionId) {
		return selfServiceService.membershipOptions(sessionId);
	}

	@PostMapping("/sessions/{sessionId}/membership-renewals/{membershipId}")
	RenewMembershipResponse renew(
			@PathVariable UUID sessionId,
			@PathVariable UUID membershipId,
			@Valid @RequestBody RenewMembershipRequest request,
			HttpServletRequest servletRequest
	) {
		return selfServiceService.renew(sessionId, membershipId, request, correlationId(servletRequest));
	}

	@PostMapping("/sessions/{sessionId}/membership-change-requests/{membershipId}")
	RenewMembershipResponse changePlan(
			@PathVariable UUID sessionId,
			@PathVariable UUID membershipId,
			@Valid @RequestBody ChangePlanRequest request,
			HttpServletRequest servletRequest
	) {
		return selfServiceService.changePlan(sessionId, membershipId, request, correlationId(servletRequest));
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

	public record VerifySessionRequest(@NotBlank String identifier) {
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
}
