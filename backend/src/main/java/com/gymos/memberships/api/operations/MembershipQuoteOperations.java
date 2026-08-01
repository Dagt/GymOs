package com.gymos.memberships.api.operations;

import com.gymos.common.security.GymOsPrincipal;
import java.util.List;
import java.util.UUID;

public interface MembershipQuoteOperations {
    Quote quote(GymOsPrincipal principal, UUID clientId, UUID planId);

    record Quote(
            UUID clientId,
            UUID planId,
            String planName,
            long listPriceCents,
            long discountCents,
            long totalCents,
            String currency,
            List<Benefit> appliedBenefits
    ) {}

    record Benefit(
            UUID benefitId,
            String code,
            String name,
            long discountCents
    ) {}
}
