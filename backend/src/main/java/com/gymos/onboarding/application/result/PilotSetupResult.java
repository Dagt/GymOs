package com.gymos.onboarding.application.result;

public record PilotSetupResult(
		OnboardingChecklistResult checklist,
		int productsCreated,
		int plansCreated,
		boolean kioskCreated
) {
}
