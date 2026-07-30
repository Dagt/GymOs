package com.gymos.sales.model;

import java.util.UUID;

public record AppliedBenefitDetail(
		UUID benefitId,
		String code,
		String name,
		long discountCents,
		int applicationOrder
) {
}
