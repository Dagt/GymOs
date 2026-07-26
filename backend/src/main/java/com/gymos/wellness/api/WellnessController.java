package com.gymos.wellness.api;

import com.gymos.common.api.Correlation;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.wellness.application.WellnessService;
import com.gymos.wellness.application.command.CreateProfessionalAvailabilityCommand;
import com.gymos.wellness.application.command.CreateProfessionalCommand;
import com.gymos.wellness.application.command.CreateProfessionalServiceCommand;
import com.gymos.wellness.infrastructure.WellnessRepository.AvailabilityRecord;
import com.gymos.wellness.infrastructure.WellnessRepository.ProfessionalRecord;
import com.gymos.wellness.infrastructure.WellnessRepository.ProfessionalServiceRecord;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class WellnessController {
	private final WellnessService wellnessService;

	public WellnessController(WellnessService wellnessService) {
		this.wellnessService = wellnessService;
	}

	@GetMapping("/professionals")
	List<ProfessionalRecord> professionals(@AuthenticationPrincipal GymOsPrincipal principal) {
		return wellnessService.professionals(principal);
	}

	@PostMapping("/professionals")
	ProfessionalRecord createProfessional(
			@AuthenticationPrincipal GymOsPrincipal principal,
			@Valid @RequestBody CreateProfessionalRequest request,
			HttpServletRequest servletRequest
	) {
		return wellnessService.createProfessional(principal, request.toCommand(), correlationId(servletRequest));
	}

	@GetMapping("/professionals/{professionalId}/services")
	List<ProfessionalServiceRecord> services(@AuthenticationPrincipal GymOsPrincipal principal, @PathVariable UUID professionalId) {
		return wellnessService.services(principal, professionalId);
	}

	@PostMapping("/professionals/{professionalId}/services")
	ProfessionalServiceRecord createService(
			@AuthenticationPrincipal GymOsPrincipal principal,
			@PathVariable UUID professionalId,
			@Valid @RequestBody CreateServiceRequest request,
			HttpServletRequest servletRequest
	) {
		return wellnessService.createService(principal, professionalId, request.toCommand(), correlationId(servletRequest));
	}

	@GetMapping("/professionals/{professionalId}/availability")
	List<AvailabilityRecord> availability(@AuthenticationPrincipal GymOsPrincipal principal, @PathVariable UUID professionalId) {
		return wellnessService.availability(principal, professionalId);
	}

	@PostMapping("/professionals/{professionalId}/availability")
	AvailabilityRecord createAvailability(
			@AuthenticationPrincipal GymOsPrincipal principal,
			@PathVariable UUID professionalId,
			@Valid @RequestBody CreateAvailabilityRequest request,
			HttpServletRequest servletRequest
	) {
		return wellnessService.createAvailability(principal, professionalId, request.toCommand(), correlationId(servletRequest));
	}

	private String correlationId(HttpServletRequest request) {
		Object value = request.getAttribute(Correlation.ATTRIBUTE);
		return value == null ? "unknown" : value.toString();
	}

	public record CreateProfessionalRequest(
			@NotBlank @Size(max = 160) String displayName,
			@NotBlank @Size(max = 60) String professionalType,
			@Size(max = 180) String email,
			@Size(max = 32) String phone,
			@Size(max = 4000) String bio
	) {
		private CreateProfessionalCommand toCommand() {
			return new CreateProfessionalCommand(displayName, professionalType, email, phone, bio);
		}
	}

	public record CreateServiceRequest(
			@NotBlank @Size(max = 160) String name,
			@NotBlank @Size(max = 60) String serviceType,
			@Min(15) int durationMinutes,
			@Min(0) long priceCents,
			@NotBlank @Size(min = 3, max = 3) String currency
	) {
		private CreateProfessionalServiceCommand toCommand() {
			return new CreateProfessionalServiceCommand(name, serviceType, durationMinutes, priceCents, currency);
		}
	}

	public record CreateAvailabilityRequest(
			@Min(1) int dayOfWeek,
			@NotNull LocalTime opensAt,
			@NotNull LocalTime closesAt
	) {
		private CreateProfessionalAvailabilityCommand toCommand() {
			return new CreateProfessionalAvailabilityCommand(dayOfWeek, opensAt, closesAt);
		}
	}
}
