package com.gymos.memberships.api;

import com.gymos.common.security.GymOsPrincipal;
import com.gymos.memberships.application.MembershipService;
import com.gymos.memberships.infrastructure.MembershipRepository.MembershipSummary;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
}
