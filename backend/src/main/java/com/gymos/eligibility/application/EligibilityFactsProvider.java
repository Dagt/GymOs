package com.gymos.eligibility.application;

import com.gymos.eligibility.api.EligibilityPolicy.ActiveMembership;
import com.gymos.eligibility.api.EligibilityPolicy.EligibleBenefit;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EligibilityFactsProvider {
	Optional<String> clientStatus(UUID organizationId, UUID clientId);

	Optional<ActiveMembership> activeMembership(
			UUID organizationId, UUID branchId, UUID clientId, LocalDate effectiveOn);

	List<EligibleBenefit> pricingBenefits(
			UUID organizationId, UUID branchId, UUID clientId, LocalDate effectiveOn, UUID targetPlanId);

	List<EligibleBenefit> accessBenefits(
			UUID organizationId, UUID branchId, UUID clientId, LocalDate effectiveOn);
}
