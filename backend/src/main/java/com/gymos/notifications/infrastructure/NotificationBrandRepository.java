package com.gymos.notifications.infrastructure;

import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class NotificationBrandRepository {
	private final JdbcClient jdbcClient;

	public NotificationBrandRepository(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	public EmailBrand emailBrand(UUID organizationId) {
		return jdbcClient.sql("""
				SELECT COALESCE(NULLIF(brand_name, ''), trade_name) AS brand_name,
				       logo_url, primary_color, accent_color, surface_color
				FROM organization
				WHERE id = :organizationId
				  AND deleted_at IS NULL
				""")
				.param("organizationId", organizationId)
				.query((rs, row) -> new EmailBrand(
						rs.getString("brand_name"),
						rs.getString("logo_url"),
						rs.getString("primary_color"),
						rs.getString("accent_color"),
						rs.getString("surface_color")
				))
				.optional()
				.orElse(new EmailBrand("Tu gimnasio", null, null, null, null));
	}

	public record EmailBrand(
			String brandName,
			String logoUrl,
			String primaryColor,
			String accentColor,
			String surfaceColor
	) {
	}
}
