package com.gymos.coaching.application.pdf;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymos.coaching.infrastructure.CoachingCatalogRepository.AssignmentPdfData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public final class WorkoutPdfDocumentMapper {
	private final ObjectMapper objectMapper;
	private final WorkoutPdfBrandingLookup brandingLookup;
	private final WorkoutPdfExerciseCatalogLookup exerciseCatalogLookup;

	public WorkoutPdfDocumentMapper(
			ObjectMapper objectMapper,
			WorkoutPdfBrandingLookup brandingLookup,
			WorkoutPdfExerciseCatalogLookup exerciseCatalogLookup
	) {
		this.objectMapper = objectMapper;
		this.brandingLookup = brandingLookup;
		this.exerciseCatalogLookup = exerciseCatalogLookup;
	}

	public WorkoutPdfDocument fromAssignmentData(
			UUID organizationId,
			UUID branchId,
			AssignmentPdfData data
	) {
		WorkoutPdfBrandingLookup.Palette palette = brandingLookup.resolve(
				organizationId, branchId,
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
					sessions.add(mapSession(organizationId, sessNode, dayIndex++));
				}
			}
		} catch (Exception ignored) {}

		appendEquipmentGlossary(sessions);

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

	private WorkoutPdfSession mapSession(UUID organizationId, JsonNode sessNode, int dayIndex) {
		String name = text(sessNode, "name", "Sesión " + dayIndex);
		String focus = text(sessNode, "focus", "");
		String notes = text(sessNode, "notes", "");

		List<WorkoutPdfBlock> blocks = new ArrayList<>();
		JsonNode blocksNode = sessNode.get("blocks");
		if (blocksNode != null && blocksNode.isArray()) {
			for (JsonNode blockNode : blocksNode) {
				if (blockNode.isObject() && blockNode.has("exercises")) {
					blocks.add(mapBlock(organizationId, blockNode));
				} else {
					WorkoutPdfExercise ex = mapExercise(organizationId, blockNode, 1);
					blocks.add(new WorkoutPdfBlock("Bloque Principal", "MAIN", "TRABAJO PRINCIPAL", "", List.of(ex)));
				}
			}
		}

		return new WorkoutPdfSession(dayIndex, name, focus, notes, blocks);
	}

	private WorkoutPdfBlock mapBlock(UUID organizationId, JsonNode blockNode) {
		String name = text(blockNode, "name", "");
		String type = text(blockNode, "type", "MAIN");
		String notes = text(blockNode, "notes", "");
		String typeLabel = blockTypeLabel(type);

		List<WorkoutPdfExercise> exercises = new ArrayList<>();
		JsonNode exercisesNode = blockNode.get("exercises");
		if (exercisesNode != null && exercisesNode.isArray()) {
			int exIndex = 1;
			for (JsonNode exNode : exercisesNode) {
				exercises.add(mapExercise(organizationId, exNode, exIndex++));
			}
		}

		return new WorkoutPdfBlock(name, type, typeLabel, notes, exercises);
	}

	private WorkoutPdfExercise mapExercise(UUID organizationId, JsonNode exNode, int index) {
		String name = text(exNode, "exerciseName", text(exNode, "exercise", text(exNode, "name", "Ejercicio")));
		var formatted = PrescriptionFormatter.format(exNode);
		String mediaUrl = text(exNode, "mediaUrl", null);
		String primaryMuscleGroup = text(exNode, "primaryMuscleGroup", null);
		String equipment = text(exNode, "equipment", null);
		if (equipment == null || equipment.isBlank()) {
			equipment = exerciseCatalogLookup.equipment(
					organizationId, text(exNode, "exerciseId", null), name
			).orElse(null);
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

	private static void appendEquipmentGlossary(List<WorkoutPdfSession> sessions) {
		if (sessions.isEmpty()) return;

		Map<String, EquipmentEntry> unique = new LinkedHashMap<>();
		for (WorkoutPdfSession session : sessions) {
			if (session.blocks() == null) continue;
			for (WorkoutPdfBlock block : session.blocks()) {
				if (block.exercises() == null) continue;
				for (WorkoutPdfExercise exercise : block.exercises()) {
					if (exercise.name() == null || exercise.name().isBlank()
							|| exercise.equipment() == null || exercise.equipment().isBlank()) continue;
					String key = exercise.name().trim().toLowerCase(Locale.forLanguageTag("es-MX"));
					unique.putIfAbsent(key, new EquipmentEntry(exercise.name().trim(), exercise.equipment().trim()));
				}
			}
		}
		if (unique.isEmpty()) return;

		List<WorkoutPdfExercise> entries = new ArrayList<>();
		int index = 1;
		for (EquipmentEntry entry : unique.values()) {
			entries.add(new WorkoutPdfExercise(
					index++, entry.exerciseName(), entry.equipment(), "", "", false,
					null, null, entry.equipment()
			));
		}

		WorkoutPdfSession last = sessions.get(sessions.size() - 1);
		List<WorkoutPdfBlock> blocks = new ArrayList<>(last.blocks() == null ? List.of() : last.blocks());
		blocks.add(new WorkoutPdfBlock(
				"Equipo necesario",
				"EQUIPMENT",
				"EQUIPO NECESARIO",
				"Cada ejercicio aparece una sola vez.",
				entries
		));
		sessions.set(sessions.size() - 1, new WorkoutPdfSession(
				last.dayIndex(), last.name(), last.focus(), last.notes(), blocks
		));
	}

	private record EquipmentEntry(String exerciseName, String equipment) {}

	private static String blockTypeLabel(String type) {
		if (type == null) return "BLOQUE";
		return switch (type.toUpperCase()) {
			case "WARMUP" -> "CALENTAMIENTO";
			case "MAIN" -> "TRABAJO PRINCIPAL";
			case "SUPERSET" -> "SUPERSERIE";
			case "CIRCUIT" -> "CIRCUITO";
			case "FINISHER" -> "FINALIZADOR";
			case "COOLDOWN" -> "VUELTA A LA CALMA";
			case "EQUIPMENT" -> "EQUIPO NECESARIO";
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
