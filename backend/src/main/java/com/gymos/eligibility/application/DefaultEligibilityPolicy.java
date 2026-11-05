package com.gymos.eligibility.application;

import com.gymos.eligibility.api.EligibilityPolicy;
import com.gymos.eligibility.api.EligibilityPolicy.ActiveMembership;
import com.gymos.eligibility.api.EligibilityPolicy.EligibilityDecision;
import com.gymos.eligibility.api.EligibilityPolicy.EligibilityRequest;
import com.gymos.eligibility.api.EligibilityPolicy.EligibleBenefit;
import com.gymos.eligibility.api.EligibilityPolicy.Reason;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class DefaultEligibilityPolicy implements EligibilityPolicy {
	private final EligibilityFactsProvider facts;

	public DefaultEligibilityPolicy(EligibilityFactsProvider facts) {
		this.facts = facts;
	}

	@Override
	public EligibilityDecision evaluate(EligibilityRequest request) {
		Optional<String> clientStatus = facts.clientStatus(request.organizationId(), request.clientId());
		if (clientStatus.isEmpty()) {
			return denied(Reason.CLIENT_NOT_FOUND, null);
		}
		if (!"ACTIVE".equals(clientStatus.get())) {
			return denied(Reason.CLIENT_NOT_ACTIVE, null);
		}
		return switch (request.purpose()) {
			case MEMBERSHIP_PRICING -> pricing(request);
			case BOOKING -> booking(request);
			case ACCESS -> access(request);
		};
	}

	@Override
	public boolean hasExpiredAccessMembership(UUID organizationId, UUID branchId, UUID clientId, LocalDate effectiveOn) {
		return facts.hasExpiredAccessMembership(organizationId, branchId, clientId, effectiveOn);
	}

	private EligibilityDecision pricing(EligibilityRequest request) {
		UUID targetPlanId = request.requiredPlanIds().stream().findFirst().orElse(null);
		List<EligibleBenefit> benefits = facts.pricingBenefits(
				request.organizationId(), request.branchId(), request.clientId(), request.effectiveOn(), targetPlanId);
		return new EligibilityDecision(true, Reason.ELIGIBLE, null, benefits);
	}

	private EligibilityDecision booking(EligibilityRequest request) {
		if (request.requiredPlanIds().isEmpty()) {
			return new EligibilityDecision(true, Reason.ELIGIBLE, null, List.of());
		}
		ActiveMembership membership = activeMembership(request).orElse(null);
		if (membership == null) {
			return denied(Reason.ACTIVE_MEMBERSHIP_REQUIRED, null);
		}
		if (!request.requiredPlanIds().contains(membership.planId())) {
			return denied(Reason.PLAN_NOT_ALLOWED, membership);
		}
		return new EligibilityDecision(true, Reason.ELIGIBLE, membership, List.of());
	}

	private EligibilityDecision access(EligibilityRequest request) {
		ActiveMembership membership = activeMembership(request).orElse(null);
		if (membership == null) {
			return denied(Reason.ACTIVE_MEMBERSHIP_REQUIRED, null);
		}
		List<EligibleBenefit> benefits = facts.accessBenefits(
				request.organizationId(), request.branchId(), request.clientId(), request.effectiveOn());
		Set<UUID> grantedIds = benefits.stream().map(EligibleBenefit::id).collect(java.util.stream.Collectors.toSet());
		if (!request.requiredBenefitIds().isEmpty()
				&& request.requiredBenefitIds().stream().noneMatch(grantedIds::contains)) {
			return new EligibilityDecision(false, Reason.BENEFIT_NOT_GRANTED, membership, benefits);
		}
		return new EligibilityDecision(true, Reason.ELIGIBLE, membership, benefits);
	}

	private Optional<ActiveMembership> activeMembership(EligibilityRequest request) {
		return facts.activeMembership(
				request.organizationId(), request.branchId(), request.clientId(), request.effectiveOn());
	}

	private EligibilityDecision denied(Reason reason, ActiveMembership membership) {
		return new EligibilityDecision(false, reason, membership, List.of());
	}
}
