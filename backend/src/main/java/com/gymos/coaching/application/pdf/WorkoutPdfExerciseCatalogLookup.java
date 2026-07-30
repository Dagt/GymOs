package com.gymos.coaching.application.pdf;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Application port for resolving exercise catalog metadata required by PDFs.
 */
public interface WorkoutPdfExerciseCatalogLookup {
	Optional<String> equipment(UUID organizationId, String exerciseId, String exerciseName);

	/**
	 * Conservative fallback for legacy exercises without catalog equipment.
	 * It only translates explicit equipment words and makes no biomechanical
	 * assumptions.
	 */
	static Optional<String> inferFromName(String exerciseName) {
		if (exerciseName == null || exerciseName.isBlank()) return Optional.empty();
		String normalized = Normalizer.normalize(exerciseName, Normalizer.Form.NFD)
				.replaceAll("\\p{M}+", "")
				.toLowerCase(Locale.ROOT)
				.trim();

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
}
