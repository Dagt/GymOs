package com.gymos.coaching.application.pdf;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Resolves the organization palette used by the tenant shell. The organization
 * is loaded first and remains the source of truth for PDF colors; branch data is
 * only allowed to override presentation metadata such as logo and display name.
 * This prevents default branch colors from replacing an explicitly configured
 * organization accent.
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
		if (current == null) {
			return Palette.fallback(legacyPrimaryColor, logoUrl, gymName);
		}
		try {
			Palette organization = current.findOrganization(gymName, logoUrl)
					.orElseGet(() -> Palette.fallback(legacyPrimaryColor, logoUrl, gymName));
			return current.applyBranchPresentationOverrides(organization, gymName, logoUrl, branchName);
		} catch (RuntimeException ignored) {
			// Branding must never prevent the workout document from being generated.
			return Palette.fallback(legacyPrimaryColor, logoUrl, gymName);
		}
	}

	private Optional<Palette> findOrganization(String gymName, String logoUrl) {
		String normalizedName = clean(gymName, "");
		String normalizedLogo = clean(logoUrl, "");
		if (normalizedName.isBlank() && normalizedLogo.isBlank()) return Optional.empty();

		return jdbc.sql("""
				SELECT
				    COALESCE(NULLIF(trim(primary_color), ''), '#111111') AS primary_color,
				    COALESCE(NULLIF(trim(accent_color), ''), NULLIF(trim(primary_color), ''), '#6B7280') AS accent_color,
				    COALESCE(NULLIF(trim(surface_color), ''), '#FFFFFF') AS surface_color,
				    NULLIF(trim(logo_url), '') AS logo_url,
				    COALESCE(NULLIF(trim(brand_name), ''), trade_name) AS display_name
				FROM organization
				WHERE (:logoUrl <> '' AND trim(COALESCE(logo_url, '')) = :logoUrl)
				   OR (:gymName <> '' AND lower(trim(COALESCE(NULLIF(brand_name, ''), trade_name))) = lower(:gymName))
				ORDER BY CASE WHEN :logoUrl <> '' AND trim(COALESCE(logo_url, '')) = :logoUrl THEN 0 ELSE 1 END
				LIMIT 1
				""")
				.param("gymName", normalizedName)
				.param("logoUrl", normalizedLogo)
				.query((rs, row) -> new Palette(
						clean(rs.getString("primary_color"), "#111111"),
						clean(rs.getString("accent_color"), "#6B7280"),
						clean(rs.getString("surface_color"), "#FFFFFF"),
						clean(rs.getString("logo_url"), logoUrl),
						clean(rs.getString("display_name"), gymName)
				))
				.optional();
	}

	private Palette applyBranchPresentationOverrides(
			Palette organization,
			String gymName,
			String logoUrl,
			String branchName
	) {
		if (branchName == null || branchName.isBlank()) return organization;
		try {
			return jdbc.sql("""
					SELECT b.logo_url, b.brand_name
					FROM branch b
					JOIN organization o ON o.id = b.organization_id
					WHERE lower(trim(b.name)) = lower(trim(:branchName))
					  AND (
					      (:logoUrl <> '' AND trim(COALESCE(o.logo_url, '')) = :logoUrl)
					      OR (:gymName <> '' AND lower(trim(COALESCE(NULLIF(o.brand_name, ''), o.trade_name))) = lower(:gymName))
					  )
					ORDER BY CASE WHEN :logoUrl <> '' AND trim(COALESCE(o.logo_url, '')) = :logoUrl THEN 0 ELSE 1 END
					LIMIT 1
					""")
					.param("branchName", branchName.trim())
					.param("gymName", clean(gymName, ""))
					.param("logoUrl", clean(logoUrl, ""))
					.query((rs, row) -> new Palette(
							organization.primaryColor(),
							organization.accentColor(),
							organization.surfaceColor(),
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
