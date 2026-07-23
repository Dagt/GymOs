package com.gymos.memberships.application;

import com.gymos.common.time.BusinessTime;
import java.time.LocalDate;
import java.time.OffsetDateTime;

final class MembershipBusinessDate {
	private MembershipBusinessDate() {
	}

	static LocalDate from(OffsetDateTime occurredAt) {
		return BusinessTime.localDate(occurredAt);
	}
}
