package com.gymos.coaching.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymos.audit.api.AuditService;
import com.gymos.coaching.infrastructure.CoachingCatalogRepository;
import com.gymos.coaching.infrastructure.CoachingCatalogRepository.AssignmentDetail;
import com.gymos.coaching.infrastructure.CoachingCatalogRepository.AssignmentPdfData;
import com.gymos.coaching.infrastructure.CoachingCatalogRepository.CoachMetric;
import com.gymos.coaching.infrastructure.CoachingCatalogRepository.ExerciseData;
import com.gymos.coaching.infrastructure.CoachingCatalogRepository.ExercisePage;
import com.gymos.coaching.infrastructure.CoachingCatalogRepository.ExerciseRecord;
import com.gymos.common.api.BusinessException;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.common.time.BusinessTime;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.Normalizer;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CoachingCatalogService {
	private static final int MAX_PAGE_SIZE = 100;
	private final CoachingCatalogRepository repository;
	private final AuditService auditService;
	private final ObjectMapper objectMapper;

	public CoachingCatalogService(
			CoachingCatalogRepository repository, AuditService auditService, ObjectMapper objectMapper
	) {
		this.repository = repository;
		this.auditService = auditService;
		this.objectMapper = objectMapper;
	}

	@Transactional(readOnly = true)
	public ExercisePage exercises(
			GymOsPrincipal principal, String query, String muscle, String equipment, String type, int page, int size
	) {
		requireTenant(principal);
		int safePage = Math.max(0, page);
		int safeSize = Math.max(1, Math.min(MAX_PAGE_SIZE, size));
		return repository.exercises(principal.organizationId(), query, muscle, equipment, type, safePage, safeSize);
	}

	@Transactional
	public ExerciseRecord createExercise(
			GymOsPrincipal principal, CreateExerciseCommand command, String correlationId
	) {
		requireTenant(principal);
		String name = required(command.name(), "EXERCISE_NAME_REQUIRED", "El nombre del ejercicio es obligatorio.");
		String normalizedName = normalize(name);
		repository.activeByNormalizedName(principal.organizationId(), normalizedName).ifPresent(existing -> {
			throw new BusinessException("EXERCISE_DUPLICATE", "Ya existe un ejercicio con ese nombre.");
		});
		String type = normalizeEnum(command.type(), "STRENGTH");
		UUID id = repository.createExercise(
				principal.organizationId(),
				principal.userId(),
				new ExerciseData(
						name, normalizedName, blank(command.description()), blank(command.instructions()),
						blank(command.primaryMuscleGroup()), blank(command.secondaryMuscleGroups()),
						blank(command.movementPattern()), type, blank(command.equipment()),
						blank(command.suggestedLevel()), blank(command.laterality()),
						blank(command.mediaUrl()), blank(command.warnings()),
						principal.permissions().contains("exercise.manage") ? "ORGANIZATION" : "COACH"
				)
		);
		auditService.record(principal, "EXERCISE_CREATED", "EXERCISE", id, "BACKOFFICE", null, correlationId);
		return repository.exercise(principal.organizationId(), id).orElseThrow();
	}

	@Transactional(readOnly = true)
	public ExerciseRecord getExercise(GymOsPrincipal principal, UUID exerciseId) {
		requireTenant(principal);
		return repository.exercise(principal.organizationId(), exerciseId)
				.orElseThrow(() -> new BusinessException("EXERCISE_NOT_FOUND", "El ejercicio no existe."));
	}

	@Transactional
	public ExerciseRecord updateExercise(
			GymOsPrincipal principal, UUID exerciseId, CreateExerciseCommand command, String correlationId
	) {
		requireTenant(principal);
		ExerciseRecord existing = repository.exercise(principal.organizationId(), exerciseId)
				.orElseThrow(() -> new BusinessException("EXERCISE_NOT_FOUND", "El ejercicio no existe."));

		String name = required(command.name(), "EXERCISE_NAME_REQUIRED", "El nombre del ejercicio es obligatorio.");
		String normalizedName = normalize(name);
		String type = normalizeEnum(command.type(), "STRENGTH");

		repository.updateExercise(
				principal.organizationId(), exerciseId,
				new ExerciseData(
						name, normalizedName, blank(command.description()), blank(command.instructions()),
						blank(command.primaryMuscleGroup()), blank(command.secondaryMuscleGroups()),
						blank(command.movementPattern()), type, blank(command.equipment()),
						blank(command.suggestedLevel()), blank(command.laterality()),
						blank(command.mediaUrl()), blank(command.warnings()),
						existing.origin()
				)
		);
		auditService.record(principal, "EXERCISE_UPDATED", "EXERCISE", exerciseId, "BACKOFFICE", null, correlationId);
		return repository.exercise(principal.organizationId(), exerciseId).orElseThrow();
	}

	@Transactional
	public List<String> addAlias(GymOsPrincipal principal, UUID exerciseId, String alias, String correlationId) {
		requireTenant(principal);
		repository.exercise(principal.organizationId(), exerciseId)
				.orElseThrow(() -> new BusinessException("EXERCISE_NOT_FOUND", "El ejercicio no existe."));
		repository.addAlias(exerciseId, alias);
		auditService.record(principal, "EXERCISE_ALIAS_ADDED", "EXERCISE", exerciseId, "BACKOFFICE", alias, correlationId);
		return repository.aliases(exerciseId);
	}

	@Transactional
	public ExerciseRecord changeExerciseStatus(
			GymOsPrincipal principal, UUID exerciseId, String status, String correlationId
	) {
		requireTenant(principal);
		ExerciseRecord exercise = repository.exercise(principal.organizationId(), exerciseId)
				.orElseThrow(() -> new BusinessException("EXERCISE_NOT_FOUND", "El ejercicio no existe."));
		if (exercise.organizationId() == null) {
			throw new BusinessException("SYSTEM_EXERCISE_IMMUTABLE", "Los ejercicios del sistema no se pueden archivar.");
		}
		String normalizedStatus = normalizeEnum(status, "ACTIVE");
		if (!List.of("ACTIVE", "ARCHIVED").contains(normalizedStatus)) {
			throw new BusinessException("EXERCISE_STATUS_INVALID", "Estado de ejercicio inválido.");
		}
		repository.updateExerciseStatus(principal.organizationId(), exerciseId, normalizedStatus);
		auditService.record(principal, "EXERCISE_STATUS_CHANGED", "EXERCISE", exerciseId, "BACKOFFICE", normalizedStatus, correlationId);
		return repository.exercise(principal.organizationId(), exerciseId).orElseThrow();
	}

	@Transactional(readOnly = true)
	public AssignmentReport assignmentReport(
			GymOsPrincipal principal, LocalDate from, LocalDate to, UUID coachId, UUID clientId
	) {
		requireBranch(principal);
		LocalDate safeTo = to == null ? BusinessTime.today() : to;
		LocalDate safeFrom = from == null ? safeTo.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) : from;
		if (safeTo.isBefore(safeFrom) || safeFrom.plusYears(1).isBefore(safeTo)) {
			throw new BusinessException("REPORT_RANGE_INVALID", "El rango del reporte es inválido o excede un año.");
		}
		List<CoachMetric> metrics = repository.metrics(principal.organizationId(), principal.branchId(), safeFrom, safeTo);
		List<AssignmentDetail> details = repository.assignments(
				principal.organizationId(), principal.branchId(), safeFrom, safeTo, coachId, clientId
		);
		long assignments = metrics.stream().mapToLong(CoachMetric::assignments).sum();
		long uniqueClients = details.stream().map(AssignmentDetail::clientId).distinct().count();
		long activeCoaches = metrics.size();
		long coachesWithoutAssignments = metrics.stream().filter(metric -> metric.assignments() == 0).count();
		return new AssignmentReport(
				safeFrom, safeTo, assignments, uniqueClients, activeCoaches, coachesWithoutAssignments,
				metrics, details
		);
	}

	@Transactional(readOnly = true)
	public byte[] assignmentCsv(
			GymOsPrincipal principal, LocalDate from, LocalDate to, UUID coachId, UUID clientId
	) {
		AssignmentReport report = assignmentReport(principal, from, to, coachId, clientId);
		StringBuilder csv = new StringBuilder("\uFEFFfecha,coach,socio,rutina,version,estado\n");
		for (AssignmentDetail row : report.details()) {
			csv.append(row.assignedOn()).append(',')
					.append(csv(row.coachName())).append(',')
					.append(csv(row.clientName())).append(',')
					.append(csv(row.workoutTitle())).append(',')
					.append(row.versionNumber()).append(',')
					.append(csv(row.status())).append('\n');
		}
		return csv.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
	}

	@Transactional(readOnly = true)
	public byte[] assignmentPdf(GymOsPrincipal principal, UUID assignmentId) {
		requireBranch(principal);
		AssignmentPdfData data = repository.assignmentPdf(
				principal.organizationId(), principal.branchId(), assignmentId
		).orElseThrow(() -> new BusinessException("WORKOUT_ASSIGNMENT_NOT_FOUND", "La asignación no existe."));
		try {
			return renderPdf(data);
		} catch (IOException exception) {
			throw new BusinessException("WORKOUT_PDF_FAILED", "No fue posible generar el PDF de la rutina.");
		}
	}

	private byte[] renderPdf(AssignmentPdfData data) throws IOException {
		try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
			PdfWriter writer = new PdfWriter(document);
			writer.title(data.gymName());
			writer.heading(data.title());
			writer.line("Socio: " + data.clientName());
			writer.line("Coach: " + data.coachName());
			writer.line("Objetivo: " + value(data.goal()) + "   Nivel: " + value(data.level()));
			writer.line("Asignada: " + data.assignedOn() + "   Versión: " + data.versionNumber());
			if (data.startsOn() != null || data.endsOn() != null) {
				writer.line("Vigencia: " + value(data.startsOn()) + " a " + value(data.endsOn()));
			}
			writer.separator();
			JsonNode root = objectMapper.readTree(data.contentJson());
			JsonNode sessions = root.has("sessions") ? root.get("sessions") : root.get("days");
			if (sessions != null && sessions.isArray()) {
				int index = 1;
				for (JsonNode session : sessions) {
					writer.heading("Día " + index++ + " - " + text(session, "name", "Sesión"));
					String notes = text(session, "notes", "");
					if (!notes.isBlank()) writer.line(notes);
					JsonNode blocks = session.get("blocks");
					if (blocks != null && blocks.isArray()) {
						for (JsonNode block : blocks) {
							if (block.isObject() && block.has("exercises")) {
								writer.subheading(text(block, "name", text(block, "type", "Bloque")));
								for (JsonNode exercise : block.get("exercises")) writer.exercise(exercise);
							} else {
								writer.exercise(block);
							}
						}
					}
				}
			} else {
				writer.line(data.contentJson());
			}
			if (data.printableNotes() != null && !data.printableNotes().isBlank()) {
				writer.separator();
				writer.subheading("Indicaciones");
				writer.line(data.printableNotes());
			}
			writer.finish();
			document.save(output);
			return output.toByteArray();
		}
	}

	private String text(JsonNode node, String field, String fallback) {
		JsonNode value = node == null ? null : node.get(field);
		return value == null || value.isNull() ? fallback : value.asText(fallback);
	}

	private String required(String value, String code, String message) {
		if (value == null || value.isBlank()) throw new BusinessException(code, message);
		return value.trim();
	}
	private String blank(String value) { return value == null || value.isBlank() ? null : value.trim(); }
	private String normalizeEnum(String value, String fallback) {
		return value == null || value.isBlank() ? fallback : value.trim().toUpperCase(Locale.ROOT);
	}
	private String normalize(String value) {
		return Normalizer.normalize(value, Normalizer.Form.NFD)
				.replaceAll("\\p{M}", "").trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
	}
	private String csv(String value) { return "\"" + (value == null ? "" : value.replace("\"", "\"\"")) + "\""; }
	private String value(Object value) { return value == null ? "-" : value.toString(); }
	private void requireTenant(GymOsPrincipal principal) {
		if (principal == null || principal.organizationId() == null) {
			throw new BusinessException("AUTHENTICATION_REQUIRED", "Debes iniciar sesión para continuar.");
		}
	}
	private void requireBranch(GymOsPrincipal principal) {
		requireTenant(principal);
		if (principal.branchId() == null) {
			throw new BusinessException("BRANCH_REQUIRED", "Selecciona una sucursal para operar.");
		}
	}

	public record CreateExerciseCommand(
			String name, String description, String instructions, String primaryMuscleGroup,
			String secondaryMuscleGroups, String movementPattern, String type, String equipment,
			String suggestedLevel, String laterality, String mediaUrl, String warnings
	) {}
	public record AssignmentReport(
			LocalDate from, LocalDate to, long assignments, long uniqueClients,
			long activeCoaches, long coachesWithoutAssignments,
			List<CoachMetric> coaches, List<AssignmentDetail> details
	) {}

	private final class PdfWriter {
		private final PDDocument document;
		private final PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
		private final PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
		private PDPage page;
		private PDPageContentStream stream;
		private float y;

		private PdfWriter(PDDocument document) throws IOException {
			this.document = document;
			newPage();
		}
		void title(String value) throws IOException { write(value, 18, bold, 22); }
		void heading(String value) throws IOException { ensure(40); write(value, 15, bold, 20); }
		void subheading(String value) throws IOException { ensure(32); write(value, 12, bold, 17); }
		void line(String value) throws IOException {
			for (String part : wrap(value == null ? "" : value, 96)) write(part, 10, regular, 14);
		}
		void exercise(JsonNode node) throws IOException {
			String name = text(node, "exerciseName", text(node, "exercise", text(node, "name", "Ejercicio")));
			String prescription = text(node, "prescription", "");
			if (prescription.isBlank()) {
				String sets = text(node, "sets", "");
				String reps = text(node, "reps", "");
				prescription = (sets.isBlank() ? "" : sets + " x ") + reps;
			}
			String notes = text(node, "notes", "");
			line("• " + name + (prescription.isBlank() ? "" : " — " + prescription) + (notes.isBlank() ? "" : " — " + notes));
		}
		void separator() throws IOException { ensure(20); y -= 8; }
		void finish() throws IOException { if (stream != null) stream.close(); }
		private void write(String value, float size, PDType1Font font, float leading) throws IOException {
			ensure(leading + 8);
			stream.beginText();
			stream.setFont(font, size);
			stream.newLineAtOffset(45, y);
			stream.showText(safePdfText(value));
			stream.endText();
			y -= leading;
		}
		private void ensure(float needed) throws IOException { if (y - needed < 45) newPage(); }
		private void newPage() throws IOException {
			if (stream != null) stream.close();
			page = new PDPage(PDRectangle.LETTER);
			document.addPage(page);
			stream = new PDPageContentStream(document, page);
			y = page.getMediaBox().getHeight() - 45;
		}
		private List<String> wrap(String value, int max) {
			List<String> lines = new ArrayList<>();
			for (String paragraph : value.replace('\r', ' ').split("\\n")) {
				StringBuilder current = new StringBuilder();
				for (String word : paragraph.split("\\s+")) {
					if (current.length() + word.length() + 1 > max && !current.isEmpty()) {
						lines.add(current.toString()); current.setLength(0);
					}
					if (!current.isEmpty()) current.append(' ');
					current.append(word);
				}
				if (!current.isEmpty()) lines.add(current.toString());
			}
			return lines.isEmpty() ? List.of("") : lines;
		}
		private String safePdfText(String value) {
			return value.replace('—', '-').replace('•', '-').replace('\t', ' ');
		}
	}
}
