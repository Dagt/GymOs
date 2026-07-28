package com.gymos.coaching.application.pdf;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Resolves the same organization/branch palette used by the tenant web shell.
 * Organization branding is loaded first through a minimal, stable query. Branch
 * branding is then applied field-by-field when the active branch defines overrides.
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
			return Palette.fallback(legacyPrimaryColor, logoUrl, gymName);
		}
		try {
			Palette organization = current.findOrganization(gymName)
					.orElseGet(() -> Palette.fallback(legacyPrimaryColor, logoUrl, gymName));
			return current.applyBranchOverrides(organization, gymName, branchName);
		} catch (RuntimeException ignored) {
			// Branding must never prevent the workout document from being generated.
			return Palette.fallback(legacyPrimaryColor, logoUrl, gymName);
		}
	}

	private Optional<Palette> findOrganization(String gymName) {
		return jdbc.sql("""
				SELECT
				    COALESCE(NULLIF(trim(primary_color), ''), '#111111') AS primary_color,
				    COALESCE(NULLIF(trim(accent_color), ''), NULLIF(trim(primary_color), ''), '#6B7280') AS accent_color,
				    COALESCE(NULLIF(trim(surface_color), ''), '#FFFFFF') AS surface_color,
				    NULLIF(trim(logo_url), '') AS logo_url,
				    COALESCE(NULLIF(trim(brand_name), ''), trade_name) AS display_name
				FROM organization
				WHERE lower(trim(COALESCE(NULLIF(brand_name, ''), trade_name))) = lower(trim(:gymName))
				LIMIT 1
				""")
				.param("gymName", gymName)
				.query((rs, row) -> new Palette(
						clean(rs.getString("primary_color"), "#111111"),
						clean(rs.getString("accent_color"), "#6B7280"),
						clean(rs.getString("surface_color"), "#FFFFFF"),
						clean(rs.getString("logo_url"), null),
						clean(rs.getString("display_name"), gymName)
				))
				.optional();
	}

	private Palette applyBranchOverrides(Palette organization, String gymName, String branchName) {
		if (branchName == null || branchName.isBlank()) return organization;
		try {
			return jdbc.sql("""
					SELECT primary_color, accent_color, surface_color, logo_url, brand_name
					FROM branch
					WHERE lower(trim(name)) = lower(trim(:branchName))
					  AND organization_id = (
					      SELECT id FROM organization
					      WHERE lower(trim(COALESCE(NULLIF(brand_name, ''), trade_name))) = lower(trim(:gymName))
					      LIMIT 1
					  )
					LIMIT 1
					""")
					.param("branchName", branchName)
					.param("gymName", gymName)
					.query((rs, row) -> new Palette(
							clean(rs.getString("primary_color"), organization.primaryColor()),
							clean(rs.getString("accent_color"), organization.accentColor()),
							clean(rs.getString("surface_color"), organization.surfaceColor()),
							clean(rs.getString("logo_url"), organization.logoUrl()),
							clean(rs.getString("brand_name"), organization.displayName())
					))
					.optional()
					.orElse(organization);
		} catch (RuntimeException ignored) {
			return organization;
		}
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
		static Palette fallback(String legacyPrimaryColor, String logoUrl, String gymName) {
			String legacy = clean(legacyPrimaryColor, "#111111");
			return new Palette(legacy, legacy, "#FFFFFF", clean(logoUrl, null), clean(gymName, null));
		}
	}
}
