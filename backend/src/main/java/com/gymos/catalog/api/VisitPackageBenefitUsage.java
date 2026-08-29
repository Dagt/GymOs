package com.gymos.catalog.api;

import java.util.Objects;
import java.util.UUID;

/** Commercial behavior of one ACCESS benefit inside one visit package. */
public record VisitPackageBenefitUsage(
		UUID benefitId,
		VisitPackageBenefitUsageMode usageMode
) {
	public VisitPackageBenefitUsage {
		Objects.requireNonNull(benefitId, "benefitId");
		Objects.requireNonNull(usageMode, "usageMode");
	}
}
