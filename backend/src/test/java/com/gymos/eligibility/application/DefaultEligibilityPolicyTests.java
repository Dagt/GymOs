package com.gymos.eligibility.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.gymos.eligibility.api.EligibilityPolicy.ActiveMembership;
import com.gymos.eligibility.api.EligibilityPolicy.BenefitType;
import com.gymos.eligibility.api.EligibilityPolicy.EligibleBenefit;
import com.gymos.eligibility.api.EligibilityPolicy.EligibilityRequest;
import com.gymos.eligibility.api.EligibilityPolicy.Reason;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DefaultEligibilityPolicyTests {
	private final EligibilityFactsProvider facts = mock(EligibilityFactsProvider.class);
	private final DefaultEligibilityPolicy policy = new DefaultEligibilityPolicy(facts);
	private final UUID organizationId = UUID.randomUUID();
	private final UUID branchId = UUID.randomUUID();
	private final UUID clientId = UUID.randomUUID();
	private final UUID planId = UUID.randomUUID();
	private final UUID membershipId = UUID.randomUUID();
	private final LocalDate effectiveOn = LocalDate.of(2026, 7, 29);
	private final ActiveMembership membership = new ActiveMembership(
			membershipId, planId, "Plan Plus",
			effectiveOn.minusDays(5), effectiveOn.plusDays(24), effectiveOn.plusDays(27));

	@BeforeEach
	void activeClient() {
		when(facts.clientStatus(organizationId, clientId)).thenReturn(Optional.of("ACTIVE"));
		when(facts.activeMembership(organizationId, branchId, clientId, effectiveOn))
				.thenReturn(Optional.of(membership));
	}

	@Test
	void exposesPricingBenefitsThroughTheSamePolicy() {
		EligibleBenefit discount = benefit(UUID.randomUUID(), BenefitType.PRICING);
		when(facts.pricingBenefits(organizationId, branchId, clientId, effectiveOn))
				.thenReturn(List.of(discount));

		var decision = policy.evaluate(EligibilityRequest.membershipPricing(
				organizationId, branchId, clientId, effectiveOn));

		assertThat(decision.eligible()).isTrue();
		assertThat(decision.benefits()).containsExactly(discount);
	}

	@Test
	void authorizesBookingFromTheMembershipPlanAtTheRequestedDate() {
		var allowed = policy.evaluate(EligibilityRequest.booking(
				organizationId, branchId, clientId, effectiveOn, Set.of(planId)));
		var denied = policy.evaluate(EligibilityRequest.booking(
				organizationId, branchId, clientId, effectiveOn, Set.of(UUID.randomUUID())));

		assertThat(allowed.eligible()).isTrue();
		assertThat(allowed.membership()).isEqualTo(membership);
		assertThat(denied.reason()).isEqualTo(Reason.PLAN_NOT_ALLOWED);
	}

	@Test
	void authorizesAccessOnlyWhenTheCurrentPlanGrantsOneRequiredBenefit() {
		UUID grantedBenefitId = UUID.randomUUID();
		EligibleBenefit accessBenefit = benefit(grantedBenefitId, BenefitType.ACCESS);
		when(facts.accessBenefits(organizationId, branchId, clientId, effectiveOn))
				.thenReturn(List.of(accessBenefit));

		var allowed = policy.evaluate(EligibilityRequest.access(
				organizationId, branchId, clientId, effectiveOn,
				Set.of(grantedBenefitId, UUID.randomUUID())));
		var denied = policy.evaluate(EligibilityRequest.access(
				organizationId, branchId, clientId, effectiveOn, Set.of(UUID.randomUUID())));

		assertThat(allowed.eligible()).isTrue();
		assertThat(allowed.benefits()).containsExactly(accessBenefit);
		assertThat(denied.reason()).isEqualTo(Reason.BENEFIT_NOT_GRANTED);
	}

	@Test
	void inactiveClientIsDeniedConsistentlyForEveryPurpose() {
		when(facts.clientStatus(organizationId, clientId)).thenReturn(Optional.of("SUSPENDED"));

		assertThat(policy.evaluate(EligibilityRequest.membershipPricing(
				organizationId, branchId, clientId, effectiveOn)).reason())
				.isEqualTo(Reason.CLIENT_NOT_ACTIVE);
		assertThat(policy.evaluate(EligibilityRequest.booking(
				organizationId, branchId, clientId, effectiveOn, Set.of())).reason())
				.isEqualTo(Reason.CLIENT_NOT_ACTIVE);
		assertThat(policy.evaluate(EligibilityRequest.access(
				organizationId, branchId, clientId, effectiveOn, Set.of())).reason())
				.isEqualTo(Reason.CLIENT_NOT_ACTIVE);
	}

	private EligibleBenefit benefit(UUID id, BenefitType type) {
		return new EligibleBenefit(
				id, "BENEFIT", "Beneficio", type,
				type == BenefitType.PRICING ? "PERCENT" : "NONE",
				null, type == BenefitType.PRICING ? 10.0 : null,
				false, 10,
				type == BenefitType.ACCESS ? membershipId : null,
				type == BenefitType.ACCESS ? planId : null,
				type == BenefitType.ACCESS ? membership.planName() : null
		);
	}
}
