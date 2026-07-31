package com.gymos.memberships.application;

import com.gymos.common.api.BusinessException;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.common.time.BusinessTime;
import com.gymos.memberships.infrastructure.MembershipRepository;
import com.gymos.memberships.infrastructure.MembershipRepository.MembershipSummary;
import com.gymos.memberships.infrastructure.MembershipRepository.MembershipPeriodSummary;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class MembershipQueryService {
	private final MembershipRepository membershipRepository;

	MembershipQueryService(MembershipRepository membershipRepository) {
		this.membershipRepository = membershipRepository;
	}

	@Transactional
	List<MembershipSummary> forClient(GymOsPrincipal principal, UUID clientId) {
		MembershipAccessGuard.requireBranch(principal);
		if (!membershipRepository.clientExists(principal.organizationId(), clientId)) {
			throw new BusinessException("CLIENT_NOT_FOUND", "El cliente no existe o no esta disponible.");
		}
		membershipRepository.expireDueMemberships(
				principal.organizationId(), clientId, BusinessTime.today());
		return membershipRepository.forClient(principal.organizationId(), principal.branchId(), clientId);
	}

	@Transactional
	MembershipSummary detail(GymOsPrincipal principal, UUID membershipId) {
		MembershipAccessGuard.requireBranch(principal);
		membershipRepository.expireMembershipIfDue(
				principal.organizationId(), membershipId, BusinessTime.today());
		return membershipRepository.byId(principal.organizationId(), principal.branchId(), membershipId)
				.orElseThrow(() -> new BusinessException("MEMBERSHIP_NOT_FOUND", "La membresia no existe o no esta disponible."));
	}

	List<MembershipPeriodSummary> periods(GymOsPrincipal principal, UUID membershipId) {
		MembershipAccessGuard.requireBranch(principal);
		return membershipRepository.periods(principal.organizationId(), principal.branchId(), membershipId);
	}
}
