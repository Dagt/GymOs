package com.gymos.coaching.application.pdf;

import java.util.UUID;

/**
 * Application port for tenant branding used by workout PDFs.
 */
public interface WorkoutPdfBrandingLookup {
	Palette resolve(
			UUID organizationId,
			UUID branchId,
			String gymName,
			String branchName,
			String logoUrl,
			String legacyPrimaryColor
	);

	record Palette(
			String primaryColor,
			String accentColor,
			String surfaceColor,
			String logoUrl,
			String displayName
	) {
		public static Palette fallback(String legacyPrimaryColor, String logoUrl, String gymName) {
			String legacy = clean(legacyPrimaryColor, "#111111");
			return new Palette(
					legacy, legacy, "#FFFFFF",
					clean(logoUrl, null), clean(gymName, null));
		}

		private static String clean(String value, String fallback) {
			return value == null || value.isBlank() ? fallback : value.trim();
		}
	}
}
