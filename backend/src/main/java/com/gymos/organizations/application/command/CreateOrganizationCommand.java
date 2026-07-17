package com.gymos.organizations.application.command;

public record CreateOrganizationCommand(
		String legalName,
		String tradeName,
		String taxId,
		String timezone,
		String defaultCurrency,
		String contactEmail,
		String contactPhone,
		String brandName,
		String logoUrl,
		String primaryColor,
		String accentColor,
		String surfaceColor,
		String firstBranchName,
		String firstBranchCode,
		String firstBranchPhone
) {
}
