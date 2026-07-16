package com.gymos.memberships.api.operations;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record MembershipSummaryOperationResult(
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
