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
import com.gymos.coaching.infrastructure.CoachingCatalogRepository.WeeklyTrendMetric;
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
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
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

	@Transactional(readOnly = true)
	public List<String> exerciseEquipmentOptions(GymOsPrincipal principal) {
		requireTenant(principal);
		return repository.exerciseEquipmentOptions(principal.organizationId());
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
		return buildAssignmentReport(
				principal, from, to, coachId, clientId, page, size, workoutReadScope(principal)
		);
	}

	@Transactional(readOnly = true)
	public AssignmentReport metricsReport(
			GymOsPrincipal principal, LocalDate from, LocalDate to, UUID coachId, UUID clientId,
			int page, int size
	) {
		return buildAssignmentReport(
				principal, from, to, coachId, clientId, page, size, metricsReadScope(principal)
		);
	}

	private AssignmentReport buildAssignmentReport(
			GymOsPrincipal principal, LocalDate from, LocalDate to, UUID coachId, UUID clientId,
			int page, int size, UUID ownerUserId
	) {
		requireBranch(principal);
		LocalDate safeTo = to == null ? BusinessTime.today() : to;
		LocalDate safeFrom = from == null ? safeTo.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) : from;
		if (safeTo.isBefore(safeFrom) || safeFrom.plusYears(1).isBefore(safeTo)) {
			throw new BusinessException("REPORT_RANGE_INVALID", "El rango del reporte es inválido o excede un año.");
		}
		int safePage = Math.max(0, page);
		int safeSize = Math.max(1, Math.min(MAX_PAGE_SIZE, size));
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
	public WeeklyTrendReport weeklyTrend(
			GymOsPrincipal principal, int weeks, LocalDate to, UUID coachId, UUID clientId
	) {
		requireBranch(principal);
		if (weeks < 4 || weeks > 52) {
			throw new BusinessException(
					"REPORT_WEEKS_INVALID",
					"El historial semanal debe cubrir entre 4 y 52 semanas."
			);
		}
		LocalDate safeTo = to == null ? BusinessTime.today() : to;
		LocalDate currentWeek = safeTo.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
		LocalDate safeFrom = currentWeek.minusWeeks(weeks - 1L);
		UUID ownerUserId = metricsReadScope(principal);
		Map<LocalDate, WeeklyTrendMetric> values = repository.weeklyTrend(
				principal.organizationId(), principal.branchId(), safeFrom, safeTo,
				coachId, clientId, ownerUserId
		).stream().collect(Collectors.toMap(WeeklyTrendMetric::weekStart, Function.identity()));
		List<WeeklyTrendPoint> points = new ArrayList<>();
		for (int index = 0; index < weeks; index++) {
			LocalDate weekFrom = safeFrom.plusWeeks(index);
			LocalDate weekTo = weekFrom.plusDays(6).isAfter(safeTo)
					? safeTo : weekFrom.plusDays(6);
			WeeklyTrendMetric metric = values.get(weekFrom);
			points.add(new WeeklyTrendPoint(
					weekFrom, weekTo,
					metric == null ? 0 : metric.assignments(),
					metric == null ? 0 : metric.uniqueClients(),
					metric == null ? 0 : metric.firstWorkouts()
			));
		}
		return new WeeklyTrendReport(safeFrom, safeTo, weeks, points);
	}

	@Transactional(readOnly = true)
	public byte[] assignmentCsv(
			GymOsPrincipal principal, LocalDate from, LocalDate to, UUID coachId, UUID clientId
	) {
		UUID ownerUserId = metricsReadScope(principal);
		AssignmentReport report = buildAssignmentReport(
				principal, from, to, coachId, clientId, 0, 50, ownerUserId
		);
		if (report.totalDetails() > 10_000) {
			throw new BusinessException(
					"REPORT_EXPORT_TOO_LARGE",
					"El reporte excede 10,000 asignaciones. Reduce el rango o filtra por coach."
			);
		}
		List<AssignmentDetail> exportRows = repository.assignments(
				principal.organizationId(), principal.branchId(), report.from(), report.to(),
				coachId, clientId, ownerUserId,
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
			writer.workoutTitle(data.title(), data.versionNumber());
			writer.assignmentSummary();
			if (data.summary() != null && !data.summary().isBlank()) {
				writer.callout("Indicaciones generales", data.summary());
			}
			JsonNode root = objectMapper.readTree(data.contentJson());
			JsonNode sessions = root.has("sessions") ? root.get("sessions") : root.get("days");
			if (sessions != null && sessions.isArray()) {
				int dayIndex = 1;
				for (JsonNode session : sessions) {
					writer.day(
							dayIndex++,
							text(session, "name", "Sesión"),
							text(session, "focus", "")
					);
					String notes = text(session, "notes", "");
					if (!notes.isBlank()) writer.note("Preparación del día", notes);
					JsonNode blocks = session.get("blocks");
					if (blocks != null && blocks.isArray()) {
						for (JsonNode block : blocks) {
							if (block.isObject() && block.has("exercises")) {
								writer.block(
										text(block, "name", "Bloque"),
										text(block, "type", ""),
										text(block, "notes", "")
								);
								int exerciseIndex = 1;
								for (JsonNode exercise : block.get("exercises")) {
									writer.exercise(exercise, exerciseIndex++);
								}
							} else {
								writer.exercise(block, 1);
							}
						}
					}
				}
			} else {
				writer.line(data.contentJson());
			}
			if (data.printableNotes() != null && !data.printableNotes().isBlank()) {
				writer.callout("Indicaciones personales del coach", data.printableNotes());
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

	private UUID metricsReadScope(GymOsPrincipal principal) {
		if (!principal.permissions().contains("coach.metrics.read")) {
			throw new BusinessException(
					"COACH_METRICS_READ_FORBIDDEN",
					"No tienes permiso para consultar las métricas de coaches."
			);
		}
		if (principal.permissions().contains("workout.read.all")
				|| principal.permissions().contains("workout.manage.all")) return null;
		if (principal.permissions().contains("workout.read.own")
				|| principal.permissions().contains("workout.manage.own")) return principal.userId();
		return null;
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

	public record WeeklyTrendReport(
			LocalDate from, LocalDate to, int weeks, List<WeeklyTrendPoint> points
	) {}

	public record WeeklyTrendPoint(
			LocalDate weekFrom, LocalDate weekTo,
			long assignments, long uniqueClients, long firstWorkouts
	) {}

	private final class PdfWriter {
		private static final float MARGIN = 40;
		private static final float CONTENT_WIDTH = PDRectangle.A4.getWidth() - MARGIN * 2;
		private static final float BOTTOM_LIMIT = 52;
		private final PDDocument document;
		private final AssignmentPdfData data;
		private final Color primary;
		private final Color primaryDark;
		private final Color primarySoft;
		private final Color ink = new Color(30, 42, 35);
		private final Color muted = new Color(91, 107, 98);
		private final Color line = new Color(218, 226, 221);
		private final Color surface = new Color(247, 249, 247);
		private final Color accent = new Color(214, 173, 48);
		private final PDImageXObject logo;
		private final PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
		private final PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
		private PDPage page;
		private PDPageContentStream stream;
		private float y;
		private int pageNumber;

		private PdfWriter(PDDocument document, AssignmentPdfData data) throws IOException {
			this.document = document;
			this.data = data;
			this.primary = color(data.primaryColor());
			this.primaryDark = blend(primary, Color.BLACK, 0.18f);
			this.primarySoft = blend(primary, Color.WHITE, 0.91f);
			this.logo = loadLogo(document, data.logoUrl());
			newPage();
		}

		void workoutTitle(String value, int version) throws IOException {
			List<String> titleLines = wrap(value, bold, 22, CONTENT_WIDTH - 80);
			ensure(35 + titleLines.size() * 25);
			writeAt("RUTINA ASIGNADA", MARGIN, y, 8, bold, muted);
			writePill("VERSIÓN " + version, PDRectangle.A4.getWidth() - MARGIN - 68, y - 5, 68);
			y -= 24;
			for (String titleLine : titleLines) {
				writeAt(titleLine, MARGIN, y, 22, bold, ink);
				y -= 25;
			}
			y -= 5;
		}

		void assignmentSummary() throws IOException {
			String validity = data.startsOn() == null && data.endsOn() == null
					? "Sin vigencia definida"
					: formatDate(data.startsOn()) + " - " + formatDate(data.endsOn());
			List<Info> values = List.of(
					new Info("SOCIO", value(data.clientName())),
					new Info("COACH", value(data.coachName())),
					new Info("OBJETIVO", value(data.goal())),
					new Info("NIVEL", levelLabel(data.level())),
					new Info("ASIGNADA", formatDate(data.assignedOn())),
					new Info("VIGENCIA", validity)
			);
			int rows = (values.size() + 1) / 2;
			ensure(rows * 56 + 10);
			float gap = 8;
			float cardWidth = (CONTENT_WIDTH - gap) / 2;
			for (int index = 0; index < values.size(); index++) {
				int column = index % 2;
				int row = index / 2;
				float cardX = MARGIN + column * (cardWidth + gap);
				float cardTop = y - row * 56;
				infoCard(values.get(index), cardX, cardTop, cardWidth, 48);
			}
			y -= rows * 56 + 4;
		}

		void callout(String label, String value) throws IOException {
			List<String> lines = wrap(value, regular, 9, CONTENT_WIDTH - 32);
			float height = 31 + lines.size() * 12;
			ensure(height + 10);
			stream.setNonStrokingColor(primarySoft);
			stream.addRect(MARGIN, y - height, CONTENT_WIDTH, height);
			stream.fill();
			stream.setNonStrokingColor(accent);
			stream.addRect(MARGIN, y - height, 4, height);
			stream.fill();
			writeAt(label.toUpperCase(Locale.ROOT), MARGIN + 16, y - 17, 8, bold, primaryDark);
			float cursor = y - 34;
			for (String line : lines) {
				writeAt(line, MARGIN + 16, cursor, 9, regular, ink);
				cursor -= 12;
			}
			y -= height + 10;
		}

		void day(int index, String name, String focus) throws IOException {
			ensure(115);
			y -= 5;
			stream.setNonStrokingColor(primary);
			stream.addRect(MARGIN, y - 46, CONTENT_WIDTH, 46);
			stream.fill();
			stream.setNonStrokingColor(accent);
			stream.addRect(MARGIN, y - 46, 5, 46);
			stream.fill();
			writeAt(String.format(Locale.ROOT, "DÍA %02d", index), MARGIN + 18, y - 17, 8, bold, Color.WHITE);
			writeAt(truncate(name, bold, 14, 305), MARGIN + 18, y - 35, 14, bold, Color.WHITE);
			if (focus != null && !focus.isBlank()) {
				writeRight("ENFOQUE  " + focus.toUpperCase(Locale.ROOT), PDRectangle.A4.getWidth() - MARGIN - 16,
						y - 29, 8, bold, Color.WHITE);
			}
			y -= 56;
		}

		void block(String name, String type, String notes) throws IOException {
			List<String> noteLines = notes == null || notes.isBlank()
					? List.of()
					: wrap(notes, regular, 8, CONTENT_WIDTH - 20);
			float height = 32 + noteLines.size() * 11;
			ensure(height + 90);
			writeAt(blockTypeLabel(type), MARGIN, y - 8, 7, bold, primary);
			writeAt(truncate(name, bold, 12, CONTENT_WIDTH - 145), MARGIN, y - 24, 12, bold, ink);
			stream.setStrokingColor(line);
			stream.setLineWidth(0.8f);
			stream.moveTo(MARGIN, y - 30);
			stream.lineTo(PDRectangle.A4.getWidth() - MARGIN, y - 30);
			stream.stroke();
			float cursor = y - 43;
			for (String noteLine : noteLines) {
				writeAt(noteLine, MARGIN, cursor, 8, regular, muted);
				cursor -= 11;
			}
			y -= height + 8;
		}

		void note(String label, String value) throws IOException {
			List<String> lines = wrap(value, regular, 8, CONTENT_WIDTH - 24);
			float height = 25 + lines.size() * 11;
			ensure(height + 5);
			stream.setNonStrokingColor(surface);
			stream.addRect(MARGIN, y - height, CONTENT_WIDTH, height);
			stream.fill();
			writeAt(label.toUpperCase(Locale.ROOT), MARGIN + 12, y - 15, 7, bold, muted);
			float cursor = y - 29;
			for (String line : lines) {
				writeAt(line, MARGIN + 12, cursor, 8, regular, ink);
				cursor -= 11;
			}
			y -= height + 8;
		}

		void line(String value) throws IOException {
			callout("Contenido de la rutina", value == null ? "" : value);
		}

		void exercise(JsonNode node, int exerciseIndex) throws IOException {
			String name = text(node, "exerciseName", text(node, "exercise", text(node, "name", "Ejercicio")));
			String prescription = text(node, "prescription", "");
			List<SetRow> setRows = setRows(node);
			List<String> details = new ArrayList<>();
			addDetail(details, "Descanso", suffix(text(node, "restSeconds", ""), " s"));
			addDetail(details, "Tempo", text(node, "tempo", ""));
			addDetail(details, "Duración", text(node, "duration", ""));
			addDetail(details, "Distancia", text(node, "distance", ""));
			addDetail(details, "Agrupación", text(node, "groupKey", ""));
			if (node.path("optional").asBoolean(false)) details.add("Opcional");
			String notes = text(node, "notes", "");
			List<String> nameLines = wrap(name, bold, 11, CONTENT_WIDTH - 54);
			List<String> detailLines = details.isEmpty()
					? List.of()
					: wrap(String.join("  |  ", details), regular, 8, CONTENT_WIDTH - 28);
			List<String> prescriptionLines = prescription.isBlank()
					? List.of()
					: wrap("Indicación: " + prescription, regular, 8, CONTENT_WIDTH - 28);
			List<String> noteLines = notes.isBlank()
					? List.of()
					: wrap("Técnica: " + notes, regular, 8, CONTENT_WIDTH - 28);
			float boxHeight = 19 + nameLines.size() * 14
					+ (setRows.isEmpty() ? 0 : 20 + setRows.size() * 19)
					+ (detailLines.size() + prescriptionLines.size() + noteLines.size()) * 12
					+ 12;
			ensure(boxHeight + 10);
			float top = y;
			stream.setNonStrokingColor(surface);
			stream.addRect(MARGIN, top - boxHeight, CONTENT_WIDTH, boxHeight);
			stream.fill();
			stream.setNonStrokingColor(primary);
			stream.addRect(MARGIN, top - boxHeight, 4, boxHeight);
			stream.fill();
			stream.setNonStrokingColor(primarySoft);
			stream.addRect(MARGIN + 13, top - 32, 24, 24);
			stream.fill();
			writeCentered(String.valueOf(exerciseIndex), MARGIN + 25, top - 25, 9, bold, primaryDark);
			float cursor = top - 18;
			for (String nameLine : nameLines) {
				writeAt(nameLine, MARGIN + 48, cursor, 11, bold, ink);
				cursor -= 14;
			}
			cursor -= 3;
			if (!setRows.isEmpty()) {
				stream.setNonStrokingColor(blend(primary, Color.WHITE, 0.86f));
				stream.addRect(MARGIN + 12, cursor - 16, CONTENT_WIDTH - 24, 17);
				stream.fill();
				writeAt("SERIE", MARGIN + 20, cursor - 11, 7, bold, primaryDark);
				writeAt("REPETICIONES", MARGIN + 150, cursor - 11, 7, bold, primaryDark);
				writeAt("CARGA", MARGIN + 310, cursor - 11, 7, bold, primaryDark);
				cursor -= 19;
				for (SetRow row : setRows) {
					writeAt(truncate(row.label(), regular, 8, 115), MARGIN + 20, cursor - 11, 8, regular, ink);
					writeAt(truncate(row.reps(), regular, 8, 145), MARGIN + 150, cursor - 11, 8, regular, ink);
					writeAt(truncate(row.load(), regular, 8, 170), MARGIN + 310, cursor - 11, 8, regular, ink);
					stream.setStrokingColor(line);
					stream.setLineWidth(0.35f);
					stream.moveTo(MARGIN + 12, cursor - 16);
					stream.lineTo(PDRectangle.A4.getWidth() - MARGIN - 12, cursor - 16);
					stream.stroke();
					cursor -= 19;
				}
			}
			cursor = writeLines(detailLines, MARGIN + 14, cursor - 2, 8, regular, muted, 12);
			cursor = writeLines(prescriptionLines, MARGIN + 14, cursor, 8, regular, ink, 12);
			writeLines(noteLines, MARGIN + 14, cursor, 8, regular, ink, 12);
			y = top - boxHeight - 9;
		}

		void finish() throws IOException {
			if (stream != null) stream.close();
			int totalPages = document.getNumberOfPages();
			for (int index = 0; index < totalPages; index++) {
				PDPage footerPage = document.getPage(index);
				try (PDPageContentStream footer = new PDPageContentStream(
						document, footerPage, PDPageContentStream.AppendMode.APPEND, true, true
				)) {
					footer.setStrokingColor(line);
					footer.setLineWidth(0.6f);
					footer.moveTo(MARGIN, 39);
					footer.lineTo(PDRectangle.A4.getWidth() - MARGIN, 39);
					footer.stroke();
					writeAt(footer, value(data.branchName()), MARGIN, 24, 7, regular, muted);
					writeRight(footer, "Página " + (index + 1) + " de " + totalPages,
							PDRectangle.A4.getWidth() - MARGIN, 24, 7, bold, muted);
				}
			}
		}

		private void ensure(float needed) throws IOException {
			if (y - needed < BOTTOM_LIMIT) {
				newPage();
				y = PDRectangle.A4.getHeight() - 112;
			}
		}

		private void newPage() throws IOException {
			if (stream != null) stream.close();
			page = new PDPage(PDRectangle.A4);
			document.addPage(page);
			stream = new PDPageContentStream(document, page);
			pageNumber++;
			float top = page.getMediaBox().getHeight();
			stream.setNonStrokingColor(primaryDark);
			stream.addRect(0, top - 84, page.getMediaBox().getWidth(), 84);
			stream.fill();
			stream.setNonStrokingColor(accent);
			stream.addRect(0, top - 88, page.getMediaBox().getWidth(), 4);
			stream.fill();
			if (logo == null) {
				String initials = data.gymName() == null || data.gymName().isBlank()
						? "G" : data.gymName().substring(0, 1).toUpperCase(Locale.ROOT);
				stream.setNonStrokingColor(Color.WHITE);
				stream.addRect(MARGIN, top - 68, 44, 44);
				stream.fill();
				writeCentered(initials, MARGIN + 22, top - 49, 20, bold, primaryDark);
			} else {
				stream.drawImage(logo, MARGIN, top - 68, 44, 44);
			}
			writeAt(value(data.gymName()), MARGIN + 58, top - 40, 16, bold, Color.WHITE);
			writeAt(value(data.branchName()) + (data.contactPhone() == null ? "" : "  |  " + data.contactPhone()),
					MARGIN + 58, top - 58, 8, regular, Color.WHITE);
			writeRight("PLAN DE ENTRENAMIENTO", PDRectangle.A4.getWidth() - MARGIN, top - 45,
					8, bold, Color.WHITE);
			y = top - 112;
		}

		private void infoCard(Info info, float x, float top, float width, float height) throws IOException {
			stream.setNonStrokingColor(surface);
			stream.addRect(x, top - height, width, height);
			stream.fill();
			writeAt(info.label(), x + 12, top - 16, 7, bold, muted);
			List<String> valueLines = wrap(info.value(), bold, 9, width - 24);
			float cursor = top - 32;
			for (String valueLine : valueLines.stream().limit(2).toList()) {
				writeAt(valueLine, x + 12, cursor, 9, bold, ink);
				cursor -= 11;
			}
		}

		private List<SetRow> setRows(JsonNode node) {
			List<SetRow> rows = new ArrayList<>();
			JsonNode plan = node.get("setPlan");
			if (plan != null && plan.isArray() && !plan.isEmpty()) {
				int setIndex = 1;
				for (JsonNode set : plan) {
					JsonNode segments = set.get("segments");
					if (segments != null && segments.isArray() && !segments.isEmpty()) {
						int segmentIndex = 0;
						for (JsonNode segment : segments) {
							String label = segments.size() == 1
									? "Serie " + setIndex
									: "Serie " + setIndex + (segmentIndex == 0 ? " - inicio" : " - tramo " + (segmentIndex + 1));
							rows.add(new SetRow(label, value(text(segment, "reps", "")), value(text(segment, "load", ""))));
							segmentIndex++;
						}
					}
					setIndex++;
				}
				return rows;
			}
			String sets = text(node, "sets", "");
			String reps = text(node, "reps", "");
			String load = text(node, "load", "");
			if (!sets.isBlank() || !reps.isBlank() || !load.isBlank()) {
				rows.add(new SetRow(sets.isBlank() ? "Todas" : sets + " iguales", value(reps), value(load)));
			}
			return rows;
		}

		private float writeLines(
				List<String> lines, float x, float startY, float size, PDType1Font font, Color color, float leading
		) throws IOException {
			float cursor = startY;
			for (String line : lines) {
				writeAt(line, x, cursor, size, font, color);
				cursor -= leading;
			}
			return cursor;
		}

		private void writePill(String value, float x, float centerY, float width) throws IOException {
			stream.setNonStrokingColor(primarySoft);
			stream.addRect(x, centerY - 11, width, 19);
			stream.fill();
			writeCentered(value, x + width / 2, centerY - 5, 7, bold, primaryDark);
		}

		private void writeAt(
				String value, float x, float positionY, float size, PDType1Font font, Color color
		) throws IOException {
			writeAt(stream, value, x, positionY, size, font, color);
		}

		private void writeAt(
				PDPageContentStream target, String value, float x, float positionY,
				float size, PDType1Font font, Color color
		) throws IOException {
			target.setNonStrokingColor(color);
			target.beginText();
			target.setFont(font, size);
			target.newLineAtOffset(x, positionY);
			target.showText(safePdfText(value == null ? "" : value));
			target.endText();
		}

		private void writeRight(
				String value, float right, float positionY, float size, PDType1Font font, Color color
		) throws IOException {
			writeRight(stream, value, right, positionY, size, font, color);
		}

		private void writeRight(
				PDPageContentStream target, String value, float right, float positionY,
				float size, PDType1Font font, Color color
		) throws IOException {
			String safe = safePdfText(value);
			float width = textWidth(safe, font, size);
			writeAt(target, safe, right - width, positionY, size, font, color);
		}

		private void writeCentered(
				String value, float centerX, float positionY, float size, PDType1Font font, Color color
		) throws IOException {
			String safe = safePdfText(value);
			writeAt(safe, centerX - textWidth(safe, font, size) / 2, positionY, size, font, color);
		}

		private List<String> wrap(String value, PDType1Font font, float size, float maxWidth) throws IOException {
			List<String> lines = new ArrayList<>();
			for (String paragraph : safePdfText(value == null ? "" : value).replace('\r', ' ').split("\\n")) {
				StringBuilder current = new StringBuilder();
				for (String word : paragraph.split("\\s+")) {
					String candidate = current.isEmpty() ? word : current + " " + word;
					if (textWidth(candidate, font, size) > maxWidth && !current.isEmpty()) {
						lines.add(current.toString());
						current.setLength(0);
					}
					if (!current.isEmpty()) current.append(' ');
					current.append(word);
				}
				if (!current.isEmpty()) lines.add(current.toString());
			}
			return lines.isEmpty() ? List.of("") : lines;
		}

		private String truncate(String value, PDType1Font font, float size, float maxWidth) throws IOException {
			String safe = safePdfText(value);
			if (textWidth(safe, font, size) <= maxWidth) return safe;
			String suffix = "...";
			int end = safe.length();
			while (end > 1 && textWidth(safe.substring(0, end) + suffix, font, size) > maxWidth) end--;
			return safe.substring(0, end).stripTrailing() + suffix;
		}

		private float textWidth(String value, PDType1Font font, float size) throws IOException {
			return font.getStringWidth(safePdfText(value)) / 1000f * size;
		}

		private String formatDate(LocalDate date) {
			if (date == null) return "No definida";
			return date.format(DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", Locale.forLanguageTag("es-MX")));
		}

		private String levelLabel(String level) {
			if (level == null) return "General";
			return switch (level.toUpperCase(Locale.ROOT)) {
				case "BEGINNER" -> "Principiante";
				case "INTERMEDIATE" -> "Intermedio";
				case "ADVANCED" -> "Avanzado";
				default -> "General";
			};
		}

		private String blockTypeLabel(String type) {
			if (type == null) return "BLOQUE";
			return switch (type.toUpperCase(Locale.ROOT)) {
				case "WARMUP" -> "CALENTAMIENTO";
				case "MAIN" -> "TRABAJO PRINCIPAL";
				case "SUPERSET" -> "SUPERSERIE";
				case "CIRCUIT" -> "CIRCUITO";
				case "FINISHER" -> "FINALIZADOR";
				case "COOLDOWN" -> "VUELTA A LA CALMA";
				default -> "BLOQUE";
			};
		}

		private Color blend(Color from, Color to, float ratio) {
			float safeRatio = Math.max(0, Math.min(1, ratio));
			return new Color(
					Math.round(from.getRed() + (to.getRed() - from.getRed()) * safeRatio),
					Math.round(from.getGreen() + (to.getGreen() - from.getGreen()) * safeRatio),
					Math.round(from.getBlue() + (to.getBlue() - from.getBlue()) * safeRatio)
			);
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

		private record Info(String label, String value) {}
		private record SetRow(String label, String reps, String load) {}
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
