package com.gymos.memberships.application;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;

final class MembershipBusinessDate {
	private static final ZoneId DEFAULT_BUSINESS_ZONE = ZoneId.of("America/Mexico_City");

	private MembershipBusinessDate() {
	}

	static LocalDate from(OffsetDateTime occurredAt) {
		if (occurredAt == null) {
			throw new IllegalArgumentException("occurredAt is required");
		}
		return occurredAt.atZoneSameInstant(DEFAULT_BUSINESS_ZONE).toLocalDate();
	}
}
