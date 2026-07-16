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
import java.util.List;
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
		return coachingRepository.workouts(principal.organizationId(), principal.branchId());
	}

	@Transactional(readOnly = true)
	public WorkoutDetailRecord workout(GymOsPrincipal principal, UUID workoutId) {
		requireBranch(principal);
		WorkoutRecord workout = coachingRepository.workout(principal.organizationId(), principal.branchId(), workoutId)
				.orElseThrow(() -> new BusinessException("WORKOUT_NOT_FOUND", "La rutina no existe o no esta disponible."));
		return new WorkoutDetailRecord(workout, coachingRepository.workoutVersions(principal.organizationId(), workoutId));
	}

	@Transactional
	public WorkoutDetailRecord createWorkout(GymOsPrincipal principal, CreateWorkoutCommand command, String correlationId) {
		requireBranch(principal);
		UUID coachId = command.coachId();
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
		auditService.record(principal, "WORKOUT_CREATED", "WORKOUT", workoutId, "BACKOFFICE", null, correlationId);
		auditService.record(principal, "WORKOUT_VERSION_CREATED", "WORKOUT_VERSION", versionId, "BACKOFFICE", null, correlationId);
		return workout(principal, workoutId);
	}

	@Transactional
	public WorkoutDetailRecord createWorkoutVersion(GymOsPrincipal principal, UUID workoutId, CreateWorkoutVersionCommand command, String correlationId) {
		requireBranch(principal);
		coachingRepository.workout(principal.organizationId(), principal.branchId(), workoutId)
				.orElseThrow(() -> new BusinessException("WORKOUT_NOT_FOUND", "La rutina no existe o no esta disponible."));
		validateWorkoutJson(command.contentJson());
		UUID versionId = coachingRepository.createWorkoutVersion(
				principal.organizationId(),
				workoutId,
				principal.userId(),
				new CreateWorkoutVersionData(blankToNull(command.summary()), command.contentJson().trim())
		);
		auditService.record(principal, "WORKOUT_VERSION_CREATED", "WORKOUT_VERSION", versionId, "BACKOFFICE", null, correlationId);
		return workout(principal, workoutId);
	}

	@Transactional
	public WorkoutAssignmentRecord assignWorkout(GymOsPrincipal principal, UUID workoutId, AssignWorkoutCommand command, String correlationId) {
		requireBranch(principal);
		WorkoutRecord workout = coachingRepository.workout(principal.organizationId(), principal.branchId(), workoutId)
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
						blankToNull(command.notes())
				)
		);
		auditService.record(principal, "WORKOUT_ASSIGNED", "WORKOUT_ASSIGNMENT", assignmentId, "BACKOFFICE", null, correlationId);
		return coachingRepository.assignment(principal.organizationId(), principal.branchId(), assignmentId).orElseThrow();
	}

	@Transactional(readOnly = true)
	public List<WorkoutAssignmentRecord> clientWorkouts(GymOsPrincipal principal, UUID clientId) {
		requireBranch(principal);
		if (!coachingRepository.clientExists(principal.organizationId(), clientId)) {
			throw new BusinessException("CLIENT_NOT_FOUND", "El cliente no existe o no esta activo.");
		}
		return coachingRepository.clientAssignments(principal.organizationId(), principal.branchId(), clientId);
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
}
