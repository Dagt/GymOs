package com.gymos.catalog.application.command;

public record UpdateMembershipPlanCommand(
		String name,
		String description,
		long priceCents,
		String currency,
		int durationDays,
		int graceDays,
		String benefits,
		boolean multiBranchAccess,
		boolean selfServiceAcquisitionEnabled,
		String registrationFeePolicy,
		String status
) {
	public UpdateMembershipPlanCommand(
			String name, String description, long priceCents, String currency, int durationDays,
			int graceDays, String benefits, boolean multiBranchAccess, String status
	) {
		this(name, description, priceCents, currency, durationDays, graceDays, benefits,
				multiBranchAccess, true, "DEFAULT", status);
	}
}
