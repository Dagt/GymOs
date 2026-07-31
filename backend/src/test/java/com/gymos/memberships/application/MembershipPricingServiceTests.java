package com.gymos.memberships.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.gymos.eligibility.api.EligibilityPolicy;
import com.gymos.eligibility.api.EligibilityPolicy.BenefitType;
import com.gymos.eligibility.api.EligibilityPolicy.EligibleBenefit;
import com.gymos.eligibility.api.EligibilityPolicy.EligibilityDecision;
import com.gymos.eligibility.api.EligibilityPolicy.Reason;
import com.gymos.memberships.application.result.MembershipQuoteResult;
import com.gymos.memberships.infrastructure.MembershipRepository;
import com.gymos.memberships.infrastructure.MembershipRepository.PlanForRenewal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MembershipPricingServiceTests {
	private final MembershipRepository membershipRepository = mock(MembershipRepository.class);
	private final EligibilityPolicy eligibilityPolicy = mock(EligibilityPolicy.class);
	private final MembershipPricingService service = new MembershipPricingService(membershipRepository, eligibilityPolicy);
	private final UUID organizationId = UUID.randomUUID();
	private final UUID branchId = UUID.randomUUID();
	private final UUID clientId = UUID.randomUUID();
	private final PlanForRenewal plan = new PlanForRenewal(
			UUID.randomUUID(), UUID.randomUUID(), "Plan mensual", 30, 3, false, 10_000, "MXN");

	@Test
	void stacksOnlyCombinableBenefitsInPriorityOrder() {
		when(eligibilityPolicy.evaluate(any())).thenReturn(eligible(List.of(
				benefit("STUDENT", "PERCENT", null, 10.0, true, 10),
				benefit("AGREEMENT", "FIXED", 1_000, null, true, 20)
		)));

		MembershipQuoteResult quote = service.quote(organizationId, branchId, clientId, plan);

		assertThat(quote.listPriceCents()).isEqualTo(10_000);
		assertThat(quote.discountCents()).isEqualTo(2_000);
		assertThat(quote.totalCents()).isEqualTo(8_000);
		assertThat(quote.appliedBenefits()).hasSize(2);
	}

	@Test
	void selectsBestExclusiveAlternativeInsteadOfStackWhenCheaper() {
		when(eligibilityPolicy.evaluate(any())).thenReturn(eligible(List.of(
				benefit("STUDENT", "PERCENT", null, 10.0, true, 10),
				benefit("PROMO", "SPECIAL_PRICE", 4_000, null, false, 20)
		)));

		MembershipQuoteResult quote = service.quote(organizationId, branchId, clientId, plan);

		assertThat(quote.totalCents()).isEqualTo(4_000);
		assertThat(quote.discountCents()).isEqualTo(6_000);
		assertThat(quote.appliedBenefits()).singleElement()
				.satisfies(applied -> assertThat(applied.code()).isEqualTo("PROMO"));
	}

	private EligibilityDecision eligible(List<EligibleBenefit> benefits) {
		return new EligibilityDecision(true, Reason.ELIGIBLE, null, benefits);
	}

	private EligibleBenefit benefit(
			String code,
			String type,
			Integer value,
			Double percent,
			boolean combinable,
			int priority
	) {
		return new EligibleBenefit(
				UUID.randomUUID(), code, code, BenefitType.PRICING, type, value, percent,
				combinable, priority, null, null, null);
	}
}
