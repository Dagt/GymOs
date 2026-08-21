package com.gymos.memberships.application;

import com.gymos.common.api.BusinessException;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.common.time.BusinessTime;
import com.gymos.memberships.infrastructure.MembershipRepository;
import com.gymos.memberships.infrastructure.MembershipRepository.MembershipSummary;
import com.gymos.memberships.infrastructure.MembershipRepository.PlanForRenewal;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
class MembershipOperationPolicy {
	private final MembershipRepository membershipRepository;

	MembershipOperationPolicy(MembershipRepository membershipRepository) {
		this.membershipRepository = membershipRepository;
	}

	MembershipSummary requireRenewableMembership(
			GymOsPrincipal principal,
			UUID membershipId,
			String statusErrorCode,
			String statusErrorMessage
	) {
		MembershipSummary current = requireMembership(principal, membershipId);
		if (!"ACTIVE".equals(current.status()) && !"EXPIRED".equals(current.status())) {
			throw new BusinessException(statusErrorCode, statusErrorMessage);
		}
		return current;
	}

	MembershipSummary requireActiveMembership(
			GymOsPrincipal principal,
			UUID membershipId,
			String statusErrorCode,
			String statusErrorMessage
	) {
		MembershipSummary current = requireMembership(principal, membershipId);
		if (!"ACTIVE".equals(current.status())) {
			throw new BusinessException(statusErrorCode, statusErrorMessage);
		}
		return current;
	}

	private MembershipSummary requireMembership(GymOsPrincipal principal, UUID membershipId) {
		MembershipAccessGuard.requireBranch(principal);
		membershipRepository.expireMembershipIfDue(
				principal.organizationId(), membershipId, BusinessTime.today());
		MembershipSummary current = membershipRepository.byIdForUpdate(principal.organizationId(), principal.branchId(), membershipId)
				.orElseThrow(() -> new BusinessException("MEMBERSHIP_NOT_FOUND", "La membresia no existe o no esta disponible."));
		return current;
	}

	PlanForRenewal requirePlanForRenewal(UUID organizationId, UUID planId, String message) {
		return membershipRepository.planForRenewal(organizationId, planId)
				.orElseThrow(() -> new BusinessException("PLAN_NOT_AVAILABLE", message));
	}

	void requireDifferentPlan(MembershipSummary current, UUID targetPlanId) {
		if (current.planId().equals(targetPlanId)) {
			throw new BusinessException("MEMBERSHIP_PLAN_UNCHANGED", "Selecciona un plan diferente.");
		}
	}

	void requirePlanChangePaymentCanBeStarted(boolean validateNow, String method) {
		String normalizedMethod = method.trim().toUpperCase();
		if (!validateNow && !"CASH".equals(normalizedMethod) && !"CARD_ONLINE".equals(normalizedMethod)) {
			throw new BusinessException("PLAN_CHANGE_REQUIRES_COMPLETED_PAYMENT", "El cambio de plan requiere pago completado.");
		}
	}
}
