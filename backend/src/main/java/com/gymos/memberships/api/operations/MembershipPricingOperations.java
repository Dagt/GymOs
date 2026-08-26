package com.gymos.memberships.api.operations;

import com.gymos.common.security.GymOsPrincipal;
import java.util.List;
import java.util.UUID;

public interface MembershipPricingOperations {
	MembershipQuoteView quoteForCheckout(GymOsPrincipal principal, UUID clientId, UUID planId);

	record MembershipQuoteView(
			UUID clientId, UUID planId, String planName, long planPriceCents,
			long registrationFeeCents, String registrationFeeLabel, long listPriceCents,
			long discountCents, long totalCents, String currency,
			List<AppliedBenefitView> appliedBenefits
	) {}

	record AppliedBenefitView(UUID benefitId, String code, String name, long discountCents) {}
}
