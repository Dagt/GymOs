package com.gymos.coaching.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymos.audit.api.AuditService;
import com.gymos.coaching.application.command.AssignWorkoutCommand;
import com.gymos.coaching.application.command.CreateCoachCommand;
import com.gymos.coaching.application.command.CreateWorkoutCommand;
import com.gymos.coaching.application.command.CreateWorkoutVersionCommand;
import com.gymos.coaching.infrastructure.CoachingRepository;
import com.gymos.coaching.infrastructure.CoachingRepository.AssignWorkoutData;
import com.gymos.coaching.infrastructure.CoachingRepository.CoachRecord;
import com.gymos.coaching.infrastructure.CoachingRepository.CoachProfileRow;
import com.gymos.coaching.infrastructure.CoachingRepository.CoachIdentity;
import com.gymos.coaching.infrastructure.CoachingRepository.CoachReportRecord;
import com.gymos.coaching.infrastructure.CoachingRepository.CreateCoachData;
import com.gymos.coaching.infrastructure.CoachingRepository.CreateWorkoutData;
import com.gymos.coaching.infrastructure.CoachingRepository.CreateWorkoutVersionData;
import com.gymos.coaching.infrastructure.CoachingRepository.WorkoutAssignmentRecord;
import com.gymos.coaching.infrastructure.CoachingRepository.WorkoutDetailRecord;
import com.gymos.coaching.infrastructure.CoachingRepository.WorkoutRecord;
import com.gymos.coaching.infrastructure.CoachingRepository.WorkoutSummaryRecord;
import com.gymos.coaching.infrastructure.CoachingRepository.WorkoutVersionRecord;
import com.gymos.common.api.BusinessException;
import com.gymos.common.security.GymOsPrincipal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CoachingService {
	private final CoachingRepository coachingRepository;
	private final AuditService auditService;
	private final ObjectMapper objectMapper;

	public CoachingService(CoachingRepository coachingRepository, AuditService auditService, ObjectMapper objectMapper) {
		this.coachingRepository = coachingRepository;
		this.auditService = auditService;
		this.objectMapper = objectMapper;
	}

	@Transactional(readOnly = true)
	public List<CoachRecord> coaches(GymOsPrincipal principal) {
		requireBranch(principal);
		return coachingRepository.coaches(principal.organizationId(), principal.branchId());
	}

	@Transactional(readOnly = true)
	public List<CoachProfileRecord> coachProfiles(GymOsPrincipal principal) {
		requireBranch(principal);
		List<UUID> managedBranchIds = coachingRepository.accessibleBranchIds(
				principal.organizationId(), principal.userId()
		);
		Map<UUID, CoachProfileAccumulator> profiles = new LinkedHashMap<>();
		for (CoachProfileRow row : coachingRepository.coachProfileRows(
				principal.organizationId(), managedBranchIds
		)) {
			CoachProfileAccumulator profile = profiles.computeIfAbsent(
					row.userId(),
					ignored -> new CoachProfileAccumulator(
							row.userId(), row.displayName(), row.email(), row.phone(),
							parseSpecialties(row.specialtiesJson()), row.bio()
					)
			);
			if ("ACTIVE".equals(row.status())) {
				profile.active = true;
				profile.branches.add(new CoachBranchRecord(row.branchId(), row.branchName()));
			}
		}
		return profiles.values().stream()
				.map(CoachProfileAccumulator::toRecord)
				.sorted(java.util.Comparator.comparing(CoachProfileRecord::displayName))
				.toList();
	}

	@Transactional
	public CoachProfileRecord updateCoachProfile(
			GymOsPrincipal principal, UUID coachUserId,
			UpdateCoachProfileCommand command, String correlationId
	) {
		requireBranch(principal);
		List<UUID> managedBranchIds = coachingRepository.accessibleBranchIds(
				principal.organizationId(), principal.userId()
		);
		boolean visible = coachingRepository.coachProfileRows(
				principal.organizationId(), managedBranchIds
		).stream().anyMatch(row -> row.userId().equals(coachUserId));
		if (!visible || !coachingRepository.userHasCoachRole(principal.organizationId(), coachUserId)) {
			throw new BusinessException("COACH_PROFILE_NOT_FOUND", "El perfil de coach no está disponible.");
		}
		String status = required(
				command.status(), "COACH_STATUS_REQUIRED", "El estado del coach es obligatorio."
		).toUpperCase();
		if (!List.of("ACTIVE", "INACTIVE").contains(status)) {
			throw new BusinessException("COACH_STATUS_INVALID", "Usa ACTIVE o INACTIVE.");
		}
		List<UUID> selectedBranchIds = command.branchIds() == null
				? List.of()
				: command.branchIds().stream().distinct().toList();
		if ("ACTIVE".equals(status) && selectedBranchIds.isEmpty()) {
			throw new BusinessException(
					"COACH_BRANCH_REQUIRED",
					"Un coach activo necesita al menos una sucursal."
			);
		}
		if (!managedBranchIds.containsAll(selectedBranchIds)) {
			throw new BusinessException(
					"COACH_BRANCH_OUTSIDE_SCOPE",
					"Una sucursal seleccionada está fuera de tu alcance."
			);
		}
		List<String> specialties = normalizeSpecialties(command.specialties());
		String specialtiesJson = specialtiesJson(specialties);
		String bio = blankToNull(command.bio());
		CoachIdentity identity = coachingRepository.coachIdentity(
				principal.organizationId(), coachUserId
		).orElseThrow(() -> new BusinessException(
				"COACH_PROFILE_NOT_FOUND", "El perfil de coach no está disponible."
		));
		OffsetDateTime now = OffsetDateTime.now();
		coachingRepository.updateCoachProfileDetails(
				principal.organizationId(), coachUserId, specialtiesJson, bio,
				principal.userId(), now
		);
		List<UUID> activeBranchIds = "ACTIVE".equals(status) ? selectedBranchIds : List.of();
		for (UUID branchId : activeBranchIds) {
			coachingRepository.upsertCoachBranchProfile(
					principal.organizationId(), branchId, identity, specialtiesJson, bio,
					principal.userId(), now
			);
		}
		coachingRepository.deactivateCoachBranches(
				principal.organizationId(), coachUserId, managedBranchIds, activeBranchIds,
				principal.userId(), now
		);
		coachingRepository.replaceCoachBranchAccess(
				coachUserId, managedBranchIds, activeBranchIds
		);
		auditService.record(
				principal, "COACH_PROFILE_UPDATED", "COACH_PROFILE", coachUserId,
				"BACKOFFICE", status + ":" + activeBranchIds, correlationId
		);
		return coachProfiles(principal).stream()
				.filter(profile -> profile.userId().equals(coachUserId))
				.findFirst()
				.orElseThrow(() -> new BusinessException(
						"COACH_PROFILE_NOT_FOUND", "El perfil actualizado no está disponible."
				));
	}

	@Transactional
	public CoachRecord createCoach(GymOsPrincipal principal, CreateCoachCommand command, String correlationId) {
		requireBranch(principal);
		String displayName = required(command.displayName(), "COACH_NAME_REQUIRED", "El nombre del coach es obligatorio.");
		String email = blankToNull(command.email());
		if (email != null && coachingRepository.coachEmailExists(principal.organizationId(), principal.branchId(), email)) {
			throw new BusinessException("COACH_EMAIL_ALREADY_EXISTS", "Ya existe un coach con ese correo en la sucursal.");
		}
		UUID coachId = coachingRepository.createCoach(
				principal.organizationId(),
				principal.branchId(),
				principal.userId(),
				new CreateCoachData(
						displayName,
						email,
						blankToNull(command.phone()),
						specialtiesJson(command.specialties()),
						blankToNull(command.bio())
				)
		);
		auditService.record(principal, "COACH_CREATED", "COACH", coachId, "BACKOFFICE", null, correlationId);
		return coachingRepository.coach(principal.organizationId(), principal.branchId(), coachId).orElseThrow();
	}

	@Transactional(readOnly = true)
	public List<WorkoutSummaryRecord> workouts(GymOsPrincipal principal) {
		requireBranch(principal);
		return coachingRepository.workouts(principal.organizationId(), principal.branchId(), readScope(principal));
	}

	@Transactional(readOnly = true)
	public WorkoutDetailRecord workout(GymOsPrincipal principal, UUID workoutId) {
		requireBranch(principal);
		WorkoutRecord workout = coachingRepository.workout(
				principal.organizationId(), principal.branchId(), workoutId, readScope(principal)
		)
				.orElseThrow(() -> new BusinessException("WORKOUT_NOT_FOUND", "La rutina no existe o no esta disponible."));
		return new WorkoutDetailRecord(workout, coachingRepository.workoutVersions(principal.organizationId(), workoutId));
	}

	@Transactional
	public WorkoutDetailRecord createWorkout(GymOsPrincipal principal, CreateWorkoutCommand command, String correlationId) {
		requireBranch(principal);
		UUID ownerUserId = manageScope(principal);
		UUID coachId = command.coachId();
		if (ownerUserId != null) {
			CoachRecord ownCoach = coachingRepository.coachByUser(
					principal.organizationId(), principal.branchId(), ownerUserId
			).orElseThrow(() -> new BusinessException("COACH_PROFILE_REQUIRED", "Tu usuario no tiene un perfil de coach activo en esta sucursal."));
			if (coachId != null && !coachId.equals(ownCoach.id())) {
				throw new BusinessException("WORKOUT_OUTSIDE_OWNER_SCOPE", "Solo puedes crear rutinas para tu propio perfil.");
			}
			coachId = ownCoach.id();
		}
		if (coachId != null) {
			CoachRecord coach = coachingRepository.coach(principal.organizationId(), principal.branchId(), coachId)
					.orElseThrow(() -> new BusinessException("COACH_NOT_FOUND", "El coach no existe o no esta disponible."));
			if (!"ACTIVE".equals(coach.status())) {
				throw new BusinessException("COACH_NOT_ACTIVE", "El coach no esta activo.");
			}
		}
		String title = required(command.title(), "WORKOUT_TITLE_REQUIRED", "El titulo de la rutina es obligatorio.");
		String level = normalizeLevel(command.level());
		validateWorkoutJson(command.contentJson());
		UUID workoutId = coachingRepository.createWorkout(
				principal.organizationId(),
				principal.branchId(),
				principal.userId(),
				new CreateWorkoutData(coachId, title, blankToNull(command.goal()), level)
		);
		UUID versionId = coachingRepository.createWorkoutVersion(
				principal.organizationId(),
				workoutId,
				principal.userId(),
				new CreateWorkoutVersionData(blankToNull(command.summary()), command.contentJson().trim())
		);
		persistStructure(principal.organizationId(), versionId, command.contentJson());
		auditService.record(principal, "WORKOUT_CREATED", "WORKOUT", workoutId, "BACKOFFICE", null, correlationId);
		auditService.record(principal, "WORKOUT_VERSION_CREATED", "WORKOUT_VERSION", versionId, "BACKOFFICE", null, correlationId);
		return workout(principal, workoutId);
	}

	@Transactional
	public WorkoutDetailRecord createWorkoutVersion(GymOsPrincipal principal, UUID workoutId, CreateWorkoutVersionCommand command, String correlationId) {
		requireBranch(principal);
		coachingRepository.workout(
				principal.organizationId(), principal.branchId(), workoutId, manageScope(principal)
		)
				.orElseThrow(() -> new BusinessException("WORKOUT_NOT_FOUND", "La rutina no existe o no esta disponible."));
		validateWorkoutJson(command.contentJson());
		UUID versionId = coachingRepository.createWorkoutVersion(
				principal.organizationId(),
				workoutId,
				principal.userId(),
				new CreateWorkoutVersionData(blankToNull(command.summary()), command.contentJson().trim())
		);
		persistStructure(principal.organizationId(), versionId, command.contentJson());
		auditService.record(principal, "WORKOUT_VERSION_CREATED", "WORKOUT_VERSION", versionId, "BACKOFFICE", null, correlationId);
		return workout(principal, workoutId);
	}

	private void persistStructure(UUID organizationId, UUID versionId, String contentJson) {
		try {
			var root = objectMapper.readTree(contentJson);
			var sessions = root.has("sessions") ? root.get("sessions") : root.get("days");
			if (sessions == null || !sessions.isArray()) return;
			int sessionPosition = 0;
			for (var session : sessions) {
				UUID sessionId = coachingRepository.createWorkoutSession(
						organizationId, versionId,
						session.path("name").asText("Sesión " + (sessionPosition + 1)),
						session.path("focus").asText(null), session.path("notes").asText(null),
						sessionPosition++
				);
				var blocks = session.get("blocks");
				if (blocks == null || !blocks.isArray()) continue;
				int blockPosition = 0;
				for (var block : blocks) {
					boolean structured = block.isObject() && block.has("exercises");
					UUID blockId = coachingRepository.createWorkoutBlock(
							organizationId, sessionId,
							structured ? block.path("name").asText("Bloque") : "Trabajo principal",
							structured ? block.path("type").asText("MAIN") : "MAIN",
							structured ? block.path("notes").asText(null) : null,
							blockPosition++
					);
					var exercises = structured ? block.get("exercises") : objectMapper.createArrayNode().add(block);
					int exercisePosition = 0;
					for (var exercise : exercises) {
						UUID exerciseId = null;
						if (exercise.hasNonNull("exerciseId")) {
							try { exerciseId = UUID.fromString(exercise.get("exerciseId").asText()); }
							catch (IllegalArgumentException ignored) { exerciseId = null; }
						}
						String name = exercise.path("exerciseName").asText(
								exercise.path("exercise").asText(exercise.path("name").asText("Ejercicio"))
						);
						coachingRepository.createWorkoutExercise(
								organizationId, blockId, exerciseId, name,
								exercise.path("prescription").asText(null),
								exercise.has("sets") && exercise.get("sets").canConvertToInt() ? exercise.get("sets").asInt() : null,
								exercise.path("reps").asText(null), exercise.path("load").asText(null),
								exercise.has("restSeconds") && exercise.get("restSeconds").canConvertToInt()
										? exercise.get("restSeconds").asInt() : null,
								exercise.path("tempo").asText(null), exercise.path("duration").asText(null),
								exercise.path("distance").asText(null), exercise.path("notes").asText(null),
								exercise.path("groupKey").asText(null), exercise.path("optional").asBoolean(false),
								exercisePosition++
						);
					}
				}
			}
		} catch (JsonProcessingException exception) {
			throw new BusinessException("WORKOUT_CONTENT_INVALID", "El contenido de la rutina debe ser JSON válido.");
		}
	}

	@Transactional
	public WorkoutAssignmentRecord assignWorkout(GymOsPrincipal principal, UUID workoutId, AssignWorkoutCommand command, String correlationId) {
		requireBranch(principal);
		UUID ownerUserId = manageScope(principal);
		WorkoutRecord workout = coachingRepository.workout(
				principal.organizationId(), principal.branchId(), workoutId, ownerUserId
		)
				.orElseThrow(() -> new BusinessException("WORKOUT_NOT_FOUND", "La rutina no existe o no esta disponible."));
		if (!"ACTIVE".equals(workout.status())) {
			throw new BusinessException("WORKOUT_NOT_ACTIVE", "La rutina no esta activa.");
		}
		if (!coachingRepository.clientExists(principal.organizationId(), command.clientId())) {
			throw new BusinessException("CLIENT_NOT_FOUND", "El cliente no existe o no esta activo.");
		}
		WorkoutVersionRecord version = resolveVersion(principal.organizationId(), workoutId, command.workoutVersionId());
		if (!"PUBLISHED".equals(version.status())) {
			throw new BusinessException("WORKOUT_VERSION_NOT_PUBLISHED", "Solo se pueden asignar versiones publicadas.");
		}
		if (command.startsOn() != null && command.endsOn() != null && command.endsOn().isBefore(command.startsOn())) {
			throw new BusinessException("WORKOUT_ASSIGNMENT_RANGE_INVALID", "La fecha final no puede ser anterior al inicio.");
		}
		String idempotencyKey = blankToNull(command.idempotencyKey());
		String requestHash = assignmentHash(workoutId, version.id(), command);
		if (idempotencyKey != null) {
			var existing = coachingRepository.assignmentByIdempotencyKey(principal.organizationId(), idempotencyKey);
			if (existing.isPresent()) {
				if (!requestHash.equals(existing.get().requestHash())) {
					throw new BusinessException("IDEMPOTENCY_KEY_REUSED", "La llave de idempotencia ya fue usada con otra solicitud.");
				}
				return coachingRepository.assignment(
						principal.organizationId(), principal.branchId(), existing.get().assignmentId(), ownerUserId
				)
						.orElseThrow();
			}
		}
		UUID assignmentId = coachingRepository.assignWorkout(
				principal.organizationId(),
				principal.branchId(),
				principal.userId(),
				new AssignWorkoutData(
						workoutId,
						version.id(),
						command.clientId(),
						workout.coachId(),
						LocalDate.now(),
						command.startsOn(),
						command.endsOn(),
						blankToNull(command.notes()),
						blankToNull(command.printableNotes()),
						idempotencyKey,
						requestHash
				)
		);
		auditService.record(principal, "WORKOUT_ASSIGNED", "WORKOUT_ASSIGNMENT", assignmentId, "BACKOFFICE", null, correlationId);
		return coachingRepository.assignment(
				principal.organizationId(), principal.branchId(), assignmentId, ownerUserId
		).orElseThrow();
	}

	private String assignmentHash(UUID workoutId, UUID versionId, AssignWorkoutCommand command) {
		try {
			String value = String.join("|",
					workoutId.toString(), versionId.toString(), command.clientId().toString(),
					String.valueOf(command.startsOn()), String.valueOf(command.endsOn()),
					String.valueOf(command.notes()), String.valueOf(command.printableNotes()));
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
			return java.util.HexFormat.of().formatHex(digest);
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException(exception);
		}
	}

	@Transactional(readOnly = true)
	public List<WorkoutAssignmentRecord> clientWorkouts(GymOsPrincipal principal, UUID clientId) {
		requireBranch(principal);
		if (!coachingRepository.clientExists(principal.organizationId(), clientId)) {
			throw new BusinessException("CLIENT_NOT_FOUND", "El cliente no existe o no esta activo.");
		}
		return coachingRepository.clientAssignments(
				principal.organizationId(), principal.branchId(), clientId, readScope(principal)
		);
	}

	@Transactional
	public WorkoutAssignmentRecord updateAssignmentStatus(
			GymOsPrincipal principal, UUID assignmentId, String status, String reason, String correlationId
	) {
		requireBranch(principal);
		String normalized = required(status, "WORKOUT_ASSIGNMENT_STATUS_REQUIRED", "El estado es obligatorio.").toUpperCase();
		if (!List.of("ACTIVE", "COMPLETED", "CANCELLED").contains(normalized)) {
			throw new BusinessException("WORKOUT_ASSIGNMENT_STATUS_INVALID", "Estado de asignación inválido.");
		}
		String normalizedReason = blankToNull(reason);
		if ("CANCELLED".equals(normalized) && normalizedReason == null) {
			throw new BusinessException(
					"WORKOUT_ASSIGNMENT_CANCEL_REASON_REQUIRED",
					"Indica el motivo de cancelación."
			);
		}
		UUID ownerUserId = manageScope(principal);
		coachingRepository.assignment(principal.organizationId(), principal.branchId(), assignmentId, ownerUserId)
				.orElseThrow(() -> new BusinessException("WORKOUT_ASSIGNMENT_NOT_FOUND", "La asignación no existe."));
		coachingRepository.updateAssignmentStatus(
				principal.organizationId(), principal.branchId(), assignmentId,
				normalized, normalizedReason, principal.userId()
		);
		String auditDetail = normalizedReason == null ? normalized : normalized + ": " + normalizedReason;
		auditService.record(
				principal, "WORKOUT_ASSIGNMENT_STATUS_CHANGED", "WORKOUT_ASSIGNMENT",
				assignmentId, "BACKOFFICE", auditDetail, correlationId
		);
		return coachingRepository.assignment(
				principal.organizationId(), principal.branchId(), assignmentId, ownerUserId
		).orElseThrow();
	}

	@Transactional
	public WorkoutAssignmentRecord replaceAssignment(
			GymOsPrincipal principal, UUID replacedAssignmentId, UUID workoutId,
			AssignWorkoutCommand command, String correlationId
	) {
		requireBranch(principal);
		UUID ownerUserId = manageScope(principal);
		WorkoutAssignmentRecord replaced = coachingRepository.assignment(
				principal.organizationId(), principal.branchId(), replacedAssignmentId, ownerUserId
		).orElseThrow(() -> new BusinessException("WORKOUT_ASSIGNMENT_NOT_FOUND", "La asignacion a reemplazar no existe."));
		String replacementKey = blankToNull(command.idempotencyKey());
		if ("REPLACED".equals(replaced.status()) && replacementKey != null
				&& coachingRepository.assignmentByIdempotencyKey(
						principal.organizationId(), replacementKey
				).isPresent()) {
			return assignWorkout(principal, workoutId, command, correlationId);
		}
		if (!"ACTIVE".equals(replaced.status())) {
			throw new BusinessException("WORKOUT_ASSIGNMENT_NOT_ACTIVE", "Solo se puede reemplazar una asignacion activa.");
		}
		if (!replaced.clientId().equals(command.clientId())) {
			throw new BusinessException("WORKOUT_ASSIGNMENT_CLIENT_MISMATCH", "El reemplazo debe conservar al mismo socio.");
		}
		WorkoutAssignmentRecord replacement = assignWorkout(principal, workoutId, command, correlationId);
		coachingRepository.replaceAssignment(
				principal.organizationId(), principal.branchId(), replacedAssignmentId, replacement.id()
		);
		auditService.record(
				principal, "WORKOUT_ASSIGNMENT_REPLACED", "WORKOUT_ASSIGNMENT",
				replacedAssignmentId, "BACKOFFICE", replacement.id().toString(), correlationId
		);
		return replacement;
	}

	@Transactional
	public WorkoutDetailRecord duplicateWorkout(
			GymOsPrincipal principal, UUID workoutId, String correlationId
	) {
		requireBranch(principal);
		WorkoutRecord source = coachingRepository.workout(
				principal.organizationId(), principal.branchId(), workoutId, manageScope(principal)
		)
				.orElseThrow(() -> new BusinessException("WORKOUT_NOT_FOUND", "La rutina no existe."));
		WorkoutVersionRecord version = coachingRepository.latestWorkoutVersion(principal.organizationId(), workoutId)
				.orElseThrow(() -> new BusinessException("WORKOUT_VERSION_NOT_FOUND", "La rutina no tiene versión."));
		return createWorkout(
				principal,
				new CreateWorkoutCommand(
						source.coachId(), source.title() + " (copia)", source.goal(), source.level(),
						version.summary(), version.contentJson()
				),
				correlationId
		);
	}

	@Transactional
	public WorkoutDetailRecord publishWorkout(
			GymOsPrincipal principal, UUID workoutId, String correlationId
	) {
		requireBranch(principal);
		coachingRepository.workout(
				principal.organizationId(), principal.branchId(), workoutId, manageScope(principal)
		)
				.orElseThrow(() -> new BusinessException("WORKOUT_NOT_FOUND", "La rutina no existe."));
		WorkoutVersionRecord version = coachingRepository.latestWorkoutVersion(principal.organizationId(), workoutId)
				.orElseThrow(() -> new BusinessException("WORKOUT_VERSION_NOT_FOUND", "La rutina no tiene versión."));
		if ("PUBLISHED".equals(version.status())) return workout(principal, workoutId);
		try {
			coachingRepository.publishWorkoutVersion(
					principal.organizationId(), workoutId, version.id(), principal.userId()
			);
		} catch (IllegalStateException exception) {
			throw new BusinessException("WORKOUT_VERSION_NOT_DRAFT", "La última versión no está disponible para publicar.");
		}
		auditService.record(principal, "WORKOUT_PUBLISHED", "WORKOUT", workoutId, "BACKOFFICE", null, correlationId);
		return workout(principal, workoutId);
		}

		@Transactional
		public WorkoutDetailRecord updateWorkout(GymOsPrincipal principal, UUID workoutId, CreateWorkoutCommand command, String correlationId) {
		requireBranch(principal);
		UUID ownerUserId = manageScope(principal);
		WorkoutRecord existing = coachingRepository.workout(
				principal.organizationId(), principal.branchId(), workoutId, ownerUserId
		)
				.orElseThrow(() -> new BusinessException("WORKOUT_NOT_FOUND", "La rutina no existe o no esta disponible."));

		UUID coachId = command.coachId();
		if (ownerUserId != null && (coachId == null || !coachId.equals(existing.coachId()))) {
			throw new BusinessException("WORKOUT_OUTSIDE_OWNER_SCOPE", "No puedes transferir una rutina a otro coach.");
		}
		if (coachId != null) {
			CoachRecord coach = coachingRepository.coach(principal.organizationId(), principal.branchId(), coachId)
					.orElseThrow(() -> new BusinessException("COACH_NOT_FOUND", "El coach no existe o no esta disponible."));
			if (!"ACTIVE".equals(coach.status())) {
				throw new BusinessException("COACH_NOT_ACTIVE", "El coach no esta activo.");
			}
		} else {
			coachId = existing.coachId();
		}

		validateWorkoutJson(command.contentJson());

		coachingRepository.updateWorkout(
				principal.organizationId(), principal.branchId(), workoutId, principal.userId(),
				new CreateWorkoutData(
						coachId,
						required(command.title(), "WORKOUT_TITLE_REQUIRED", "El titulo de la rutina es obligatorio."),
						blankToNull(command.goal()),
						normalizeLevel(command.level())
				)
		);

		WorkoutVersionRecord latest = coachingRepository.latestWorkoutVersion(principal.organizationId(), workoutId)
				.orElseThrow(() -> new BusinessException("WORKOUT_VERSION_NOT_FOUND", "La rutina no tiene version."));
		UUID versionId;
		if ("DRAFT".equals(latest.status())) {
			versionId = latest.id();
			coachingRepository.updateDraftVersion(
					principal.organizationId(), workoutId, versionId,
					new CreateWorkoutVersionData(blankToNull(command.summary()), command.contentJson().trim())
			);
			coachingRepository.deleteWorkoutStructure(principal.organizationId(), versionId);
		} else {
			versionId = coachingRepository.createWorkoutVersion(
					principal.organizationId(), workoutId, principal.userId(),
					new CreateWorkoutVersionData(blankToNull(command.summary()), command.contentJson().trim())
			);
		}
		persistStructure(principal.organizationId(), versionId, command.contentJson());

		auditService.record(principal, "WORKOUT_UPDATED", "WORKOUT", workoutId, "BACKOFFICE", null, correlationId);
		return workout(principal, workoutId);
		}

		@Transactional(readOnly = true)
		public List<WorkoutVersionRecord> workoutVersions(GymOsPrincipal principal, UUID workoutId) {
		requireBranch(principal);
		coachingRepository.workout(
				principal.organizationId(), principal.branchId(), workoutId, readScope(principal)
		)
				.orElseThrow(() -> new BusinessException("WORKOUT_NOT_FOUND", "La rutina no existe o no esta disponible."));
		return coachingRepository.workoutVersions(principal.organizationId(), workoutId);
		}

		@Transactional(readOnly = true)
		public WorkoutVersionRecord workoutVersion(GymOsPrincipal principal, UUID versionId) {
		requireBranch(principal);
		return coachingRepository.workoutVersionById(
				principal.organizationId(), principal.branchId(), versionId, readScope(principal)
		)
				.orElseThrow(() -> new BusinessException("WORKOUT_VERSION_NOT_FOUND", "La version de rutina no existe."));
		}

		@Transactional(readOnly = true)
		public WorkoutAssignmentRecord workoutAssignment(GymOsPrincipal principal, UUID assignmentId) {
		requireBranch(principal);
		return coachingRepository.workoutAssignmentById(
				principal.organizationId(), principal.branchId(), assignmentId, readScope(principal)
		)
				.orElseThrow(() -> new BusinessException("WORKOUT_ASSIGNMENT_NOT_FOUND", "La asignacion no existe."));
		}

	@Transactional(readOnly = true)
	public CoachReportRecord coachReport(GymOsPrincipal principal) {
		requireBranch(principal);
		return coachingRepository.coachReport(principal.organizationId(), principal.branchId());
	}

	private WorkoutVersionRecord resolveVersion(UUID organizationId, UUID workoutId, UUID versionId) {
		if (versionId != null) {
			return coachingRepository.workoutVersion(organizationId, workoutId, versionId)
					.orElseThrow(() -> new BusinessException("WORKOUT_VERSION_NOT_FOUND", "La version de rutina no existe."));
		}
		return coachingRepository.latestWorkoutVersion(organizationId, workoutId)
				.orElseThrow(() -> new BusinessException("WORKOUT_VERSION_NOT_FOUND", "La rutina no tiene una version publicada."));
	}

	private void validateWorkoutJson(String contentJson) {
		if (contentJson == null || contentJson.isBlank()) {
			throw new BusinessException("WORKOUT_CONTENT_REQUIRED", "La rutina necesita contenido estructurado.");
		}
		try {
			objectMapper.readTree(contentJson);
		} catch (JsonProcessingException exception) {
			throw new BusinessException("WORKOUT_CONTENT_INVALID", "El contenido de la rutina debe ser JSON valido.");
		}
	}

	private String specialtiesJson(List<String> specialties) {
		try {
			List<String> normalized = specialties == null ? List.of() : specialties.stream()
					.filter(value -> value != null && !value.isBlank())
					.map(String::trim)
					.distinct()
					.toList();
			return objectMapper.writeValueAsString(normalized);
		} catch (JsonProcessingException exception) {
			throw new BusinessException("COACH_SPECIALTIES_INVALID", "No fue posible guardar las especialidades.");
		}
	}

	private List<String> normalizeSpecialties(List<String> specialties) {
		if (specialties == null) return List.of();
		return specialties.stream()
				.filter(value -> value != null && !value.isBlank())
				.map(String::trim)
				.distinct()
				.limit(20)
				.toList();
	}

	private List<String> parseSpecialties(String specialtiesJson) {
		if (specialtiesJson == null || specialtiesJson.isBlank()) return List.of();
		try {
			var node = objectMapper.readTree(specialtiesJson);
			if (!node.isArray()) return List.of();
			List<String> values = new ArrayList<>();
			node.forEach(value -> {
				if (value.isTextual() && !value.asText().isBlank()) values.add(value.asText());
			});
			return List.copyOf(values);
		} catch (JsonProcessingException exception) {
			return List.of();
		}
	}

	private String normalizeLevel(String level) {
		String normalized = level == null || level.isBlank() ? "GENERAL" : level.trim().toUpperCase();
		return switch (normalized) {
			case "BEGINNER", "INTERMEDIATE", "ADVANCED", "GENERAL" -> normalized;
			default -> throw new BusinessException("WORKOUT_LEVEL_INVALID", "Nivel de rutina invalido.");
		};
	}

	private String required(String value, String code, String message) {
		if (value == null || value.isBlank()) {
			throw new BusinessException(code, message);
		}
		return value.trim();
	}

	private String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

	private void requireBranch(GymOsPrincipal principal) {
		if (principal == null) {
			throw new BusinessException("AUTHENTICATION_REQUIRED", "Debes iniciar sesion para continuar.");
		}
		if (principal.branchId() == null) {
			throw new BusinessException("BRANCH_REQUIRED", "Selecciona una sucursal para operar.");
		}
	}

	private UUID readScope(GymOsPrincipal principal) {
		if (principal.permissions().contains("workout.read.all")
				|| principal.permissions().contains("workout.manage.all")) return null;
		if (principal.permissions().contains("workout.read.own")
				|| principal.permissions().contains("workout.manage.own")) return principal.userId();
		throw new BusinessException("WORKOUT_READ_FORBIDDEN", "No tienes permiso para consultar rutinas.");
	}

	private UUID manageScope(GymOsPrincipal principal) {
		if (principal.permissions().contains("workout.manage.all")) return null;
		if (principal.permissions().contains("workout.manage.own")) return principal.userId();
		throw new BusinessException("WORKOUT_MANAGE_FORBIDDEN", "No tienes permiso para administrar rutinas.");
	}

	public record UpdateCoachProfileCommand(
			List<String> specialties, String bio, String status, List<UUID> branchIds
	) {
	}

	public record CoachBranchRecord(UUID id, String name) {
	}

	public record CoachProfileRecord(
			UUID userId, String displayName, String email, String phone,
			List<String> specialties, String bio, String status,
			List<CoachBranchRecord> branches
	) {
	}

	private static final class CoachProfileAccumulator {
		private final UUID userId;
		private final String displayName;
		private final String email;
		private final String phone;
		private final List<String> specialties;
		private final String bio;
		private final List<CoachBranchRecord> branches = new ArrayList<>();
		private boolean active;

		private CoachProfileAccumulator(
				UUID userId, String displayName, String email, String phone,
				List<String> specialties, String bio
		) {
			this.userId = userId;
			this.displayName = displayName;
			this.email = email;
			this.phone = phone;
			this.specialties = specialties;
			this.bio = bio;
		}

		private CoachProfileRecord toRecord() {
			return new CoachProfileRecord(
					userId, displayName, email, phone, specialties, bio,
					active ? "ACTIVE" : "INACTIVE", List.copyOf(branches)
			);
		}
	}
}
