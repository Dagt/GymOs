package com.gymos.coaching.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymos.audit.api.AuditService;
import com.gymos.coaching.infrastructure.CoachingCatalogRepository;
import com.gymos.coaching.infrastructure.CoachingCatalogRepository.AssignmentDetail;
import com.gymos.coaching.infrastructure.CoachingCatalogRepository.AssignmentPdfData;
import com.gymos.coaching.infrastructure.CoachingCatalogRepository.AssignmentStats;
import com.gymos.coaching.infrastructure.CoachingCatalogRepository.CoachMetric;
import com.gymos.coaching.infrastructure.CoachingCatalogRepository.ExerciseData;
import com.gymos.coaching.infrastructure.CoachingCatalogRepository.ExercisePage;
import com.gymos.coaching.infrastructure.CoachingCatalogRepository.ExerciseRecord;
import com.gymos.common.api.BusinessException;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.common.time.BusinessTime;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.awt.Color;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.text.Normalizer;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Duration;
import java.time.temporal.TemporalAdjusters;
import java.time.temporal.ChronoUnit;
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
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
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
		requireManageableExercise(principal, existing);

		String name = required(command.name(), "EXERCISE_NAME_REQUIRED", "El nombre del ejercicio es obligatorio.");
		String normalizedName = normalize(name);
		repository.activeByNormalizedName(principal.organizationId(), normalizedName)
				.filter(match -> !match.id().equals(exerciseId))
				.ifPresent(match -> {
					throw new BusinessException("EXERCISE_DUPLICATE", "Ya existe un ejercicio con ese nombre.");
				});
		repository.activeByNormalizedAlias(principal.organizationId(), normalizedName)
				.filter(match -> !match.id().equals(exerciseId))
				.ifPresent(match -> {
					throw new BusinessException("EXERCISE_DUPLICATE", "El nombre coincide con el alias de otro ejercicio.");
				});
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
		ExerciseRecord exercise = repository.exercise(principal.organizationId(), exerciseId)
				.orElseThrow(() -> new BusinessException("EXERCISE_NOT_FOUND", "El ejercicio no existe."));
		requireManageableExercise(principal, exercise);
		String cleanAlias = required(alias, "EXERCISE_ALIAS_REQUIRED", "El alias es obligatorio.");
		String normalizedAlias = normalize(cleanAlias);
		if (normalizedAlias.equals(normalize(exercise.name()))) {
			throw new BusinessException("EXERCISE_ALIAS_DUPLICATE", "El alias coincide con el nombre del ejercicio.");
		}
		repository.activeByNormalizedName(principal.organizationId(), normalizedAlias).ifPresent(match -> {
			throw new BusinessException("EXERCISE_ALIAS_DUPLICATE", "El alias coincide con un ejercicio existente.");
		});
		repository.activeByNormalizedAlias(principal.organizationId(), normalizedAlias)
				.filter(match -> !match.id().equals(exerciseId))
				.ifPresent(match -> {
					throw new BusinessException("EXERCISE_ALIAS_DUPLICATE", "El alias pertenece a otro ejercicio.");
				});
		repository.addAlias(exerciseId, cleanAlias, normalizedAlias);
		auditService.record(principal, "EXERCISE_ALIAS_ADDED", "EXERCISE", exerciseId, "BACKOFFICE", cleanAlias, correlationId);
		return repository.aliases(exerciseId);
	}

	@Transactional(readOnly = true)
	public List<String> aliases(GymOsPrincipal principal, UUID exerciseId) {
		requireTenant(principal);
		repository.exercise(principal.organizationId(), exerciseId)
				.orElseThrow(() -> new BusinessException("EXERCISE_NOT_FOUND", "El ejercicio no existe."));
		return repository.aliases(exerciseId);
	}

	@Transactional
	public List<String> deleteAlias(
			GymOsPrincipal principal, UUID exerciseId, String alias, String correlationId
	) {
		requireTenant(principal);
		ExerciseRecord exercise = repository.exercise(principal.organizationId(), exerciseId)
				.orElseThrow(() -> new BusinessException("EXERCISE_NOT_FOUND", "El ejercicio no existe."));
		requireManageableExercise(principal, exercise);
		String cleanAlias = required(alias, "EXERCISE_ALIAS_REQUIRED", "El alias es obligatorio.");
		repository.deleteAlias(exerciseId, normalize(cleanAlias));
		auditService.record(principal, "EXERCISE_ALIAS_DELETED", "EXERCISE", exerciseId, "BACKOFFICE", cleanAlias, correlationId);
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
		return assignmentReport(principal, from, to, coachId, clientId, 0, 50);
	}

	@Transactional(readOnly = true)
	public AssignmentReport assignmentReport(
			GymOsPrincipal principal, LocalDate from, LocalDate to, UUID coachId, UUID clientId,
			int page, int size
	) {
		requireBranch(principal);
		LocalDate safeTo = to == null ? BusinessTime.today() : to;
		LocalDate safeFrom = from == null ? safeTo.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) : from;
		if (safeTo.isBefore(safeFrom) || safeFrom.plusYears(1).isBefore(safeTo)) {
			throw new BusinessException("REPORT_RANGE_INVALID", "El rango del reporte es inválido o excede un año.");
		}
		int safePage = Math.max(0, page);
		int safeSize = Math.max(1, Math.min(MAX_PAGE_SIZE, size));
		UUID ownerUserId = workoutReadScope(principal);
		List<CoachMetric> metrics = repository.metrics(
				principal.organizationId(), principal.branchId(), safeFrom, safeTo,
				coachId, clientId, ownerUserId
		);
		AssignmentStats currentStats = repository.assignmentStats(
				principal.organizationId(), principal.branchId(), safeFrom, safeTo,
				coachId, clientId, ownerUserId
		);
		List<AssignmentDetail> details = repository.assignments(
				principal.organizationId(), principal.branchId(), safeFrom, safeTo,
				coachId, clientId, ownerUserId, safeSize, safePage * safeSize
		);
		long activeCoaches = metrics.size();
		long coachesWithoutAssignments = metrics.stream().filter(metric -> metric.assignments() == 0).count();
		long periodDays = ChronoUnit.DAYS.between(safeFrom, safeTo) + 1;
		LocalDate previousTo = safeFrom.minusDays(1);
		LocalDate previousFrom = previousTo.minusDays(periodDays - 1);
		AssignmentStats previousStats = repository.assignmentStats(
				principal.organizationId(), principal.branchId(), previousFrom, previousTo,
				coachId, clientId, ownerUserId
		);
		int totalPages = (int) Math.ceil(currentStats.totalRows() / (double) safeSize);
		return new AssignmentReport(
				safeFrom, safeTo, currentStats.assignments(), currentStats.uniqueClients(),
				activeCoaches, coachesWithoutAssignments,
				previousFrom, previousTo, previousStats.assignments(), previousStats.uniqueClients(),
				currentStats.assignments() - previousStats.assignments(),
				currentStats.uniqueClients() - previousStats.uniqueClients(),
				safePage, safeSize, currentStats.totalRows(), totalPages,
				metrics, details
		);
	}

	@Transactional(readOnly = true)
	public byte[] assignmentCsv(
			GymOsPrincipal principal, LocalDate from, LocalDate to, UUID coachId, UUID clientId
	) {
		AssignmentReport report = assignmentReport(principal, from, to, coachId, clientId);
		if (report.totalDetails() > 10_000) {
			throw new BusinessException(
					"REPORT_EXPORT_TOO_LARGE",
					"El reporte excede 10,000 asignaciones. Reduce el rango o filtra por coach."
			);
		}
		List<AssignmentDetail> exportRows = repository.assignments(
				principal.organizationId(), principal.branchId(), report.from(), report.to(),
				coachId, clientId, workoutReadScope(principal),
				Math.max(1, (int) report.totalDetails()), 0
		);
		StringBuilder csv = new StringBuilder(
				"\uFEFFfecha,coach,socio,rutina,version,estado,motivo,reemplazada_por\n"
		);
		for (AssignmentDetail row : exportRows) {
			csv.append(row.assignedOn()).append(',')
					.append(csv(row.coachName())).append(',')
					.append(csv(row.clientName())).append(',')
					.append(csv(row.workoutTitle())).append(',')
					.append(row.versionNumber()).append(',')
					.append(csv(row.status())).append(',')
					.append(csv(row.statusReason())).append(',')
					.append(csv(row.replacedAssignmentId() == null ? null : row.replacedAssignmentId().toString()))
					.append('\n');
		}
		return csv.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
	}

	@Transactional(readOnly = true)
	public byte[] assignmentPdf(GymOsPrincipal principal, UUID assignmentId) {
		requireBranch(principal);
		AssignmentPdfData data = repository.assignmentPdf(
				principal.organizationId(), principal.branchId(), assignmentId, workoutReadScope(principal)
		).orElseThrow(() -> new BusinessException("WORKOUT_ASSIGNMENT_NOT_FOUND", "La asignación no existe."));
		try {
			return renderPdf(data);
		} catch (IOException exception) {
			throw new BusinessException("WORKOUT_PDF_FAILED", "No fue posible generar el PDF de la rutina.");
		}
	}

	private byte[] renderPdf(AssignmentPdfData data) throws IOException {
		try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
			PdfWriter writer = new PdfWriter(document, data);
			writer.heading(data.title());
			writer.summary("Socio", data.clientName(), "Coach", data.coachName());
			writer.summary("Objetivo", value(data.goal()), "Nivel", value(data.level()));
			writer.summary("Asignada", data.assignedOn().toString(), "Versión", String.valueOf(data.versionNumber()));
			if (data.startsOn() != null || data.endsOn() != null) {
				writer.summary("Vigencia", value(data.startsOn()) + " a " + value(data.endsOn()), "", "");
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
	private void requireManageableExercise(GymOsPrincipal principal, ExerciseRecord exercise) {
		if (exercise.organizationId() == null || "SYSTEM".equals(exercise.origin())) {
			throw new BusinessException("SYSTEM_EXERCISE_IMMUTABLE", "Los ejercicios del sistema son de solo lectura.");
		}
		if (!principal.permissions().contains("exercise.manage")
				&& !repository.exerciseCreatedBy(principal.organizationId(), exercise.id(), principal.userId())) {
			throw new BusinessException("EXERCISE_OUTSIDE_OWNER_SCOPE", "Solo puedes modificar ejercicios creados por ti.");
		}
	}
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
	private UUID workoutReadScope(GymOsPrincipal principal) {
		if (principal.permissions().contains("workout.read.all")
				|| principal.permissions().contains("workout.manage.all")) return null;
		if (principal.permissions().contains("workout.read.own")
				|| principal.permissions().contains("workout.manage.own")) return principal.userId();
		throw new BusinessException("WORKOUT_READ_FORBIDDEN", "No tienes permiso para consultar esta rutina.");
	}

	public record CreateExerciseCommand(
			String name, String description, String instructions, String primaryMuscleGroup,
			String secondaryMuscleGroups, String movementPattern, String type, String equipment,
			String suggestedLevel, String laterality, String mediaUrl, String warnings
	) {}
	public record AssignmentReport(
			LocalDate from, LocalDate to, long assignments, long uniqueClients,
			long activeCoaches, long coachesWithoutAssignments,
			LocalDate previousFrom, LocalDate previousTo,
			long previousAssignments, long previousUniqueClients,
			long assignmentChange, long uniqueClientChange,
			int page, int size, long totalDetails, int totalPages,
			List<CoachMetric> coaches, List<AssignmentDetail> details
	) {}

	private final class PdfWriter {
		private final PDDocument document;
		private final AssignmentPdfData data;
		private final Color primary;
		private final PDImageXObject logo;
		private final PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
		private final PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
		private PDPage page;
		private PDPageContentStream stream;
		private float y;

		private PdfWriter(PDDocument document, AssignmentPdfData data) throws IOException {
			this.document = document;
			this.data = data;
			this.primary = color(data.primaryColor());
			this.logo = loadLogo(document, data.logoUrl());
			newPage();
		}
		void heading(String value) throws IOException { ensure(40); write(value, 15, bold, 20); }
		void subheading(String value) throws IOException { ensure(32); write(value, 12, bold, 17); }
		void summary(String labelA, String valueA, String labelB, String valueB) throws IOException {
			String left = labelA.isBlank() ? valueA : labelA + ": " + valueA;
			String right = labelB.isBlank() ? "" : labelB + ": " + valueB;
			write(left + (right.isBlank() ? "" : "    |    " + right), 10, regular, 15);
		}
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
			List<String> details = new ArrayList<>();
			addDetail(details, "Carga", text(node, "load", ""));
			addDetail(details, "Descanso", suffix(text(node, "restSeconds", ""), " s"));
			addDetail(details, "Tempo", text(node, "tempo", ""));
			addDetail(details, "Duración", text(node, "duration", ""));
			addDetail(details, "Distancia", text(node, "distance", ""));
			addDetail(details, "Grupo", text(node, "groupKey", ""));
			String notes = text(node, "notes", "");
			int rows = 1 + (details.isEmpty() ? 0 : 1) + (notes.isBlank() ? 0 : 1);
			ensure(12 + rows * 13);
			float boxHeight = 8 + rows * 13;
			stream.setNonStrokingColor(new Color(245, 248, 246));
			stream.addRect(40, y - boxHeight + 9, page.getMediaBox().getWidth() - 80, boxHeight);
			stream.fill();
			stream.setNonStrokingColor(Color.BLACK);
			write(name + (prescription.isBlank() ? "" : "  |  " + prescription), 10, bold, 14);
			if (!details.isEmpty()) write(String.join("  |  ", details), 8, regular, 12);
			if (!notes.isBlank()) write(notes, 8, regular, 12);
			y -= 3;
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
			page = new PDPage(PDRectangle.A4);
			document.addPage(page);
			stream = new PDPageContentStream(document, page);
			float top = page.getMediaBox().getHeight();
			stream.setNonStrokingColor(primary);
			stream.addRect(0, top - 88, page.getMediaBox().getWidth(), 88);
			stream.fill();
			stream.setNonStrokingColor(Color.WHITE);
			if (logo == null) {
				String initials = data.gymName() == null || data.gymName().isBlank()
						? "G" : data.gymName().substring(0, 1).toUpperCase(Locale.ROOT);
				writeAt(initials, 48, top - 51, 24, bold);
			} else {
				stream.drawImage(logo, 40, top - 72, 48, 48);
			}
			writeAt(data.gymName(), 86, top - 41, 17, bold);
			writeAt(data.branchName() + (data.contactPhone() == null ? "" : "  |  " + data.contactPhone()),
					86, top - 61, 9, regular);
			stream.setNonStrokingColor(Color.BLACK);
			y = top - 112;
		}
		private void writeAt(String value, float x, float positionY, float size, PDType1Font font) throws IOException {
			stream.beginText();
			stream.setFont(font, size);
			stream.newLineAtOffset(x, positionY);
			stream.showText(safePdfText(value == null ? "" : value));
			stream.endText();
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
			String normalized = value
					.replace('—', '-')
					.replace('–', '-')
					.replace('•', '-')
					.replace('“', '"')
					.replace('”', '"')
					.replace('‘', '\'')
					.replace('’', '\'')
					.replace('\t', ' ');
			StringBuilder safe = new StringBuilder(normalized.length());
			for (int index = 0; index < normalized.length(); index++) {
				char character = normalized.charAt(index);
				safe.append(character >= 32 && character <= 255 ? character : '?');
			}
			return safe.toString();
		}
		private void addDetail(List<String> details, String label, String value) {
			if (value != null && !value.isBlank()) details.add(label + ": " + value);
		}
		private String suffix(String value, String suffix) {
			return value == null || value.isBlank() ? "" : value + suffix;
		}
	}

	private Color color(String value) {
		try {
			return Color.decode(value == null || value.isBlank() ? "#173C2A" : value);
		} catch (NumberFormatException ignored) {
			return Color.decode("#173C2A");
		}
	}

	private PDImageXObject loadLogo(PDDocument document, String logoUrl) {
		if (logoUrl == null || logoUrl.isBlank()) return null;
		try {
			URI uri = URI.create(logoUrl);
			if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) return null;
			for (InetAddress address : InetAddress.getAllByName(uri.getHost())) {
				if (address.isAnyLocalAddress() || address.isLoopbackAddress()
						|| address.isLinkLocalAddress() || address.isSiteLocalAddress()) return null;
			}
			HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(3)).GET().build();
			HttpResponse<byte[]> response = HttpClient.newBuilder()
					.connectTimeout(Duration.ofSeconds(2))
					.followRedirects(HttpClient.Redirect.NEVER)
					.build()
					.send(request, HttpResponse.BodyHandlers.ofByteArray());
			byte[] body = response.body();
			if (response.statusCode() / 100 != 2 || body.length == 0 || body.length > 2_000_000) return null;
			return PDImageXObject.createFromByteArray(document, body, "gym-logo");
		} catch (Exception ignored) {
			return null;
		}
	}
}
