package com.gymos.coaching.application.pdf;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Resolves the immutable catalog metadata needed by workout PDFs. The lookup is
 * intentionally limited to equipment so the glossary remains useful without
 * repeating prescription or technical instructions already printed in the plan.
 */
@Component
public final class WorkoutPdfExerciseCatalogLookup {

	private static volatile WorkoutPdfExerciseCatalogLookup instance;
	private final JdbcClient jdbc;

	public WorkoutPdfExerciseCatalogLookup(JdbcClient jdbc) {
		this.jdbc = jdbc;
		instance = this;
	}

	public static Optional<String> equipment(String exerciseId) {
		if (exerciseId == null || exerciseId.isBlank() || instance == null) return Optional.empty();
		try {
			UUID id = UUID.fromString(exerciseId.trim());
			return instance.jdbc.sql("""
					SELECT NULLIF(trim(equipment), '') AS equipment
					FROM exercise
					WHERE id = :id
					LIMIT 1
					""")
					.param("id", id)
					.query(String.class)
					.optional()
					.filter(value -> value != null && !value.isBlank());
		} catch (RuntimeException ignored) {
			return Optional.empty();
		}
	}
}
