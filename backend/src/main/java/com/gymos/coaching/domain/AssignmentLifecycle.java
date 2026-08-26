package com.gymos.coaching.domain;

import java.time.LocalDate;

/** End dates are inclusive. Calendar completion does not prove exercise adherence. */
public final class AssignmentLifecycle {
	public static final String PERIOD_ENDED = "PERIOD_ENDED";
	private AssignmentLifecycle() {}

	public static String effectiveStatus(String status, LocalDate startsOn, LocalDate endsOn, LocalDate today) {
		if (!"ACTIVE".equals(status)) return status;
		if (endsOn != null && endsOn.isBefore(today)) return "COMPLETED";
		if (startsOn != null && startsOn.isAfter(today)) return "SCHEDULED";
		return "ACTIVE";
	}
}
