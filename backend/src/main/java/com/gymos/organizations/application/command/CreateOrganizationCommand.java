package com.gymos.organizations.application.command;

public record CreateOrganizationCommand(
		String legalName,
		String tradeName,
		String taxId,
		String timezone,
		String defaultCurrency,
		String contactEmail,
		String contactPhone,
		String firstBranchName,
		String firstBranchCode,
		String firstBranchPhone
) {
}
