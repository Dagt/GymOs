package com.gymos.platformbilling.api.operations;

import com.gymos.common.security.GymOsPrincipal;
import com.gymos.platformbilling.application.PlatformBillingService;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
class PlatformBillingOperationsAdapter implements PlatformBillingOperations {
	private final PlatformBillingService service;

	PlatformBillingOperationsAdapter(PlatformBillingService service) {
		this.service = service;
	}

	@Override
	public List<BranchCommercialStatus> branches(GymOsPrincipal principal) {
		return service.overview(principal).branches().stream()
				.map(branch -> new BranchCommercialStatus(
						branch.organizationId(), branch.activeMembers(), branch.monthlyAmount(),
						branch.subscriptionStatus(), branch.paymentStatus(), branch.implementationFee(),
						branch.implementationPaid(), branch.nextBillingDate()))
				.toList();
	}
}
