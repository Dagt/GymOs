package com.gymos.benefits.application.command;

import java.time.LocalDate;
import java.util.UUID;

public record AssignClientBenefitCommand(
		UUID clientId,
		UUID benefitId,
		UUID branchId,
		LocalDate validFrom,
		LocalDate validUntil,
		String notes
) {
}
