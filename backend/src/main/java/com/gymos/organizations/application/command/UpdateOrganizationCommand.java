package com.gymos.organizations.application.command;

public record UpdateOrganizationCommand(
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
		String surfaceColor
) {
}
