package com.gymos.access.application.command;

import java.time.LocalDate;
import java.util.UUID;

public record CreateVisitPassCommand(
		UUID clientId,
		UUID saleId,
		String passType,
		int totalVisits,
		LocalDate validFrom,
		LocalDate validUntil
) {
}
