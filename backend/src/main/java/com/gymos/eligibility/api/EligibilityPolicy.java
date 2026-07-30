package com.gymos.eligibility.api;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Single entry point for client eligibility decisions.
 *
 * <p>The requesting module supplies the target restrictions (plans or benefits);
 * this policy owns the shared client, branch, date, membership and benefit rules.
 */
public interface EligibilityPolicy {
	EligibilityDecision evaluate(EligibilityRequest request);

	enum Purpose {
		MEMBERSHIP_PRICING,
		BOOKING,
		ACCESS
	}

	enum Reason {
		ELIGIBLE,
		CLIENT_NOT_FOUND,
		CLIENT_NOT_ACTIVE,
		ACTIVE_MEMBERSHIP_REQUIRED,
		PLAN_NOT_ALLOWED,
		BENEFIT_NOT_GRANTED
	}

	enum BenefitType {
		PRICING,
		ACCESS
	}

	record EligibilityRequest(
			UUID organizationId,
			UUID branchId,
			UUID clientId,
			LocalDate effectiveOn,
			Purpose purpose,
			Set<UUID> requiredPlanIds,
			Set<UUID> requiredBenefitIds
	) {
		public EligibilityRequest {
			requiredPlanIds = requiredPlanIds == null ? Set.of() : Set.copyOf(requiredPlanIds);
			requiredBenefitIds = requiredBenefitIds == null ? Set.of() : Set.copyOf(requiredBenefitIds);
		}

		public static EligibilityRequest membershipPricing(
				UUID organizationId, UUID branchId, UUID clientId, LocalDate effectiveOn
		) {
			return new EligibilityRequest(
					organizationId, branchId, clientId, effectiveOn,
					Purpose.MEMBERSHIP_PRICING, Set.of(), Set.of());
		}

		public static EligibilityRequest booking(
				UUID organizationId, UUID branchId, UUID clientId, LocalDate effectiveOn,
				Set<UUID> requiredPlanIds
		) {
			return new EligibilityRequest(
					organizationId, branchId, clientId, effectiveOn,
					Purpose.BOOKING, requiredPlanIds, Set.of());
		}

		public static EligibilityRequest access(
				UUID organizationId, UUID branchId, UUID clientId, LocalDate effectiveOn,
				Set<UUID> requiredBenefitIds
		) {
			return new EligibilityRequest(
					organizationId, branchId, clientId, effectiveOn,
					Purpose.ACCESS, Set.of(), requiredBenefitIds);
		}
	}

	record EligibilityDecision(
			boolean eligible,
			Reason reason,
			ActiveMembership membership,
			List<EligibleBenefit> benefits
	) {
		public EligibilityDecision {
			benefits = benefits == null ? List.of() : List.copyOf(benefits);
		}
	}

	record ActiveMembership(
			UUID id,
			UUID planId,
			String planName,
			LocalDate startsOn,
			LocalDate endsOn,
			LocalDate graceEndsOn
	) {
	}

	record EligibleBenefit(
			UUID id,
			String code,
			String name,
			BenefitType benefitType,
			String discountType,
			Integer discountValueCents,
			Double discountPercent,
			boolean combinable,
			int priority,
			UUID membershipId,
			UUID planId,
			String planName
	) {
	}
}
