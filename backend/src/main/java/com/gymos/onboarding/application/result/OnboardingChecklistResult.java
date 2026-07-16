package com.gymos.onboarding.application.result;

public record OnboardingChecklistResult(
		boolean organizationProfileComplete,
		boolean branchConfigured,
		boolean hasMembershipPlans,
		boolean hasRetailProducts,
		boolean hasKioskTerminal,
		boolean hasClients,
		boolean readyForPilot,
		long activePlanCount,
		long activeProductCount,
		long clientCount,
		long activeKioskCount
) {
}
