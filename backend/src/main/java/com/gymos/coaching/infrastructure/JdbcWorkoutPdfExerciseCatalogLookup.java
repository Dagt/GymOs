package com.gymos.coaching.infrastructure;

import com.gymos.coaching.application.pdf.WorkoutPdfExerciseCatalogLookup;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcWorkoutPdfExerciseCatalogLookup implements WorkoutPdfExerciseCatalogLookup {
	private final JdbcClient jdbcClient;

	public JdbcWorkoutPdfExerciseCatalogLookup(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	@Override
	public Optional<String> equipment(UUID organizationId, String exerciseId, String exerciseName) {
		try {
			Optional<String> byId = findById(organizationId, exerciseId);
			if (byId.isPresent()) return byId;
			Optional<String> byName = findByExactName(organizationId, exerciseName);
			if (byName.isPresent()) return byName;
		} catch (RuntimeException ignored) {
			// Optional catalog enrichment must not prevent PDF generation.
		}
		return WorkoutPdfExerciseCatalogLookup.inferFromName(exerciseName);
	}

	private Optional<String> findById(UUID organizationId, String exerciseId) {
		if (exerciseId == null || exerciseId.isBlank()) return Optional.empty();
		try {
			UUID id = UUID.fromString(exerciseId.trim());
			return jdbcClient.sql("""
					SELECT NULLIF(trim(equipment), '') AS equipment
					FROM exercise
					WHERE id = :id
					  AND (organization_id IS NULL OR organization_id = :organizationId)
					  AND status = 'ACTIVE'
					LIMIT 1
					""")
					.param("id", id)
					.param("organizationId", organizationId)
					.query(String.class)
					.optional()
					.filter(this::notBlank);
		} catch (IllegalArgumentException ignored) {
			return Optional.empty();
		}
	}

	private Optional<String> findByExactName(UUID organizationId, String exerciseName) {
		if (!notBlank(exerciseName)) return Optional.empty();
		return jdbcClient.sql("""
				SELECT NULLIF(trim(equipment), '') AS equipment
				FROM exercise
				WHERE lower(trim(name)) = lower(trim(:exerciseName))
				  AND (organization_id IS NULL OR organization_id = :organizationId)
				  AND status = 'ACTIVE'
				ORDER BY CASE WHEN organization_id = :organizationId THEN 0 ELSE 1 END
				LIMIT 1
				""")
				.param("exerciseName", exerciseName.trim())
				.param("organizationId", organizationId)
				.query(String.class)
				.optional()
				.filter(this::notBlank);
	}

	private boolean notBlank(String value) {
		return value != null && !value.isBlank();
	}
}
