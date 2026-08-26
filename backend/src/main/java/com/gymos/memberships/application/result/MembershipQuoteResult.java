package com.gymos.memberships.application.result;

import java.util.List;
import java.util.UUID;

public record MembershipQuoteResult(
		UUID clientId,
		UUID planId,
		String planName,
		long planPriceCents,
		long registrationFeeCents,
		String registrationFeeLabel,
		long listPriceCents,
		long discountCents,
		long totalCents,
		String currency,
		List<AppliedBenefit> appliedBenefits
) {
	public record AppliedBenefit(
			UUID benefitId,
			String code,
			String name,
			long discountCents
	) {
	}
}
