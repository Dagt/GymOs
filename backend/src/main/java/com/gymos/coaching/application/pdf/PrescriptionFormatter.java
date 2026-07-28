package com.gymos.coaching.application.pdf;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public final class PrescriptionFormatter {

	private PrescriptionFormatter() {}

	public record FormattedPrescription(
			String mainPrescription,
			String detailsText,
			String notesText,
			boolean isOptional
	) {}

	public static FormattedPrescription format(JsonNode exerciseNode) {
		if (exerciseNode == null || exerciseNode.isNull()) {
			return new FormattedPrescription("", "", "", false);
		}

		boolean optional = exerciseNode.path("optional").asBoolean(false);
		String freePrescription = text(exerciseNode, "prescription", "");
		String setsStr = text(exerciseNode, "sets", "");
		String repsStr = text(exerciseNode, "reps", "");
		String loadStr = text(exerciseNode, "load", "");
		String notesStr = text(exerciseNode, "notes", "");
		String tempoStr = text(exerciseNode, "tempo", "");
		String durationStr = text(exerciseNode, "duration", "");
		String distanceStr = text(exerciseNode, "distance", "");
		String groupKeyStr = text(exerciseNode, "groupKey", "");
		Integer restSeconds = exerciseNode.has("restSeconds") && exerciseNode.get("restSeconds").isNumber()
				? exerciseNode.get("restSeconds").asInt() : null;

		JsonNode setPlan = exerciseNode.get("setPlan");

		String mainPrescription;

		// 1. If setPlan is present with detailed sets/segments
		if (setPlan != null && setPlan.isArray() && !setPlan.isEmpty()) {
			mainPrescription = formatSetPlan(setPlan);
		}
		// 2. If free-text prescription is provided by coach
		else if (!freePrescription.isBlank()) {
			mainPrescription = freePrescription.trim();
			if (!loadStr.isBlank() && !mainPrescription.toLowerCase().contains(loadStr.toLowerCase())) {
				mainPrescription += " · " + loadStr.trim();
			}
		}
		// 3. Structured sets, reps, load
		else {
			mainPrescription = formatStructured(setsStr, repsStr, loadStr);
		}

		// Details string (Tempo, Rest, Duration, Distance, Group, Optional)
		List<String> details = new ArrayList<>();
		if (restSeconds != null && restSeconds > 0) {
			details.add("Descanso: " + restSeconds + " s");
		}
		if (!tempoStr.isBlank()) {
			details.add("Tempo: " + tempoStr.trim());
		}
		if (!durationStr.isBlank()) {
			details.add("Duración: " + durationStr.trim());
		}
		if (!distanceStr.isBlank()) {
			details.add("Distancia: " + distanceStr.trim());
		}
		if (!groupKeyStr.isBlank()) {
			details.add("Agrupación: " + groupKeyStr.trim());
		}
		if (optional) {
			details.add("Opcional");
		}

		String detailsText = String.join("  |  ", details);

		return new FormattedPrescription(mainPrescription, detailsText, notesStr.trim(), optional);
	}

	public static String formatStructured(String setsStr, String repsStr, String loadStr) {
		StringBuilder sb = new StringBuilder();

		String cleanSets = setsStr == null ? "" : setsStr.trim();
		String cleanReps = repsStr == null ? "" : normalizeReps(repsStr);
		String cleanLoad = loadStr == null ? "" : loadStr.trim();

		if (!cleanSets.isBlank()) {
			sb.append(cleanSets);
		}

		if (!cleanReps.isBlank()) {
			if (!sb.isEmpty()) {
				sb.append(" \u00d7 "); // Unicode multiplication sign '×'
			}
			sb.append(cleanReps);
		}

		if (!cleanLoad.isBlank()) {
			if (!sb.isEmpty()) {
				sb.append(" \u00b7 "); // Unicode middle dot '·'
			}
			sb.append(cleanLoad);
		}

		return sb.toString().trim();
	}

	private static String normalizeReps(String reps) {
		if (reps == null) return "";
		String clean = reps.trim();
		// If reps are comma separated like "12, 10, 8", format as "12 / 10 / 8"
		if (clean.contains(",")) {
			String[] parts = clean.split(",");
			return java.util.Arrays.stream(parts)
					.map(String::trim)
					.filter(s -> !s.isEmpty())
					.collect(Collectors.joining(" / "));
		}
		return clean;
	}

	private static String formatSetPlan(JsonNode setPlan) {
		int setIndex = 1;
		List<String> setSummaries = new ArrayList<>();
		for (JsonNode set : setPlan) {
			JsonNode segments = set.get("segments");
			if (segments != null && segments.isArray() && !segments.isEmpty()) {
				List<String> segParts = new ArrayList<>();
				for (JsonNode seg : segments) {
					String r = text(seg, "reps", "");
					String l = text(seg, "load", "");
					if (!r.isBlank() && !l.isBlank()) {
						segParts.add(r + " @ " + l);
					} else if (!r.isBlank()) {
						segParts.add(r);
					} else if (!l.isBlank()) {
						segParts.add(l);
					}
				}
				if (!segParts.isEmpty()) {
					setSummaries.add("S" + setIndex + ": " + String.join(" + ", segParts));
				}
			}
			setIndex++;
		}
		if (setSummaries.isEmpty()) {
			return setPlan.size() + " series personalizadas";
		}
		if (setSummaries.size() <= 4) {
			return String.join(" | ", setSummaries);
		}
		return setPlan.size() + " series (" + String.join(" | ", setSummaries.subList(0, 3)) + "... )";
	}

	private static String text(JsonNode node, String field, String fallback) {
		if (node == null || !node.has(field) || node.get(field).isNull()) {
			return fallback;
		}
		return node.get(field).asText(fallback);
	}
}
