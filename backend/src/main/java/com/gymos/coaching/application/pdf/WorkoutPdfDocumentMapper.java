package com.gymos.coaching.application.pdf;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymos.coaching.infrastructure.CoachingCatalogRepository.AssignmentPdfData;

import java.util.ArrayList;
import java.util.List;

public final class WorkoutPdfDocumentMapper {

	private static final ObjectMapper objectMapper = new ObjectMapper();

	private WorkoutPdfDocumentMapper() {}

	public static WorkoutPdfDocument fromAssignmentData(AssignmentPdfData data) {
		WorkoutPdfBrandingLookup.Palette palette = WorkoutPdfBrandingLookup.resolve(
				data.gymName(), data.branchName(), data.logoUrl(), data.primaryColor()
		);
		String resolvedLogo = palette.logoUrl() != null && !palette.logoUrl().isBlank()
				? palette.logoUrl() : data.logoUrl();
		String resolvedName = palette.displayName() != null && !palette.displayName().isBlank()
				? palette.displayName() : data.gymName();
		WorkoutPdfBranding branding = new WorkoutPdfBranding(
				resolvedName,
				data.branchName(),
				data.contactPhone(),
				resolvedLogo,
				palette.accentColor(),
				palette.primaryColor()
		);

		List<WorkoutPdfSession> sessions = new ArrayList<>();
		String summary = data.summary() != null && !data.summary().isBlank() ? data.summary() : null;

		try {
			JsonNode root = objectMapper.readTree(data.contentJson());
			if (summary == null) {
				summary = text(root, "summary", null);
			}

			JsonNode sessionsNode = root.has("sessions") ? root.get("sessions") : root.get("days");
			if (sessionsNode != null && sessionsNode.isArray()) {
				int dayIndex = 1;
				for (JsonNode sessNode : sessionsNode) {
					sessions.add(mapSession(sessNode, dayIndex++));
				}
			}
		} catch (Exception ignored) {}

		return new WorkoutPdfDocument(
				data.id() != null ? data.id().toString() : "N/A",
				data.title(),
				data.versionNumber(),
				data.clientName(),
				data.coachName(),
				data.goal(),
				data.level(),
				data.assignedOn(),
				data.startsOn(),
				data.endsOn(),
				summary,
				data.printableNotes(),
				branding,
				sessions
		);
	}

	private static WorkoutPdfSession mapSession(JsonNode sessNode, int dayIndex) {
		String name = text(sessNode, "name", "Sesión " + dayIndex);
		String focus = text(sessNode, "focus", "");
		String notes = text(sessNode, "notes", "");

		List<WorkoutPdfBlock> blocks = new ArrayList<>();
		JsonNode blocksNode = sessNode.get("blocks");
		if (blocksNode != null && blocksNode.isArray()) {
			for (JsonNode blockNode : blocksNode) {
				if (blockNode.isObject() && blockNode.has("exercises")) {
					blocks.add(mapBlock(blockNode));
				} else {
					WorkoutPdfExercise ex = mapExercise(blockNode, 1);
					blocks.add(new WorkoutPdfBlock("Bloque Principal", "MAIN", "TRABAJO PRINCIPAL", "", List.of(ex)));
				}
			}
		}

		return new WorkoutPdfSession(dayIndex, name, focus, notes, blocks);
	}

	private static WorkoutPdfBlock mapBlock(JsonNode blockNode) {
		String name = text(blockNode, "name", "");
		String type = text(blockNode, "type", "MAIN");
		String notes = text(blockNode, "notes", "");
		String typeLabel = blockTypeLabel(type);

		List<WorkoutPdfExercise> exercises = new ArrayList<>();
		JsonNode exercisesNode = blockNode.get("exercises");
		if (exercisesNode != null && exercisesNode.isArray()) {
			int exIndex = 1;
			for (JsonNode exNode : exercisesNode) {
				exercises.add(mapExercise(exNode, exIndex++));
			}
		}

		return new WorkoutPdfBlock(name, type, typeLabel, notes, exercises);
	}

	private static WorkoutPdfExercise mapExercise(JsonNode exNode, int index) {
		String name = text(exNode, "exerciseName", text(exNode, "exercise", text(exNode, "name", "Ejercicio")));
		var formatted = PrescriptionFormatter.format(exNode);
		String mediaUrl = text(exNode, "mediaUrl", null);
		String primaryMuscleGroup = text(exNode, "primaryMuscleGroup", null);
		String equipment = text(exNode, "equipment", null);
		if (equipment == null || equipment.isBlank()) {
			equipment = WorkoutPdfExerciseCatalogLookup.equipment(text(exNode, "exerciseId", null)).orElse(null);
		}

		return new WorkoutPdfExercise(
				index,
				name,
				formatted.mainPrescription(),
				formatted.detailsText(),
				formatted.notesText(),
				formatted.isOptional(),
				mediaUrl,
				primaryMuscleGroup,
				equipment
		);
	}

	private static String blockTypeLabel(String type) {
		if (type == null) return "BLOQUE";
		return switch (type.toUpperCase()) {
			case "WARMUP" -> "CALENTAMIENTO";
			case "MAIN" -> "TRABAJO PRINCIPAL";
			case "SUPERSET" -> "SUPERSERIE";
			case "CIRCUIT" -> "CIRCUITO";
			case "FINISHER" -> "FINALIZADOR";
			case "COOLDOWN" -> "VUELTA A LA CALMA";
			default -> "BLOQUE";
		};
	}

	private static String text(JsonNode node, String field, String fallback) {
		if (node == null || !node.has(field) || node.get(field).isNull()) {
			return fallback;
		}
		return node.get(field).asText(fallback);
	}
}
