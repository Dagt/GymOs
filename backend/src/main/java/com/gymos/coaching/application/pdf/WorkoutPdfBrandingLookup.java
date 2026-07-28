package com.gymos.coaching.application.pdf;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Resolves the same organization/branch palette used by the tenant web shell.
 * Branch branding has precedence and falls back field-by-field to organization branding.
 */
@Component
public final class WorkoutPdfBrandingLookup {

	private static volatile WorkoutPdfBrandingLookup instance;
	private final JdbcClient jdbc;

	public WorkoutPdfBrandingLookup(JdbcClient jdbc) {
		this.jdbc = jdbc;
		instance = this;
	}

	public static Palette resolve(
			String gymName,
			String branchName,
			String logoUrl,
			String legacyPrimaryColor
	) {
		WorkoutPdfBrandingLookup current = instance;
		if (current == null || gymName == null || gymName.isBlank()) {
			return Palette.fallback(legacyPrimaryColor);
		}
		return current.find(gymName, branchName)
				.orElseGet(() -> Palette.fallback(legacyPrimaryColor));
	}

	private Optional<Palette> find(String gymName, String branchName) {
		String normalizedBranch = branchName == null ? "" : branchName.trim();
		return jdbc.sql("""
				SELECT
				    COALESCE(NULLIF(trim(b.primary_color), ''), NULLIF(trim(o.primary_color), ''), '#111111') AS primary_color,
				    COALESCE(NULLIF(trim(b.accent_color), ''), NULLIF(trim(o.accent_color), ''), '#6B7280') AS accent_color,
				    COALESCE(NULLIF(trim(b.surface_color), ''), NULLIF(trim(o.surface_color), ''), '#FFFFFF') AS surface_color,
				    COALESCE(NULLIF(trim(b.logo_url), ''), NULLIF(trim(o.logo_url), '')) AS logo_url,
				    COALESCE(NULLIF(trim(b.brand_name), ''), NULLIF(trim(o.brand_name), ''), o.trade_name) AS display_name
				FROM organization o
				LEFT JOIN branch b
				  ON b.organization_id = o.id
				 AND b.deleted_at IS NULL
				 AND (:branchName = '' OR lower(trim(b.name)) = lower(trim(:branchName)))
				WHERE lower(trim(COALESCE(NULLIF(o.brand_name, ''), o.trade_name))) = lower(trim(:gymName))
				ORDER BY CASE WHEN b.id IS NULL THEN 1 ELSE 0 END, b.updated_at DESC NULLS LAST, o.updated_at DESC
				LIMIT 1
				""")
				.param("gymName", gymName)
				.param("branchName", normalizedBranch)
				.query((rs, row) -> new Palette(
						clean(rs.getString("primary_color"), "#111111"),
						clean(rs.getString("accent_color"), "#6B7280"),
						clean(rs.getString("surface_color"), "#FFFFFF"),
						clean(rs.getString("logo_url"), null),
						clean(rs.getString("display_name"), gymName)
				))
				.optional();
	}

	private static String clean(String value, String fallback) {
		return value == null || value.isBlank() ? fallback : value.trim();
	}

	public record Palette(
			String primaryColor,
			String accentColor,
			String surfaceColor,
			String logoUrl,
			String displayName
	) {
		static Palette fallback(String legacyPrimaryColor) {
			String legacy = clean(legacyPrimaryColor, "#111111");
			return new Palette(legacy, legacy, "#FFFFFF", null, null);
		}
	}
}
