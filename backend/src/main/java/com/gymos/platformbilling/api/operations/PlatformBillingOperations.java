package com.gymos.platformbilling.api.operations;

import com.gymos.common.security.GymOsPrincipal;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface PlatformBillingOperations {
	List<BranchCommercialStatus> branches(GymOsPrincipal principal);

	record BranchCommercialStatus(
			UUID organizationId,
			int activeMembers,
			BigDecimal monthlyAmount,
			String subscriptionStatus,
			String paymentStatus,
			BigDecimal implementationFee,
			boolean implementationPaid,
			LocalDate nextBillingDate
	) {
	}
}
