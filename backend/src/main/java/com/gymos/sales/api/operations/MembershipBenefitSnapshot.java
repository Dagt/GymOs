package com.gymos.sales.api.operations;

import java.util.UUID;

public record MembershipBenefitSnapshot(
		UUID benefitId,
		String code,
		String name,
		long discountCents
) {
}
