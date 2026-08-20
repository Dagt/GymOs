package com.gymos.selfservice.api;

import com.gymos.common.api.Correlation;
import com.gymos.selfservice.application.SelfServiceCapability;
import com.gymos.selfservice.application.SelfServiceService;
import com.gymos.wellness.api.operations.WellnessSelfServiceOperations;
import com.gymos.wellness.api.operations.WellnessSelfServiceOperations.Appointment;
import com.gymos.wellness.api.operations.WellnessSelfServiceOperations.Availability;
import com.gymos.wellness.api.operations.WellnessSelfServiceOperations.Professional;
import com.gymos.wellness.api.operations.WellnessSelfServiceOperations.Service;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/self-service/sessions/{sessionId}/wellness")
public class SelfServiceWellnessController {
	private final SelfServiceService selfService;
	private final WellnessSelfServiceOperations wellnessOperations;

	public SelfServiceWellnessController(
			SelfServiceService selfService,
			WellnessSelfServiceOperations wellnessOperations
	) {
		this.selfService = selfService;
		this.wellnessOperations = wellnessOperations;
	}

	@GetMapping("/professionals")
	List<Professional> professionals(
			@PathVariable UUID sessionId,
			@RequestHeader(name = "X-Self-Service-Session-Token", required = false) String sessionToken
	) {
		var session = verified(sessionId, sessionToken);
		return wellnessOperations.professionals(session.organizationId(), session.branchId());
	}

	@GetMapping("/professionals/{professionalId}/services")
	List<Service> services(
			@PathVariable UUID sessionId,
			@PathVariable UUID professionalId,
			@RequestHeader(name = "X-Self-Service-Session-Token", required = false) String sessionToken
	) {
		var session = verified(sessionId, sessionToken);
		return wellnessOperations.services(session.organizationId(), session.branchId(), professionalId);
	}

	@GetMapping("/professionals/{professionalId}/availability")
	List<Availability> availability(
			@PathVariable UUID sessionId,
			@PathVariable UUID professionalId,
			@RequestParam UUID serviceId,
			@RequestParam LocalDate date,
			@RequestHeader(name = "X-Self-Service-Session-Token", required = false) String sessionToken
	) {
		var session = verified(sessionId, sessionToken);
		return wellnessOperations.availability(
				session.organizationId(), session.branchId(), professionalId, serviceId, date
		);
	}

	@PostMapping("/appointments")
	Appointment createAppointment(
			@PathVariable UUID sessionId,
			@Valid @RequestBody CreateAppointmentRequest request,
			@RequestHeader(name = "X-Self-Service-Session-Token", required = false) String sessionToken,
			HttpServletRequest servletRequest
	) {
		var session = verified(sessionId, sessionToken);
		return wellnessOperations.createFreeAppointment(
				session.organizationId(), session.branchId(), session.clientId(),
				request.professionalId(), request.serviceId(), request.startsAt(), request.notes(),
				correlationId(servletRequest)
		);
	}

	private com.gymos.selfservice.infrastructure.SelfServiceRepository.SessionRecord verified(
			UUID sessionId, String sessionToken
	) {
		return selfService.verifiedSession(sessionId, sessionToken, SelfServiceCapability.WELLNESS_BOOKING);
	}

	private String correlationId(HttpServletRequest request) {
		Object value = request.getAttribute(Correlation.ATTRIBUTE);
		return value == null ? "unknown" : value.toString();
	}

	public record CreateAppointmentRequest(
			@NotNull UUID professionalId,
			@NotNull UUID serviceId,
			@NotNull OffsetDateTime startsAt,
			@Size(max = 2000) String notes
	) {}
}
