package com.gymos.coaching.api;

import com.gymos.coaching.application.CoachingService;
import com.gymos.coaching.application.command.AssignWorkoutCommand;
import com.gymos.coaching.application.command.CreateCoachCommand;
import com.gymos.coaching.application.command.CreateWorkoutCommand;
import com.gymos.coaching.application.command.CreateWorkoutVersionCommand;
import com.gymos.coaching.infrastructure.CoachingRepository.CoachRecord;
import com.gymos.coaching.infrastructure.CoachingRepository.CoachReportRecord;
import com.gymos.coaching.infrastructure.CoachingRepository.WorkoutAssignmentRecord;
import com.gymos.coaching.infrastructure.CoachingRepository.WorkoutDetailRecord;
import com.gymos.coaching.infrastructure.CoachingRepository.WorkoutSummaryRecord;
import com.gymos.common.api.Correlation;
import com.gymos.common.security.GymOsPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class CoachingController {
	private final CoachingService coachingService;

	public CoachingController(CoachingService coachingService) {
		this.coachingService = coachingService;
	}

	@GetMapping("/coaches")
	List<CoachRecord> coaches(@AuthenticationPrincipal GymOsPrincipal principal) {
		return coachingService.coaches(principal);
	}

	@PostMapping("/coaches")
	CoachRecord createCoach(
			@AuthenticationPrincipal GymOsPrincipal principal,
			@Valid @RequestBody CreateCoachRequest request,
			HttpServletRequest servletRequest
	) {
		return coachingService.createCoach(principal, request.toCommand(), correlationId(servletRequest));
	}

	@GetMapping("/workouts")
	List<WorkoutSummaryRecord> workouts(@AuthenticationPrincipal GymOsPrincipal principal) {
		return coachingService.workouts(principal);
	}

	@PostMapping("/workouts")
	WorkoutDetailRecord createWorkout(
			@AuthenticationPrincipal GymOsPrincipal principal,
			@Valid @RequestBody CreateWorkoutRequest request,
			HttpServletRequest servletRequest
	) {
		return coachingService.createWorkout(principal, request.toCommand(), correlationId(servletRequest));
	}

	@GetMapping("/workouts/{workoutId}")
	WorkoutDetailRecord workout(@AuthenticationPrincipal GymOsPrincipal principal, @PathVariable UUID workoutId) {
		return coachingService.workout(principal, workoutId);
	}

	@PostMapping("/workouts/{workoutId}/versions")
	WorkoutDetailRecord createWorkoutVersion(
			@AuthenticationPrincipal GymOsPrincipal principal,
			@PathVariable UUID workoutId,
			@Valid @RequestBody CreateWorkoutVersionRequest request,
			HttpServletRequest servletRequest
	) {
		return coachingService.createWorkoutVersion(principal, workoutId, request.toCommand(), correlationId(servletRequest));
	}

	@PostMapping("/workouts/{workoutId}/assign")
	WorkoutAssignmentRecord assignWorkout(
			@AuthenticationPrincipal GymOsPrincipal principal,
			@PathVariable UUID workoutId,
			@Valid @RequestBody AssignWorkoutRequest request,
			HttpServletRequest servletRequest
	) {
		return coachingService.assignWorkout(principal, workoutId, request.toCommand(), correlationId(servletRequest));
	}

	@PostMapping("/workout-assignments")
	WorkoutAssignmentRecord createAssignment(
			@AuthenticationPrincipal GymOsPrincipal principal,
			@Valid @RequestBody CreateAssignmentRequest request,
			HttpServletRequest servletRequest
	) {
		return coachingService.assignWorkout(
				principal, request.workoutId(),
				new AssignWorkoutCommand(
						request.clientId(), request.workoutVersionId(), request.startsOn(), request.endsOn(),
						request.privateNotes(), request.printableNotes(), request.idempotencyKey()
				),
				correlationId(servletRequest)
		);
	}

	@PostMapping("/workouts/{workoutId}/duplicate")
	WorkoutDetailRecord duplicateWorkout(
			@AuthenticationPrincipal GymOsPrincipal principal,
			@PathVariable UUID workoutId,
			HttpServletRequest servletRequest
	) {
		return coachingService.duplicateWorkout(principal, workoutId, correlationId(servletRequest));
	}

	@PostMapping("/workouts/{workoutId}/publish")
	WorkoutDetailRecord publishWorkout(
			@AuthenticationPrincipal GymOsPrincipal principal,
			@PathVariable UUID workoutId,
			HttpServletRequest servletRequest
	) {
		return coachingService.publishWorkout(principal, workoutId, correlationId(servletRequest));
	}

	@PatchMapping("/workout-assignments/{assignmentId}/status")
	WorkoutAssignmentRecord updateAssignmentStatus(
			@AuthenticationPrincipal GymOsPrincipal principal,
			@PathVariable UUID assignmentId,
			@Valid @RequestBody AssignmentStatusRequest request,
			HttpServletRequest servletRequest
	) {
		return coachingService.updateAssignmentStatus(principal, assignmentId, request.status(), correlationId(servletRequest));
	}

	@GetMapping("/clients/{clientId}/workouts")
	List<WorkoutAssignmentRecord> clientWorkouts(@AuthenticationPrincipal GymOsPrincipal principal, @PathVariable UUID clientId) {
		return coachingService.clientWorkouts(principal, clientId);
	}

	@GetMapping("/reports/coaches")
	CoachReportRecord coachReport(@AuthenticationPrincipal GymOsPrincipal principal) {
		return coachingService.coachReport(principal);
	}

	private String correlationId(HttpServletRequest request) {
		Object value = request.getAttribute(Correlation.ATTRIBUTE);
		return value == null ? "unknown" : value.toString();
	}

	public record CreateCoachRequest(
			@NotBlank @Size(max = 160) String displayName,
			@Size(max = 180) String email,
			@Size(max = 32) String phone,
			List<@Size(max = 80) String> specialties,
			@Size(max = 4000) String bio
	) {
		private CreateCoachCommand toCommand() {
			return new CreateCoachCommand(displayName, email, phone, specialties, bio);
		}
	}

	public record CreateWorkoutRequest(
			UUID coachId,
			@NotBlank @Size(max = 180) String title,
			@Size(max = 160) String goal,
			@NotBlank @Size(max = 40) String level,
			@Size(max = 2000) String summary,
			@NotBlank String contentJson
	) {
		private CreateWorkoutCommand toCommand() {
			return new CreateWorkoutCommand(coachId, title, goal, level, summary, contentJson);
		}
	}

	public record CreateWorkoutVersionRequest(
			@Size(max = 2000) String summary,
			@NotBlank String contentJson
	) {
		private CreateWorkoutVersionCommand toCommand() {
			return new CreateWorkoutVersionCommand(summary, contentJson);
		}
	}

	public record AssignWorkoutRequest(
			@NotNull UUID clientId,
			UUID workoutVersionId,
			LocalDate startsOn,
			LocalDate endsOn,
			@Size(max = 2000) String notes,
			@Size(max = 4000) String printableNotes,
			@Size(max = 160) String idempotencyKey
	) {
		private AssignWorkoutCommand toCommand() {
			return new AssignWorkoutCommand(clientId, workoutVersionId, startsOn, endsOn, notes, printableNotes, idempotencyKey);
		}
	}

	public record CreateAssignmentRequest(
			@NotNull UUID workoutId,
			@NotNull UUID clientId,
			UUID workoutVersionId,
			LocalDate startsOn,
			LocalDate endsOn,
			@Size(max = 2000) String privateNotes,
			@Size(max = 4000) String printableNotes,
			@NotBlank @Size(max = 160) String idempotencyKey
	) {}
	public record AssignmentStatusRequest(@NotBlank String status) {}
}
