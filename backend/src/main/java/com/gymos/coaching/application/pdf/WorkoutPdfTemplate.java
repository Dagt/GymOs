package com.gymos.coaching.application.pdf;

import java.util.Locale;

public enum WorkoutPdfTemplate {
	COMPACT_LIGHT,
	COMPACT_DARK,
	DETAILED;

	public static WorkoutPdfTemplate fromString(String value) {
		if (value == null || value.isBlank()) {
			return COMPACT_LIGHT;
		}
		try {
			return WorkoutPdfTemplate.valueOf(value.trim().toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			return COMPACT_LIGHT;
		}
	}
}
