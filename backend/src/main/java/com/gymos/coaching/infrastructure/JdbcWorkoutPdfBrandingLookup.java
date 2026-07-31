package com.gymos.coaching.infrastructure;

import com.gymos.coaching.application.pdf.WorkoutPdfBrandingLookup;
import com.gymos.coaching.application.pdf.WorkoutPdfBrandingLookup.Palette;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcWorkoutPdfBrandingLookup implements WorkoutPdfBrandingLookup {
	private final JdbcClient jdbcClient;

	public JdbcWorkoutPdfBrandingLookup(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	@Override
	public Palette resolve(
			UUID organizationId,
			UUID branchId,
			String gymName,
			String branchName,
			String logoUrl,
			String legacyPrimaryColor
	) {
		try {
			return findOrganization(organizationId, gymName, logoUrl)
					.orElseGet(() -> Palette.fallback(legacyPrimaryColor, logoUrl, gymName));
		} catch (RuntimeException ignored) {
			// Branding is optional and must never prevent document generation.
			return Palette.fallback(legacyPrimaryColor, logoUrl, gymName);
		}
	}

	private Optional<Palette> findOrganization(UUID organizationId, String gymName, String logoUrl) {
		return jdbcClient.sql("""
				SELECT
				    COALESCE(NULLIF(trim(primary_color), ''), '#111111') AS primary_color,
				    COALESCE(NULLIF(trim(accent_color), ''), NULLIF(trim(primary_color), ''), '#6B7280') AS accent_color,
				    COALESCE(NULLIF(trim(surface_color), ''), '#FFFFFF') AS surface_color,
				    NULLIF(trim(logo_url), '') AS logo_url,
				    COALESCE(NULLIF(trim(brand_name), ''), trade_name) AS display_name
				FROM organization
				WHERE id = :organizationId
				""")
				.param("organizationId", organizationId)
				.query((resultSet, rowNumber) -> new Palette(
						clean(resultSet.getString("primary_color"), "#111111"),
						clean(resultSet.getString("accent_color"), "#6B7280"),
						clean(resultSet.getString("surface_color"), "#FFFFFF"),
						clean(resultSet.getString("logo_url"), logoUrl),
						clean(resultSet.getString("display_name"), gymName)
				))
				.optional();
	}

	private String clean(String value, String fallback) {
		return value == null || value.isBlank() ? fallback : value.trim();
	}
}
