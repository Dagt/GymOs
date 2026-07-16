package com.gymos.selfservice.application.result;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record SelfServiceMembershipSummaryResult(
		UUID id,
		UUID clientId,
		UUID planId,
		String planName,
		UUID saleId,
		String saleNumber,
		String status,
		OffsetDateTime activatedAt,
		LocalDate startsOn,
		LocalDate endsOn,
		LocalDate graceEndsOn
) {
}
