package com.gymos.common.time;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;

public final class BusinessTime {
	public static final ZoneId DEFAULT_ZONE = ZoneId.of("America/Mexico_City");

	private BusinessTime() {
	}

	public static LocalDate localDate(OffsetDateTime occurredAt) {
		if (occurredAt == null) {
			throw new IllegalArgumentException("occurredAt is required");
		}
		return occurredAt.atZoneSameInstant(DEFAULT_ZONE).toLocalDate();
	}

	public static LocalDate today() {
		return LocalDate.now(DEFAULT_ZONE);
	}
}
