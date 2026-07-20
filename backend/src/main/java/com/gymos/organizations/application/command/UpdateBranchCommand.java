package com.gymos.organizations.application.command;

public record UpdateBranchCommand(
		String name,
		String code,
		String timezone,
		String phone,
		String brandName,
		String logoUrl,
		String primaryColor,
		String accentColor,
		String surfaceColor
) {
}
