package com.gymos.wellness.api;

import com.gymos.common.api.Correlation;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.wellness.application.WellnessService;
import com.gymos.wellness.application.WellnessService.AvailabilitySlotRecord;
import com.gymos.wellness.application.command.ConfirmWellnessAppointmentCommand;
import com.gymos.wellness.application.command.CreateProfessionalAvailabilityCommand;
import com.gymos.wellness.application.command.CreateProfessionalCommand;
import com.gymos.wellness.application.command.CreateProfessionalServiceCommand;
import com.gymos.wellness.application.command.CreateWellnessAppointmentCommand;
import com.gymos.wellness.application.command.UpdateProfessionalCommand;
import com.gymos.wellness.application.command.UpdateProfessionalServiceCommand;
import com.gymos.wellness.infrastructure.WellnessRepository.AppointmentRecord;
import com.gymos.wellness.infrastructure.WellnessRepository.AvailabilityRecord;
import com.gymos.wellness.infrastructure.WellnessRepository.ProfessionalRecord;
import com.gymos.wellness.infrastructure.WellnessRepository.ProfessionalServiceRecord;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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

	@PutMapping("/professionals/{professionalId}")
	ProfessionalRecord updateProfessional(
			@AuthenticationPrincipal GymOsPrincipal principal,
			@PathVariable UUID professionalId,
			@Valid @RequestBody UpdateProfessionalRequest request,
			HttpServletRequest servletRequest
	) {
		return wellnessService.updateProfessional(principal, professionalId, request.toCommand(), correlationId(servletRequest));
	}

	@DeleteMapping("/professionals/{professionalId}")
	void deleteProfessional(
			@AuthenticationPrincipal GymOsPrincipal principal,
			@PathVariable UUID professionalId,
			HttpServletRequest servletRequest
	) {
		wellnessService.deleteProfessional(principal, professionalId, correlationId(servletRequest));
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

	@PutMapping("/professionals/{professionalId}/services/{serviceId}")
	ProfessionalServiceRecord updateService(
			@AuthenticationPrincipal GymOsPrincipal principal,
			@PathVariable UUID professionalId,
			@PathVariable UUID serviceId,
			@Valid @RequestBody UpdateServiceRequest request,
			HttpServletRequest servletRequest
	) {
		return wellnessService.updateService(principal, professionalId, serviceId, request.toCommand(), correlationId(servletRequest));
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

	@GetMapping("/professionals/{professionalId}/availability-slots")
	List<AvailabilitySlotRecord> availabilitySlots(
			@AuthenticationPrincipal GymOsPrincipal principal,
			@PathVariable UUID professionalId,
			@RequestParam(required = false) UUID serviceId,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
	) {
		return wellnessService.availabilitySlots(principal, professionalId, serviceId, date);
	}

	@PostMapping("/wellness/appointments")
	AppointmentRecord createAppointment(
			@AuthenticationPrincipal GymOsPrincipal principal,
			@Valid @RequestBody CreateAppointmentRequest request,
			HttpServletRequest servletRequest
	) {
		return wellnessService.createAppointment(principal, request.toCommand(), correlationId(servletRequest));
	}

	@GetMapping("/wellness/appointments")
	List<AppointmentRecord> appointments(
			@AuthenticationPrincipal GymOsPrincipal principal,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
			@RequestParam(required = false) UUID professionalId,
			@RequestParam(required = false) String status
	) {
		return wellnessService.appointments(principal, date, professionalId, status);
	}

	@PostMapping("/wellness/appointments/{appointmentId}/confirm")
	AppointmentRecord confirmAppointment(
			@AuthenticationPrincipal GymOsPrincipal principal,
			@PathVariable UUID appointmentId,
			@RequestBody(required = false) ConfirmAppointmentRequest request,
			HttpServletRequest servletRequest
	) {
		ConfirmWellnessAppointmentCommand command = request != null ? request.toCommand() : null;
		return wellnessService.confirmAppointment(principal, appointmentId, command, correlationId(servletRequest));
	}

	@PostMapping("/wellness/appointments/{appointmentId}/complete")
	AppointmentRecord completeAppointment(
			@AuthenticationPrincipal GymOsPrincipal principal,
			@PathVariable UUID appointmentId,
			HttpServletRequest servletRequest
	) {
		return wellnessService.completeAppointment(principal, appointmentId, correlationId(servletRequest));
	}

	@PostMapping("/wellness/appointments/{appointmentId}/cancel")
	AppointmentRecord cancelAppointment(
			@AuthenticationPrincipal GymOsPrincipal principal,
			@PathVariable UUID appointmentId,
			@RequestBody(required = false) CancelAppointmentRequest request,
			HttpServletRequest servletRequest
	) {
		String reason = request == null ? null : request.reason();
		return wellnessService.cancelAppointment(principal, appointmentId, reason, correlationId(servletRequest));
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
			@Size(max = 4000) String bio,
			@Size(max = 255) String officeLocation,
			@Size(max = 16) String engagementType
	) {
		private CreateProfessionalCommand toCommand() {
			return new CreateProfessionalCommand(displayName, professionalType, email, phone, bio, officeLocation, engagementType);
		}
	}

	public record UpdateProfessionalRequest(
			@NotBlank @Size(max = 160) String displayName,
			@NotBlank @Size(max = 60) String professionalType,
			@Size(max = 180) String email,
			@Size(max = 32) String phone,
			@Size(max = 4000) String bio,
			@Size(max = 255) String officeLocation,
			@Size(max = 16) String engagementType,
			String status
	) {
		private UpdateProfessionalCommand toCommand() {
			return new UpdateProfessionalCommand(displayName, professionalType, email, phone, bio, officeLocation, engagementType, status);
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

	public record UpdateServiceRequest(
			@NotBlank @Size(max = 160) String name,
			@NotBlank @Size(max = 60) String serviceType,
			@Min(15) int durationMinutes,
			@Min(0) long priceCents,
			@NotBlank @Size(min = 3, max = 3) String currency,
			String status
	) {
		private UpdateProfessionalServiceCommand toCommand() {
			return new UpdateProfessionalServiceCommand(name, serviceType, durationMinutes, priceCents, currency, status);
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

	public record CreateAppointmentRequest(
			@NotNull UUID professionalId,
			UUID serviceId,
			@NotNull UUID clientId,
			@NotNull OffsetDateTime startsAt,
			@Size(max = 1000) String notes
	) {
		private CreateWellnessAppointmentCommand toCommand() {
			return new CreateWellnessAppointmentCommand(professionalId, serviceId, clientId, startsAt, notes);
		}
	}

	public record ConfirmAppointmentRequest(
			OffsetDateTime startsAt,
			@Size(max = 1000) String notes
	) {
		private ConfirmWellnessAppointmentCommand toCommand() {
			return new ConfirmWellnessAppointmentCommand(startsAt, notes);
		}
	}

	public record CancelAppointmentRequest(
			@Size(max = 500) String reason
	) {
	}
}
