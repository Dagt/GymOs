package com.gymos.selfservice.application.result;

import java.time.LocalDate;
import java.util.UUID;

public record SelfServiceMembershipView(
		UUID id,
		UUID planId,
		String planName,
		String status,
		LocalDate startsOn,
		LocalDate endsOn,
		LocalDate graceEndsOn
) {
}
