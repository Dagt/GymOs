package com.gymos.catalog.application.command;

import java.util.UUID;

public record CreatePlanCommand(
		UUID productId,
		int durationDays,
		int graceDays,
		String benefitsJson,
		boolean multiBranchAccess,
		boolean selfServiceAcquisitionEnabled,
		String registrationFeePolicy,
		String status
) {
	public CreatePlanCommand(
			UUID productId, int durationDays, int graceDays, String benefitsJson,
			boolean multiBranchAccess, String status
	) {
		this(productId, durationDays, graceDays, benefitsJson, multiBranchAccess, true, "DEFAULT", status);
	}
}
