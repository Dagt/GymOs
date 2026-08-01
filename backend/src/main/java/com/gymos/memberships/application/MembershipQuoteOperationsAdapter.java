package com.gymos.memberships.application;

import com.gymos.common.security.GymOsPrincipal;
import com.gymos.memberships.api.operations.MembershipQuoteOperations;
import com.gymos.memberships.application.result.MembershipQuoteResult;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class MembershipQuoteOperationsAdapter implements MembershipQuoteOperations {
    private final MembershipPricingService pricingService;

    public MembershipQuoteOperationsAdapter(MembershipPricingService pricingService) {
        this.pricingService = pricingService;
    }

    @Override
    public Quote quote(GymOsPrincipal principal, UUID clientId, UUID planId) {
        MembershipQuoteResult result = pricingService.quote(principal, clientId, planId);
        return new Quote(
                result.clientId(),
                result.planId(),
                result.planName(),
                result.listPriceCents(),
                result.discountCents(),
                result.totalCents(),
                result.currency(),
                result.appliedBenefits().stream()
                        .map(benefit -> new Benefit(
                                benefit.benefitId(),
                                benefit.code(),
                                benefit.name(),
                                benefit.discountCents()
                        ))
                        .toList()
        );
    }
}
