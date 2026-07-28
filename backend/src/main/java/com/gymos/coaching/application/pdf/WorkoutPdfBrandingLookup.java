package com.gymos.coaching.application.pdf;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Resolves the same tenant palette persisted for the web application.
 *
 * The PDF assignment query historically exposed only organization.primary_color,
 * while the application theme also uses accent_color and surface_color. This
 * lookup keeps the PDF aligned with the tenant configuration without coupling
 * the renderer to a particular gym palette.
 */
@Component
public final class WorkoutPdfBrandingLookup {

	private static volatile WorkoutPdfBrandingLookup instance;
	private final JdbcClient jdbc;

	public WorkoutPdfBrandingLookup(JdbcClient jdbc) {
		this.jdbc = jdbc;
		instance = this;
	}

	public static Palette resolve(String gymName, String logoUrl, String legacyPrimaryColor) {
		WorkoutPdfBrandingLookup current = instance;
		if (current == null || gymName == null || gymName.isBlank()) {
			return Palette.fallback(legacyPrimaryColor);
		}
		return current.find(gymName, logoUrl).orElseGet(() -> Palette.fallback(legacyPrimaryColor));
	}

	private Optional<Palette> find(String gymName, String logoUrl) {
		String normalizedLogo = logoUrl == null ? "" : logoUrl.trim();
		return jdbc.sql("""
				SELECT primary_color, accent_color, surface_color
				FROM organization
				WHERE lower(trim(coalesce(brand_name, trade_name))) = lower(trim(:gymName))
				  AND (:logoUrl = '' OR coalesce(logo_url, '') = :logoUrl)
				ORDER BY updated_at DESC
				LIMIT 1
				""")
				.param("gymName", gymName)
				.param("logoUrl", normalizedLogo)
				.query((rs, row) -> new Palette(
						clean(rs.getString("primary_color"), "#111111"),
						clean(rs.getString("accent_color"), clean(rs.getString("primary_color"), "#6B7280")),
						clean(rs.getString("surface_color"), "#FFFFFF")
				))
				.optional();
	}

	private static String clean(String value, String fallback) {
		return value == null || value.isBlank() ? fallback : value.trim();
	}

	public record Palette(String primaryColor, String accentColor, String surfaceColor) {
		static Palette fallback(String legacyPrimaryColor) {
			String legacy = clean(legacyPrimaryColor, "#6B7280");
			return new Palette("#111111", legacy, "#FFFFFF");
		}
	}
}
