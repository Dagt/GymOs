package com.gymos.coaching.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymos.audit.api.AuditService;
import com.gymos.coaching.application.pdf.CompactWorkoutPdfRenderer;
import com.gymos.coaching.application.pdf.DetailedWorkoutPdfRenderer;
import com.gymos.coaching.application.pdf.WorkoutPdfDocument;
import com.gymos.coaching.application.pdf.WorkoutPdfDocumentMapper;
import com.gymos.coaching.application.pdf.WorkoutPdfRenderer;
import com.gymos.coaching.application.pdf.WorkoutPdfTemplate;
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
import com.gymos.common.security.SecurityProperties;
import com.gymos.common.time.BusinessTime;
import java.io.IOException;
import java.text.Normalizer;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CoachingCatalogService {
	private static final int MAX_PAGE_SIZE = 100;
	private final CoachingCatalogRepository repository;
	private final AuditService auditService;
	private final ObjectMapper objectMapper;
	private final SecurityProperties securityProperties;
	private final WorkoutPdfDocumentMapper workoutPdfDocumentMapper;

	public CoachingCatalogService(
			CoachingCatalogRepository repository, AuditService auditService, ObjectMapper objectMapper,
			SecurityProperties securityProperties, WorkoutPdfDocumentMapper workoutPdfDocumentMapper
	) {
		this.repository = repository;
		this.auditService = auditService;
		this.objectMapper = objectMapper;
		this.securityProperties = securityProperties;
		this.workoutPdfDocumentMapper = workoutPdfDocumentMapper;
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
	public byte[] assignmentPdf(GymOsPrincipal principal, UUID assignmentId, String assetOrigin) {
		return assignmentPdf(principal, assignmentId, WorkoutPdfTemplate.COMPACT_LIGHT, assetOrigin);
	}

	@Transactional(readOnly = true)
	public byte[] assignmentPdf(GymOsPrincipal principal, UUID assignmentId, WorkoutPdfTemplate template, String assetOrigin) {
		requireBranch(principal);
		AssignmentPdfData data = repository.assignmentPdf(
				principal.organizationId(), principal.branchId(), assignmentId, workoutReadScope(principal)
		).orElseThrow(() -> new BusinessException("WORKOUT_ASSIGNMENT_NOT_FOUND", "La asignación no existe."));
		try {
			WorkoutPdfDocument doc = workoutPdfDocumentMapper.fromAssignmentData(
					principal.organizationId(), principal.branchId(), data);
			WorkoutPdfTemplate targetTemplate = template != null ? template : WorkoutPdfTemplate.COMPACT_LIGHT;
			WorkoutPdfRenderer renderer = switch (targetTemplate) {
				case COMPACT_LIGHT, COMPACT_DARK -> new CompactWorkoutPdfRenderer();
				case DETAILED -> new DetailedWorkoutPdfRenderer();
			};
			return renderer.render(doc, targetTemplate, assetOrigin);
		} catch (IOException exception) {
			throw new BusinessException("WORKOUT_PDF_FAILED", "No fue posible generar el PDF de la rutina.");
		}
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
}
