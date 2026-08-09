package com.gymos.wellness.application;

import com.gymos.common.api.BusinessException;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.wellness.api.operations.WellnessSelfServiceOperations;
import com.gymos.wellness.application.command.CreateWellnessAppointmentCommand;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class WellnessSelfServiceOperationsAdapter implements WellnessSelfServiceOperations {
	private final WellnessService wellnessService;

	public WellnessSelfServiceOperationsAdapter(WellnessService wellnessService) {
		this.wellnessService = wellnessService;
	}

	@Override
	public List<Professional> professionals(UUID organizationId, UUID branchId) {
		return wellnessService.professionals(principal(organizationId, branchId)).stream()
				.filter(item -> "ACTIVE".equals(item.status()))
				.map(item -> new Professional(
						item.id(), item.displayName(), item.professionalType(), item.bio(),
						item.officeLocation(), item.engagementType()
				))
				.toList();
	}

	@Override
	public List<Service> services(UUID organizationId, UUID branchId, UUID professionalId) {
		return wellnessService.services(principal(organizationId, branchId), professionalId).stream()
				.filter(item -> "ACTIVE".equals(item.status()) && item.priceCents() == 0)
				.map(item -> new Service(
						item.id(), item.professionalId(), item.name(), item.serviceType(),
						item.durationMinutes(), item.priceCents(), item.currency()
				))
				.toList();
	}

	@Override
	public List<Availability> availability(
			UUID organizationId, UUID branchId, UUID professionalId, UUID serviceId, LocalDate date
	) {
		requireFreeService(organizationId, branchId, professionalId, serviceId);
		return wellnessService.availabilitySlots(
				principal(organizationId, branchId), professionalId, serviceId, date
		).stream().map(item -> new Availability(item.startsAt(), item.endsAt(), item.available())).toList();
	}

	@Override
	public Appointment createFreeAppointment(
			UUID organizationId, UUID branchId, UUID clientId, UUID professionalId,
			UUID serviceId, OffsetDateTime startsAt, String notes, String correlationId
	) {
		requireFreeService(organizationId, branchId, professionalId, serviceId);
		var appointment = wellnessService.createAppointment(
				principal(organizationId, branchId),
				new CreateWellnessAppointmentCommand(professionalId, serviceId, clientId, startsAt, notes),
				correlationId
		);
		return new Appointment(
				appointment.id(), appointment.professionalId(), appointment.professionalName(),
				appointment.serviceId(), appointment.serviceName(), appointment.priceCents(),
				appointment.startsAt(), appointment.endsAt(), appointment.status()
		);
	}

	private Service requireFreeService(UUID organizationId, UUID branchId, UUID professionalId, UUID serviceId) {
		return services(organizationId, branchId, professionalId).stream()
				.filter(item -> item.id().equals(serviceId))
				.findFirst()
				.orElseThrow(() -> new BusinessException(
						"SELF_SERVICE_WELLNESS_UNAVAILABLE",
						"El servicio no existe, tiene costo o no esta disponible en el kiosco."
				));
	}

	private GymOsPrincipal principal(UUID organizationId, UUID branchId) {
		return new GymOsPrincipal(null, organizationId, branchId, "self-service-kiosk", "Kiosco", Set.of());
	}
}
