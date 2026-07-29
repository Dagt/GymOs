package com.gymos.coaching.application.pdf;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.Locale;
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
		if (current != null) {
			Optional<String> byId = current.findById(exerciseId);
			if (byId.isPresent()) return byId;
			Optional<String> byName = current.findByExactName(exerciseName);
			if (byName.isPresent()) return byName;
		}
		return inferFromName(exerciseName);
	}

	private Optional<String> findById(String exerciseId) {
		if (exerciseId == null || exerciseId.isBlank()) return Optional.empty();
		try {
			UUID id = UUID.fromString(exerciseId.trim());
			return jdbc.sql("""
					SELECT NULLIF(trim(equipment), '') AS equipment
					FROM exercise
					WHERE id = :id
					  AND status = 'ACTIVE'
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

	/**
	 * Conservative fallback for legacy/system exercises whose catalog row does
	 * not yet contain equipment. Only explicit equipment words in the exercise
	 * name are translated; no biomechanical assumptions are made.
	 */
	static Optional<String> inferFromName(String exerciseName) {
		if (!notBlank(exerciseName)) return Optional.empty();
		String normalized = normalize(exerciseName);

		if (normalized.contains("polea") || normalized.contains("cross over") || normalized.contains("crossover")) {
			return Optional.of("Polea / estación de cables");
		}
		if (normalized.contains("mancuerna")) return Optional.of("Mancuernas");
		if (normalized.contains("barra")) return Optional.of("Barra y discos");
		if (normalized.contains("disco")) return Optional.of("Disco de peso");
		if (normalized.contains("banco scott")) return Optional.of("Banco Scott y barra o mancuernas");
		if (normalized.contains("banco inclinado")) return Optional.of("Banco inclinado y mancuernas");
		if (normalized.contains("prensa articulada") || normalized.equals("prensa")) return Optional.of("Prensa de piernas");
		if (normalized.contains("extension de cuadriceps")) return Optional.of("Máquina de extensión de cuádriceps");
		if (normalized.contains("femoral tumbado")) return Optional.of("Máquina de curl femoral tumbado");
		if (normalized.contains("aductores en maquina")) return Optional.of("Máquina de aductores");
		if (normalized.contains("abductores en maquina")) return Optional.of("Máquina de abductores");
		if (normalized.contains("chest press")) return Optional.of("Máquina chest press");
		if (normalized.contains("sentadilla sissy")) return Optional.of("Máquina sissy squat o apoyo estable");
		if (normalized.contains("landmine") || normalized.contains("ladmine")) return Optional.of("Accesorio landmine y barra");
		if (normalized.contains("peso muerto")) return Optional.of("Barra y discos");
		if (normalized.contains("plancha con remo")) return Optional.of("Mancuernas");
		if (normalized.contains("press frances")) return Optional.of("Barra, mancuerna o polea");
		if (normalized.contains("curl 21")) return Optional.of("Barra o mancuernas");
		if (normalized.contains("pantorrilla") && normalized.contains("maquina")) return Optional.of("Máquina de pantorrilla");
		return Optional.empty();
	}

	private static String normalize(String value) {
		return Normalizer.normalize(value, Normalizer.Form.NFD)
				.replaceAll("\\p{M}+", "")
				.toLowerCase(Locale.ROOT)
				.trim();
	}

	private static boolean notBlank(String value) {
		return value != null && !value.isBlank();
	}
}
