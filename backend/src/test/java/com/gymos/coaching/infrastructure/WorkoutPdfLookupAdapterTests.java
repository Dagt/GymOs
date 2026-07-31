package com.gymos.coaching.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class WorkoutPdfLookupAdapterTests {
	private final UUID organizationId = UUID.randomUUID();
	private final UUID branchId = UUID.randomUUID();
	private final JdbcClient jdbcClient = JdbcClient.create(new DriverManagerDataSource(
			"jdbc:h2:mem:pdf-lookups;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", ""));

	@BeforeEach
	void seedLookupTables() {
		jdbcClient.sql("DROP ALL OBJECTS").update();
		jdbcClient.sql("""
				CREATE TABLE organization (
				    id UUID PRIMARY KEY,
				    trade_name VARCHAR(255),
				    brand_name VARCHAR(255),
				    logo_url VARCHAR(512),
				    primary_color VARCHAR(32),
				    accent_color VARCHAR(32),
				    surface_color VARCHAR(32)
				)
				""").update();
		jdbcClient.sql("""
				CREATE TABLE branch (
				    id UUID PRIMARY KEY,
				    organization_id UUID NOT NULL,
				    deleted_at TIMESTAMP WITH TIME ZONE
				)
				""").update();
		jdbcClient.sql("""
				CREATE TABLE exercise (
				    id UUID PRIMARY KEY,
				    organization_id UUID,
				    name VARCHAR(255) NOT NULL,
				    equipment VARCHAR(255),
				    status VARCHAR(32) NOT NULL
				)
				""").update();
	}

	@Test
	void lookupsUseExplicitTenantIdWithoutDependingOnRetiredBranchBranding() {
		UUID otherOrganizationId = UUID.randomUUID();
		insertOrganization(organizationId, "Gym Correcto", "#112233", "#445566", "#FFFFFF");
		insertOrganization(otherOrganizationId, "Otro Gym", "#000000", "#000000", "#000000");
		jdbcClient.sql("""
				INSERT INTO branch (id, organization_id, deleted_at)
				VALUES (:id, :organizationId, NULL)
				""")
				.param("id", branchId)
				.param("organizationId", organizationId)
				.update();
		insertExercise(otherOrganizationId, "Remo sentado", "Equipo de otro tenant");
		insertExercise(organizationId, "Remo sentado", "Polea Gym Correcto");

		var equipment = new JdbcWorkoutPdfExerciseCatalogLookup(jdbcClient)
				.equipment(organizationId, null, "Remo sentado");
		var branding = new JdbcWorkoutPdfBrandingLookup(jdbcClient).resolve(
				organizationId, branchId, "Nombre heredado", "Centro",
				"/legacy.png", "#999999");

		assertThat(equipment).contains("Polea Gym Correcto");
		assertThat(branding.primaryColor()).isEqualTo("#112233");
		assertThat(branding.accentColor()).isEqualTo("#445566");
		assertThat(branding.logoUrl()).isEqualTo("/org-logo.png");
		assertThat(branding.displayName()).isEqualTo("Gym Correcto");
	}

	@Test
	void exerciseLookupFallsBackWhenOptionalCatalogQueryFails() {
		var lookup = new JdbcWorkoutPdfExerciseCatalogLookup(missingSchemaJdbcClient());

		assertThat(lookup.equipment(organizationId, null, "Laterales en polea"))
				.contains("Polea / estación de cables");
	}

	@Test
	void brandingLookupFallsBackWhenOptionalTenantQueryFails() {
		var lookup = new JdbcWorkoutPdfBrandingLookup(missingSchemaJdbcClient());

		assertThat(lookup.resolve(
				organizationId, branchId, "Gym Demo", "Centro", "/logo.png", "#123456"))
				.satisfies(palette -> {
					assertThat(palette.primaryColor()).isEqualTo("#123456");
					assertThat(palette.accentColor()).isEqualTo("#123456");
					assertThat(palette.logoUrl()).isEqualTo("/logo.png");
					assertThat(palette.displayName()).isEqualTo("Gym Demo");
				});
	}

	private void insertOrganization(
			UUID id, String name, String primary, String accent, String surface
	) {
		jdbcClient.sql("""
				INSERT INTO organization (
				    id, trade_name, brand_name, logo_url,
				    primary_color, accent_color, surface_color
				)
				VALUES (:id, :name, :name, '/org-logo.png', :primary, :accent, :surface)
				""")
				.param("id", id)
				.param("name", name)
				.param("primary", primary)
				.param("accent", accent)
				.param("surface", surface)
				.update();
	}

	private void insertExercise(UUID tenantId, String name, String equipment) {
		jdbcClient.sql("""
				INSERT INTO exercise (id, organization_id, name, equipment, status)
				VALUES (:id, :organizationId, :name, :equipment, 'ACTIVE')
				""")
				.param("id", UUID.randomUUID())
				.param("organizationId", tenantId)
				.param("name", name)
				.param("equipment", equipment)
				.update();
	}

	private JdbcClient missingSchemaJdbcClient() {
		return JdbcClient.create(new DriverManagerDataSource(
				"jdbc:h2:mem:pdf-lookups-missing-" + UUID.randomUUID()
						+ ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
				"sa", ""));
	}
}
