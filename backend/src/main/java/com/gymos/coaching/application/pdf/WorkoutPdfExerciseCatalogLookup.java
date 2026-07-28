package com.gymos.coaching.application.pdf;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Resolves the catalog metadata needed by workout PDFs. The lookup is limited to
 * equipment so the final section remains useful without repeating prescription
 * or technical instructions already printed in the plan.
 */
@Component
public final class WorkoutPdfExerciseCatalogLookup {

	private static volatile WorkoutPdfExerciseCatalogLookup instance;
	private final JdbcClient jdbc;

	public WorkoutPdfExerciseCatalogLookup(JdbcClient jdbc) {
		this.jdbc = jdbc;
		instance = this;
	}

	public static Optional<String> equipment(String exerciseId, String exerciseName) {
		WorkoutPdfExerciseCatalogLookup current = instance;
		if (current == null) return Optional.empty();

		Optional<String> byId = current.findById(exerciseId);
		if (byId.isPresent()) return byId;
		return current.findByExactName(exerciseName);
	}

	private Optional<String> findById(String exerciseId) {
		if (exerciseId == null || exerciseId.isBlank()) return Optional.empty();
		try {
			UUID id = UUID.fromString(exerciseId.trim());
			return jdbc.sql("""
					SELECT NULLIF(trim(equipment), '') AS equipment
					FROM exercise
					WHERE id = :id
					LIMIT 1
					""")
					.param("id", id)
					.query(String.class)
					.optional()
					.filter(WorkoutPdfExerciseCatalogLookup::notBlank);
		} catch (RuntimeException ignored) {
			return Optional.empty();
		}
	}

	private Optional<String> findByExactName(String exerciseName) {
		if (exerciseName == null || exerciseName.isBlank()) return Optional.empty();
		try {
			return jdbc.sql("""
					SELECT NULLIF(trim(equipment), '') AS equipment
					FROM exercise
					WHERE lower(trim(name)) = lower(trim(:exerciseName))
					  AND status = 'ACTIVE'
					ORDER BY CASE WHEN organization_id IS NULL THEN 1 ELSE 0 END
					LIMIT 1
					""")
					.param("exerciseName", exerciseName.trim())
					.query(String.class)
					.optional()
					.filter(WorkoutPdfExerciseCatalogLookup::notBlank);
		} catch (RuntimeException ignored) {
			return Optional.empty();
		}
	}

	private static boolean notBlank(String value) {
		return value != null && !value.isBlank();
	}
}
