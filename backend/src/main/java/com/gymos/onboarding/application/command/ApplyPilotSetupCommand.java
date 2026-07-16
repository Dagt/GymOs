package com.gymos.onboarding.application.command;

import java.util.List;

public record ApplyPilotSetupCommand(
		OrganizationProfile organization,
		BranchProfile branch,
		List<PilotPlan> plans,
		List<PilotProduct> products,
		String kioskTerminalCode,
		String kioskName
) {
	public record OrganizationProfile(
			String legalName,
			String tradeName,
			String taxId,
			String timezone,
			String defaultCurrency,
			String contactEmail,
			String contactPhone
	) {
	}

	public record BranchProfile(
			String name,
			String code,
			String timezone,
			String phone
	) {
	}

	public record PilotPlan(
			String sku,
			String name,
			String description,
			long priceCents,
			String currency,
			int durationDays,
			int graceDays,
			String benefitsJson
	) {
	}

	public record PilotProduct(
			String sku,
			String name,
			String description,
			String productType,
			long priceCents,
			String currency,
			boolean taxable
	) {
	}
}
