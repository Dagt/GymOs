package com.gymos.memberships.api;

import com.gymos.common.api.Correlation;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.memberships.application.MembershipService;
import com.gymos.memberships.infrastructure.MembershipRepository.MembershipSummary;
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

	public MembershipController(MembershipService membershipService) {
		this.membershipService = membershipService;
	}

	@GetMapping("/clients/{clientId}/memberships")
	List<MembershipSummary> clientMemberships(
			@AuthenticationPrincipal GymOsPrincipal principal,
			@PathVariable UUID clientId
	) {
		return membershipService.forClient(principal, clientId);
	}

	@GetMapping("/memberships/{membershipId}")
	MembershipSummary detail(
			@AuthenticationPrincipal GymOsPrincipal principal,
			@PathVariable UUID membershipId
	) {
		return membershipService.detail(principal, membershipId);
	}

	@PostMapping("/memberships/{membershipId}/renew")
	RenewMembershipResponse renew(
			@AuthenticationPrincipal GymOsPrincipal principal,
			@PathVariable UUID membershipId,
			@Valid @RequestBody RenewMembershipRequest request,
			HttpServletRequest servletRequest
	) {
		return membershipService.renew(principal, membershipId, request, correlationId(servletRequest));
	}

	private String correlationId(HttpServletRequest request) {
		Object value = request.getAttribute(Correlation.ATTRIBUTE);
		return value == null ? "unknown" : value.toString();
	}

	public record RenewMembershipRequest(
			@NotNull @Pattern(regexp = "CASH|TRANSFER|CARD_MANUAL") String method,
			@Size(max = 160) String reference,
			boolean validateNow,
			@Size(max = 2000) String notes
	) {
	}

	public record RenewMembershipResponse(
			UUID saleId,
			String saleNumber,
			String saleStatus,
			long totalCents,
			long paidCents,
			long balanceCents,
			String currency,
			MembershipSummary membership
	) {
	}
}
