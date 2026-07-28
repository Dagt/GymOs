package com.gymos.coaching.application.pdf;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
		String sets = text(exerciseNode, "sets", "");
		String reps = text(exerciseNode, "reps", "");
		String load = text(exerciseNode, "load", "");
		String unit = firstNonBlank(
				text(exerciseNode, "unit", ""),
				text(exerciseNode, "repetitionUnit", ""),
				text(exerciseNode, "repsUnit", "")
		);
		String sides = text(exerciseNode, "sides", "");
		String notes = text(exerciseNode, "notes", "");
		String tempo = text(exerciseNode, "tempo", "");
		String duration = text(exerciseNode, "duration", "");
		String distance = text(exerciseNode, "distance", "");
		String groupKey = text(exerciseNode, "groupKey", "");
		Integer restSeconds = exerciseNode.has("restSeconds") && exerciseNode.get("restSeconds").isNumber()
				? exerciseNode.get("restSeconds").asInt() : null;

		JsonNode setPlan = exerciseNode.get("setPlan");
		String mainPrescription;
		if (setPlan != null && setPlan.isArray() && !setPlan.isEmpty()) {
			mainPrescription = formatSetPlan(setPlan, unit);
		} else if (!freePrescription.isBlank()) {
			mainPrescription = freePrescription.trim();
			if (!load.isBlank() && !containsIgnoreCase(mainPrescription, load)) {
				mainPrescription += " · " + load.trim();
			}
		} else {
			mainPrescription = formatStructured(sets, reps, load, unit);
		}

		if (mainPrescription.isBlank() && optional) {
			mainPrescription = "Opcional";
		}

		List<String> details = new ArrayList<>();
		if (!sides.isBlank()) details.add("Lados: " + sides.trim());
		if (restSeconds != null && restSeconds > 0) details.add("Descanso: " + restSeconds + " s");
		if (!tempo.isBlank()) details.add("Tempo: " + tempo.trim());
		if (!duration.isBlank()) details.add("Duración: " + duration.trim());
		if (!distance.isBlank()) details.add("Distancia: " + distance.trim());
		if (!groupKey.isBlank()) details.add("Agrupación: " + groupKey.trim());
		if (optional && !"Opcional".equalsIgnoreCase(mainPrescription)) details.add("Opcional");

		return new FormattedPrescription(
				mainPrescription,
				String.join("  |  ", details),
				notes.trim(),
				optional
		);
	}

	public static String formatStructured(String sets, String reps, String load) {
		return formatStructured(sets, reps, load, "");
	}

	public static String formatStructured(String sets, String reps, String load, String unit) {
		String cleanSets = clean(sets);
		String cleanReps = normalizeReps(reps);
		String cleanLoad = clean(load);
		String cleanUnit = normalizeUnit(unit);

		StringBuilder result = new StringBuilder();
		if (!cleanSets.isBlank()) result.append(cleanSets);
		if (!cleanReps.isBlank()) {
			if (!result.isEmpty()) result.append(" × ");
			result.append(cleanReps);
			if (!cleanUnit.isBlank() && !containsIgnoreCase(cleanReps, cleanUnit)) {
				result.append(' ').append(cleanUnit);
			}
		}
		if (!cleanLoad.isBlank()) {
			if (!result.isEmpty()) result.append(" · ");
			result.append(cleanLoad);
		}
		return result.toString().trim();
	}

	private static String formatSetPlan(JsonNode setPlan, String unit) {
		List<String> summaries = new ArrayList<>();
		for (JsonNode set : setPlan) {
			JsonNode segments = set.get("segments");
			if (segments == null || !segments.isArray() || segments.isEmpty()) continue;
			List<String> segmentParts = new ArrayList<>();
			for (JsonNode segment : segments) {
				String reps = text(segment, "reps", "").trim();
				String load = text(segment, "load", "").trim();
				String segmentUnit = normalizeUnit(firstNonBlank(text(segment, "unit", ""), unit));
				String value = reps;
				if (!value.isBlank() && !segmentUnit.isBlank() && !containsIgnoreCase(value, segmentUnit)) {
					value += " " + segmentUnit;
				}
				if (!load.isBlank()) value = value.isBlank() ? load : value + " @ " + load;
				if (!value.isBlank()) segmentParts.add(value);
			}
			if (!segmentParts.isEmpty()) summaries.add(String.join(" + ", segmentParts));
		}

		if (summaries.isEmpty()) return setPlan.size() + " series personalizadas";
		boolean allEqual = summaries.stream().allMatch(summaries.get(0)::equals);
		if (allEqual) {
			String body = summaries.get(0);
			return summaries.size() + " × " + (body.contains(" + ") ? "(" + body + ")" : body);
		}

		boolean singleSegment = summaries.stream().noneMatch(value -> value.contains(" + "));
		if (singleSegment) {
			List<String> reps = new ArrayList<>();
			List<String> loads = new ArrayList<>();
			boolean compatible = true;
			for (String summary : summaries) {
				String[] parts = summary.split(" @ ", 2);
				reps.add(parts[0]);
				loads.add(parts.length == 2 ? parts[1] : "");
				if (parts.length != 2) compatible = false;
			}
			String repText = String.join(" / ", reps);
			if (compatible) return repText + " · " + String.join(" / ", loads);
			return repText;
		}

		List<String> numbered = new ArrayList<>();
		for (int i = 0; i < summaries.size(); i++) numbered.add("S" + (i + 1) + ": " + summaries.get(i));
		return String.join(" | ", numbered);
	}

	private static String normalizeReps(String reps) {
		String clean = clean(reps);
		if (clean.contains(",")) {
			return java.util.Arrays.stream(clean.split(","))
					.map(String::trim)
					.filter(value -> !value.isEmpty())
					.collect(Collectors.joining(" / "));
		}
		return clean;
	}

	private static String normalizeUnit(String unit) {
		String value = clean(unit).toLowerCase(Locale.forLanguageTag("es-MX"));
		return switch (value) {
			case "step", "steps", "paso", "pasos" -> "pasos";
			case "rep", "reps", "repeticion", "repetición", "repeticiones" -> "";
			case "segundo", "segundos", "sec", "secs", "s" -> "s";
			case "minuto", "minutos", "min", "mins" -> "min";
			case "metro", "metros", "m" -> "m";
			case "kilometro", "kilómetro", "kilometros", "kilómetros", "km" -> "km";
			default -> clean(unit);
		};
	}

	private static boolean containsIgnoreCase(String value, String candidate) {
		return value != null && candidate != null
				&& value.toLowerCase(Locale.ROOT).contains(candidate.toLowerCase(Locale.ROOT));
	}

	private static String firstNonBlank(String... values) {
		for (String value : values) if (value != null && !value.isBlank()) return value;
		return "";
	}

	private static String clean(String value) {
		return value == null ? "" : value.trim();
	}

	private static String text(JsonNode node, String field, String fallback) {
		if (node == null || !node.has(field) || node.get(field).isNull()) return fallback;
		return node.get(field).asText(fallback);
	}
}
