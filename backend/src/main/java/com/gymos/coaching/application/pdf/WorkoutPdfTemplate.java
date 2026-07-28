package com.gymos.coaching.application.pdf;

/**
 * The member-facing workout document now has a single supported presentation:
 * the branded compact dark PDF. Legacy query values are intentionally ignored
 * so existing clients continue to work without exposing multiple templates.
 */
public enum WorkoutPdfTemplate {
	COMPACT_LIGHT,
	COMPACT_DARK,
	DETAILED;

	public static WorkoutPdfTemplate fromString(String value) {
		return COMPACT_DARK;
	}
}
