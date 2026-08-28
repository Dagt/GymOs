package com.gymos.catalog.application.command;

public record CreateMembershipPlanCommand(
		String sku,
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
	public CreateMembershipPlanCommand(
			String sku, String name, String description, long priceCents, String currency,
			int durationDays, int graceDays, String benefits, boolean multiBranchAccess, String status
	) {
		this(sku, name, description, priceCents, currency, durationDays, graceDays, benefits,
				multiBranchAccess, true, "DEFAULT", status);
	}
}
