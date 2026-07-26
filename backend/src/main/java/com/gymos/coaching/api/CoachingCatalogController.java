package com.gymos.coaching.api;

import com.gymos.coaching.application.CoachingCatalogService;
import com.gymos.coaching.application.CoachingCatalogService.AssignmentReport;
import com.gymos.coaching.application.CoachingCatalogService.CreateExerciseCommand;
import com.gymos.coaching.infrastructure.CoachingCatalogRepository.ExercisePage;
import com.gymos.coaching.infrastructure.CoachingCatalogRepository.ExerciseRecord;
import com.gymos.common.api.Correlation;
import com.gymos.common.security.GymOsPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class CoachingCatalogController {
	private final CoachingCatalogService service;

	public CoachingCatalogController(CoachingCatalogService service) {
		this.service = service;
	}

	@GetMapping("/exercises")
	ExercisePage exercises(
			@AuthenticationPrincipal GymOsPrincipal principal,
			@RequestParam(defaultValue = "") String query,
			@RequestParam(defaultValue = "") String muscleGroup,
			@RequestParam(defaultValue = "") String equipment,
			@RequestParam(defaultValue = "") String type,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "30") int size
	) {
		return service.exercises(principal, query, muscleGroup, equipment, type, page, size);
	}

	@PostMapping("/exercises")
	ExerciseRecord createExercise(
			@AuthenticationPrincipal GymOsPrincipal principal,
			@Valid @RequestBody CreateExerciseRequest request,
			HttpServletRequest servletRequest
	) {
		return service.createExercise(principal, request.toCommand(), correlationId(servletRequest));
	}

	@PatchMapping("/exercises/{exerciseId}/status")
	ExerciseRecord changeExerciseStatus(
			@AuthenticationPrincipal GymOsPrincipal principal,
			@PathVariable UUID exerciseId,
			@Valid @RequestBody ExerciseStatusRequest request,
			HttpServletRequest servletRequest
	) {
		return service.changeExerciseStatus(principal, exerciseId, request.status(), correlationId(servletRequest));
	}

	@GetMapping("/workout-assignments")
	AssignmentReport assignments(
			@AuthenticationPrincipal GymOsPrincipal principal,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@RequestParam(required = false) UUID coachId,
			@RequestParam(required = false) UUID clientId
	) {
		return service.assignmentReport(principal, from, to, coachId, clientId);
	}

	@GetMapping("/reports/coaches/workout-assignments/summary")
	AssignmentReport metrics(
			@AuthenticationPrincipal GymOsPrincipal principal,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@RequestParam(required = false) UUID coachId,
			@RequestParam(required = false) UUID clientId
	) {
		return service.assignmentReport(principal, from, to, coachId, clientId);
	}

	@GetMapping("/reports/coaches/workout-assignments/export")
	ResponseEntity<byte[]> export(
			@AuthenticationPrincipal GymOsPrincipal principal,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@RequestParam(required = false) UUID coachId,
			@RequestParam(required = false) UUID clientId
	) {
		byte[] csv = service.assignmentCsv(principal, from, to, coachId, clientId);
		return ResponseEntity.ok()
				.header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("asignaciones-rutinas.csv").build().toString())
				.contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
				.body(csv);
	}

	@GetMapping("/workout-assignments/{assignmentId}/pdf")
	ResponseEntity<byte[]> pdf(
			@AuthenticationPrincipal GymOsPrincipal principal,
			@PathVariable UUID assignmentId
	) {
		return ResponseEntity.ok()
				.header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
						.filename("rutina-" + assignmentId + ".pdf").build().toString())
				.contentType(MediaType.APPLICATION_PDF)
				.body(service.assignmentPdf(principal, assignmentId));
	}

	private String correlationId(HttpServletRequest request) {
		Object value = request.getAttribute(Correlation.ATTRIBUTE);
		return value == null ? "unknown" : value.toString();
	}

	public record CreateExerciseRequest(
			@NotBlank @Size(max = 180) String name,
			@Size(max = 4000) String description,
			@Size(max = 8000) String instructions,
			@Size(max = 80) String primaryMuscleGroup,
			@Size(max = 400) String secondaryMuscleGroups,
			@Size(max = 80) String movementPattern,
			@NotBlank @Size(max = 40) String type,
			@Size(max = 120) String equipment,
			@Size(max = 40) String suggestedLevel,
			@Size(max = 20) String laterality,
			@Size(max = 1000) String mediaUrl,
			@Size(max = 4000) String warnings
	) {
		CreateExerciseCommand toCommand() {
			return new CreateExerciseCommand(
					name, description, instructions, primaryMuscleGroup, secondaryMuscleGroups,
					movementPattern, type, equipment, suggestedLevel, laterality, mediaUrl, warnings
			);
		}
	}
	public record ExerciseStatusRequest(@NotBlank String status) {}
}
