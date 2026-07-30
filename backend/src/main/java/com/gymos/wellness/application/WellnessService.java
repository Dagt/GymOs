package com.gymos.wellness.application;

import com.gymos.audit.api.AuditService;
import com.gymos.common.api.BusinessException;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.common.time.BusinessTime;
import com.gymos.wellness.application.command.ConfirmWellnessAppointmentCommand;
import com.gymos.wellness.application.command.CreateProfessionalAvailabilityCommand;
import com.gymos.wellness.application.command.CreateProfessionalCommand;
import com.gymos.wellness.application.command.CreateProfessionalServiceCommand;
import com.gymos.wellness.application.command.CreateWellnessAppointmentCommand;
import com.gymos.wellness.application.command.UpdateProfessionalCommand;
import com.gymos.wellness.application.command.UpdateProfessionalServiceCommand;
import com.gymos.wellness.infrastructure.WellnessRepository;
import com.gymos.wellness.infrastructure.WellnessRepository.AppointmentRecord;
import com.gymos.wellness.infrastructure.WellnessRepository.AvailabilityRecord;
import com.gymos.wellness.infrastructure.WellnessRepository.CreateAppointmentData;
import com.gymos.wellness.infrastructure.WellnessRepository.CreateAvailabilityData;
import com.gymos.wellness.infrastructure.WellnessRepository.CreateProfessionalData;
import com.gymos.wellness.infrastructure.WellnessRepository.CreateServiceData;
import com.gymos.wellness.infrastructure.WellnessRepository.ProfessionalRecord;
import com.gymos.wellness.infrastructure.WellnessRepository.ProfessionalServiceRecord;
import com.gymos.wellness.infrastructure.WellnessRepository.UpdateProfessionalData;
import com.gymos.wellness.infrastructure.WellnessRepository.UpdateServiceData;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WellnessService {
	private final WellnessRepository wellnessRepository;
	private final AuditService auditService;

	public WellnessService(WellnessRepository wellnessRepository, AuditService auditService) {
		this.wellnessRepository = wellnessRepository;
		this.auditService = auditService;
	}

	@Transactional(readOnly = true)
	public List<ProfessionalRecord> professionals(GymOsPrincipal principal) {
		requireBranch(principal);
		return wellnessRepository.professionals(principal.organizationId(), principal.branchId());
	}

	@Transactional
	public ProfessionalRecord createProfessional(GymOsPrincipal principal, CreateProfessionalCommand command, String correlationId) {
		requireBranch(principal);
		UUID professionalId = wellnessRepository.createProfessional(
				principal.organizationId(),
				principal.branchId(),
				principal.userId(),
				new CreateProfessionalData(
						required(command.displayName(), "PROFESSIONAL_NAME_REQUIRED", "El nombre del profesional es obligatorio."),
						required(command.professionalType(), "PROFESSIONAL_TYPE_REQUIRED", "El tipo de profesional es obligatorio.").toUpperCase(),
						blankToNull(command.email()),
						blankToNull(command.phone()),
						blankToNull(command.bio()),
						blankToNull(command.officeLocation()),
						resolveEngagementType(command.engagementType(), "STAFF")
				)
		);
		auditService.record(principal, "PROFESSIONAL_CREATED", "PROFESSIONAL", professionalId, "BACKOFFICE", null, correlationId);
		return wellnessRepository.professional(principal.organizationId(), principal.branchId(), professionalId).orElseThrow();
	}

	@Transactional
	public ProfessionalRecord updateProfessional(GymOsPrincipal principal, UUID professionalId, UpdateProfessionalCommand command, String correlationId) {
		requireBranch(principal);
		ProfessionalRecord existing = wellnessRepository.professional(principal.organizationId(), principal.branchId(), professionalId)
				.orElseThrow(() -> new BusinessException("PROFESSIONAL_NOT_FOUND", "El profesional no existe o no esta disponible."));

		String status = (command.status() == null || command.status().isBlank()) ? "ACTIVE" : command.status().trim().toUpperCase();
		if (!List.of("ACTIVE", "INACTIVE").contains(status)) {
			throw new BusinessException("PROFESSIONAL_STATUS_INVALID", "Estado de profesional invalido.");
		}

		wellnessRepository.updateProfessional(
				principal.organizationId(),
				principal.branchId(),
				professionalId,
				principal.userId(),
				new UpdateProfessionalData(
						required(command.displayName(), "PROFESSIONAL_NAME_REQUIRED", "El nombre del profesional es obligatorio."),
						required(command.professionalType(), "PROFESSIONAL_TYPE_REQUIRED", "El tipo de profesional es obligatorio.").toUpperCase(),
						blankToNull(command.email()),
						blankToNull(command.phone()),
						blankToNull(command.bio()),
						blankToNull(command.officeLocation()),
						resolveEngagementType(command.engagementType(), existing.engagementType()),
						status
				)
		);
		auditService.record(principal, "PROFESSIONAL_UPDATED", "PROFESSIONAL", professionalId, "BACKOFFICE", null, correlationId);
		return wellnessRepository.professional(principal.organizationId(), principal.branchId(), professionalId).orElseThrow();
	}

	@Transactional
	public void deleteProfessional(GymOsPrincipal principal, UUID professionalId, String correlationId) {
		requireBranch(principal);
		wellnessRepository.professional(principal.organizationId(), principal.branchId(), professionalId)
				.orElseThrow(() -> new BusinessException("PROFESSIONAL_NOT_FOUND", "El profesional no existe o ya fue eliminado."));

		wellnessRepository.deleteProfessional(principal.organizationId(), principal.branchId(), professionalId);
		auditService.record(principal, "PROFESSIONAL_DELETED", "PROFESSIONAL", professionalId, "BACKOFFICE", null, correlationId);
	}

	@Transactional
	public ProfessionalServiceRecord createService(GymOsPrincipal principal, UUID professionalId, CreateProfessionalServiceCommand command, String correlationId) {
		requireActiveProfessional(principal, professionalId);
		if (command.durationMinutes() < 15 || command.durationMinutes() > 480) {
			throw new BusinessException("PROFESSIONAL_SERVICE_DURATION_INVALID", "La duracion debe estar entre 15 y 480 minutos.");
		}
		if (command.priceCents() < 0) {
			throw new BusinessException("PROFESSIONAL_SERVICE_PRICE_INVALID", "El precio no puede ser negativo.");
		}
		UUID serviceId = wellnessRepository.createService(
				principal.organizationId(),
				principal.branchId(),
				professionalId,
				principal.userId(),
				new CreateServiceData(
						required(command.name(), "PROFESSIONAL_SERVICE_NAME_REQUIRED", "El servicio necesita nombre."),
						required(command.serviceType(), "PROFESSIONAL_SERVICE_TYPE_REQUIRED", "El servicio necesita tipo.").toUpperCase(),
						command.durationMinutes(),
						command.priceCents(),
						(command.currency() == null || command.currency().isBlank() ? "MXN" : command.currency().trim().toUpperCase())
				)
		);
		auditService.record(principal, "PROFESSIONAL_SERVICE_CREATED", "PROFESSIONAL_SERVICE", serviceId, "BACKOFFICE", null, correlationId);
		return wellnessRepository.services(principal.organizationId(), principal.branchId(), professionalId).stream()
				.filter(service -> service.id().equals(serviceId))
				.findFirst()
				.orElseThrow();
	}

	@Transactional
	public ProfessionalServiceRecord updateService(GymOsPrincipal principal, UUID professionalId, UUID serviceId, UpdateProfessionalServiceCommand command, String correlationId) {
		requireActiveProfessional(principal, professionalId);
		wellnessRepository.service(principal.organizationId(), principal.branchId(), professionalId, serviceId)
				.orElseThrow(() -> new BusinessException("PROFESSIONAL_SERVICE_NOT_FOUND", "El servicio no existe o no pertenece al profesional."));

		if (command.durationMinutes() < 15 || command.durationMinutes() > 480) {
			throw new BusinessException("PROFESSIONAL_SERVICE_DURATION_INVALID", "La duracion debe estar entre 15 y 480 minutos.");
		}
		if (command.priceCents() < 0) {
			throw new BusinessException("PROFESSIONAL_SERVICE_PRICE_INVALID", "El precio no puede ser negativo.");
		}
		String status = (command.status() == null || command.status().isBlank()) ? "ACTIVE" : command.status().trim().toUpperCase();

		wellnessRepository.updateService(
				principal.organizationId(),
				principal.branchId(),
				professionalId,
				serviceId,
				principal.userId(),
				new UpdateServiceData(
						required(command.name(), "PROFESSIONAL_SERVICE_NAME_REQUIRED", "El servicio necesita nombre."),
						required(command.serviceType(), "PROFESSIONAL_SERVICE_TYPE_REQUIRED", "El servicio necesita tipo.").toUpperCase(),
						command.durationMinutes(),
						command.priceCents(),
						(command.currency() == null || command.currency().isBlank() ? "MXN" : command.currency().trim().toUpperCase()),
						status
				)
		);
		auditService.record(principal, "PROFESSIONAL_SERVICE_UPDATED", "PROFESSIONAL_SERVICE", serviceId, "BACKOFFICE", null, correlationId);
		return wellnessRepository.service(principal.organizationId(), principal.branchId(), professionalId, serviceId).orElseThrow();
	}

	@Transactional(readOnly = true)
	public List<ProfessionalServiceRecord> services(GymOsPrincipal principal, UUID professionalId) {
		requireActiveProfessional(principal, professionalId);
		return wellnessRepository.services(principal.organizationId(), principal.branchId(), professionalId);
	}

	@Transactional(readOnly = true)
	public List<AvailabilityRecord> availability(GymOsPrincipal principal, UUID professionalId) {
		requireActiveProfessional(principal, professionalId);
		return wellnessRepository.availability(principal.organizationId(), principal.branchId(), professionalId);
	}

	@Transactional
	public AvailabilityRecord createAvailability(GymOsPrincipal principal, UUID professionalId, CreateProfessionalAvailabilityCommand command, String correlationId) {
		ProfessionalRecord professional = requireActiveProfessional(principal, professionalId);
		if ("EXTERNAL".equals(professional.engagementType())) {
			throw new BusinessException(
					"EXTERNAL_PROFESSIONAL_AVAILABILITY_NOT_ALLOWED",
					"Los profesionales externos no administran horarios internos."
			);
		}
		if (command.dayOfWeek() < 1 || command.dayOfWeek() > 7) {
			throw new BusinessException("PROFESSIONAL_AVAILABILITY_DAY_INVALID", "El dia debe estar entre 1 y 7.");
		}
		if (command.opensAt() == null || command.closesAt() == null || !command.closesAt().isAfter(command.opensAt())) {
			throw new BusinessException("PROFESSIONAL_AVAILABILITY_HOURS_INVALID", "El horario de disponibilidad es invalido.");
		}

		wellnessRepository.deleteAvailabilityForDay(principal.organizationId(), principal.branchId(), professionalId, command.dayOfWeek());

		UUID availabilityId = wellnessRepository.createAvailability(
				principal.organizationId(),
				principal.branchId(),
				professionalId,
				principal.userId(),
				new CreateAvailabilityData(command.dayOfWeek(), command.opensAt(), command.closesAt())
		);
		auditService.record(principal, "PROFESSIONAL_AVAILABILITY_CREATED", "PROFESSIONAL_AVAILABILITY", availabilityId, "BACKOFFICE", null, correlationId);
		return wellnessRepository.availability(principal.organizationId(), principal.branchId(), professionalId).stream()
				.filter(slot -> slot.id().equals(availabilityId))
				.findFirst()
				.orElseThrow();
	}

	@Transactional(readOnly = true)
	public List<AvailabilitySlotRecord> availabilitySlots(GymOsPrincipal principal, UUID professionalId, UUID serviceId, LocalDate date) {
		ProfessionalRecord professional = requireActiveProfessional(principal, professionalId);
		if ("EXTERNAL".equals(professional.engagementType())) {
			return List.of();
		}
		LocalDate resolvedDate = date == null ? BusinessTime.today() : date;
		int dayOfWeek = resolvedDate.getDayOfWeek().getValue();

		List<AvailabilityRecord> dayAvailabilities = wellnessRepository.availability(principal.organizationId(), principal.branchId(), professionalId).stream()
				.filter(a -> a.dayOfWeek() == dayOfWeek && "ACTIVE".equals(a.status()))
				.toList();

		if (dayAvailabilities.isEmpty()) {
			return List.of();
		}

		int durationMinutes = 60;
		if (serviceId != null) {
			var serviceOpt = wellnessRepository.service(principal.organizationId(), principal.branchId(), professionalId, serviceId);
			if (serviceOpt.isPresent()) {
				durationMinutes = serviceOpt.get().durationMinutes();
			}
		}

		List<AvailabilitySlotRecord> slots = new ArrayList<>();
		for (AvailabilityRecord availability : dayAvailabilities) {
			LocalTime cursor = availability.opensAt();
			while (!cursor.plusMinutes(durationMinutes).isAfter(availability.closesAt())) {
				OffsetDateTime startsAt = resolvedDate.atTime(cursor).atZone(BusinessTime.DEFAULT_ZONE).toOffsetDateTime();
				OffsetDateTime endsAt = startsAt.plusMinutes(durationMinutes);

				int overlapping = wellnessRepository.overlappingAppointmentsCount(principal.organizationId(), principal.branchId(), professionalId, startsAt, endsAt);
				boolean available = overlapping == 0;

				slots.add(new AvailabilitySlotRecord(startsAt, endsAt, available));
				cursor = cursor.plusMinutes(durationMinutes);
			}
		}

		return slots.stream()
				.collect(Collectors.toMap(AvailabilitySlotRecord::startsAt, s -> s, (existing, replacement) -> existing))
				.values().stream()
				.sorted(Comparator.comparing(AvailabilitySlotRecord::startsAt))
				.toList();
	}

	@Transactional
	public AppointmentRecord createAppointment(GymOsPrincipal principal, CreateWellnessAppointmentCommand command, String correlationId) {
		requireBranch(principal);
		ProfessionalRecord professional = requireActiveProfessional(principal, command.professionalId());

		if (command.clientId() == null) {
			throw new BusinessException("CLIENT_REQUIRED", "Debes especificar un socio para la cita.");
		}
		if (command.startsAt() == null) {
			throw new BusinessException("APPOINTMENT_START_REQUIRED", "Debes especificar la hora de inicio de la cita.");
		}
		if (!command.startsAt().isAfter(OffsetDateTime.now().minusMinutes(5))) {
			throw new BusinessException("APPOINTMENT_START_IN_PAST", "No puedes agendar una cita en el pasado.");
		}

		int durationMinutes = 60;
		if (command.serviceId() != null) {
			var service = wellnessRepository.service(principal.organizationId(), principal.branchId(), command.professionalId(), command.serviceId())
					.orElseThrow(() -> new BusinessException("PROFESSIONAL_SERVICE_NOT_FOUND", "El servicio no existe o no pertenece al profesional."));
			durationMinutes = service.durationMinutes();
		}

		OffsetDateTime endsAt = command.startsAt().plusMinutes(durationMinutes);

		int overlapping = wellnessRepository.overlappingAppointmentsCount(principal.organizationId(), principal.branchId(), command.professionalId(), command.startsAt(), endsAt);
		if (overlapping > 0) {
			throw new BusinessException("APPOINTMENT_SLOT_TAKEN", "El profesional ya tiene una cita agendada en ese horario.");
		}

		boolean isDePlanta = "STAFF".equals(professional.engagementType());

		String initialStatus = isDePlanta ? "CONFIRMED" : "REQUESTED";
		UUID confirmedBy = isDePlanta ? principal.userId() : null;
		OffsetDateTime confirmedAt = isDePlanta ? OffsetDateTime.now() : null;

		UUID appointmentId = wellnessRepository.createAppointment(
				principal.organizationId(),
				principal.branchId(),
				principal.userId(),
				new CreateAppointmentData(
						command.professionalId(),
						command.serviceId(),
						command.clientId(),
						command.startsAt(),
						endsAt,
						initialStatus,
						blankToNull(command.notes()),
						confirmedBy,
						confirmedAt
				)
		);

		auditService.record(principal, "WELLNESS_APPOINTMENT_CREATED", "WELLNESS_APPOINTMENT", appointmentId, "BACKOFFICE", null, correlationId);
		return wellnessRepository.appointment(principal.organizationId(), principal.branchId(), appointmentId).orElseThrow();
	}

	@Transactional(readOnly = true)
	public List<AppointmentRecord> appointments(GymOsPrincipal principal, LocalDate date, UUID professionalId, String status) {
		requireBranch(principal);
		return wellnessRepository.appointments(principal.organizationId(), principal.branchId(), date, professionalId, status);
	}

	@Transactional
	public AppointmentRecord confirmAppointment(GymOsPrincipal principal, UUID appointmentId, ConfirmWellnessAppointmentCommand command, String correlationId) {
		requireBranch(principal);
		AppointmentRecord appointment = wellnessRepository.appointment(principal.organizationId(), principal.branchId(), appointmentId)
				.orElseThrow(() -> new BusinessException("APPOINTMENT_NOT_FOUND", "La cita no existe."));

		if ("CANCELLED".equals(appointment.status()) || "REJECTED".equals(appointment.status())) {
			throw new BusinessException("APPOINTMENT_CANNOT_BE_CONFIRMED", "No se puede confirmar una cita cancelada o rechazada.");
		}

		OffsetDateTime startsAt = (command != null && command.startsAt() != null) ? command.startsAt() : appointment.startsAt();
		int durationMinutes = appointment.durationMinutes() > 0 ? appointment.durationMinutes() : 60;
		OffsetDateTime endsAt = startsAt.plusMinutes(durationMinutes);
		String notes = (command != null && command.notes() != null && !command.notes().isBlank()) ? command.notes().trim() : appointment.notes();

		wellnessRepository.updateAppointmentScheduleAndStatus(
				principal.organizationId(), principal.branchId(), appointmentId,
				startsAt, endsAt, notes, "CONFIRMED", principal.userId()
		);

		auditService.record(principal, "WELLNESS_APPOINTMENT_CONFIRMED", "WELLNESS_APPOINTMENT", appointmentId, "BACKOFFICE", null, correlationId);
		return wellnessRepository.appointment(principal.organizationId(), principal.branchId(), appointmentId).orElseThrow();
	}

	@Transactional
	public AppointmentRecord completeAppointment(GymOsPrincipal principal, UUID appointmentId, String correlationId) {
		requireBranch(principal);
		AppointmentRecord appointment = wellnessRepository.appointment(principal.organizationId(), principal.branchId(), appointmentId)
				.orElseThrow(() -> new BusinessException("APPOINTMENT_NOT_FOUND", "La cita no existe."));

		if ("COMPLETED".equals(appointment.status())) {
			return appointment;
		}
		if (!"CONFIRMED".equals(appointment.status())) {
			throw new BusinessException("APPOINTMENT_NOT_CONFIRMED", "Solo se pueden completar citas confirmadas.");
		}

		wellnessRepository.updateAppointmentStatus(principal.organizationId(), principal.branchId(), appointmentId, "COMPLETED", null, principal.userId());
		auditService.record(principal, "WELLNESS_APPOINTMENT_COMPLETED", "WELLNESS_APPOINTMENT", appointmentId, "BACKOFFICE", null, correlationId);
		return wellnessRepository.appointment(principal.organizationId(), principal.branchId(), appointmentId).orElseThrow();
	}

	@Transactional
	public AppointmentRecord cancelAppointment(GymOsPrincipal principal, UUID appointmentId, String reason, String correlationId) {
		requireBranch(principal);
		AppointmentRecord appointment = wellnessRepository.appointment(principal.organizationId(), principal.branchId(), appointmentId)
				.orElseThrow(() -> new BusinessException("APPOINTMENT_NOT_FOUND", "La cita no existe."));

		if ("CANCELLED".equals(appointment.status())) {
			return appointment;
		}

		wellnessRepository.updateAppointmentStatus(principal.organizationId(), principal.branchId(), appointmentId, "CANCELLED", blankToNull(reason), principal.userId());
		auditService.record(principal, "WELLNESS_APPOINTMENT_CANCELLED", "WELLNESS_APPOINTMENT", appointmentId, "BACKOFFICE", reason, correlationId);
		return wellnessRepository.appointment(principal.organizationId(), principal.branchId(), appointmentId).orElseThrow();
	}

	private ProfessionalRecord requireActiveProfessional(GymOsPrincipal principal, UUID professionalId) {
		requireBranch(principal);
		ProfessionalRecord professional = wellnessRepository.professional(principal.organizationId(), principal.branchId(), professionalId)
				.orElseThrow(() -> new BusinessException("PROFESSIONAL_NOT_FOUND", "El profesional no existe o no esta disponible."));
		if (!"ACTIVE".equals(professional.status())) {
			throw new BusinessException("PROFESSIONAL_NOT_ACTIVE", "El profesional no esta activo.");
		}
		return professional;
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

	private String resolveEngagementType(String value, String fallback) {
		String resolved = value == null || value.isBlank() ? fallback : value.trim().toUpperCase();
		if (!List.of("STAFF", "EXTERNAL").contains(resolved)) {
			throw new BusinessException(
					"PROFESSIONAL_ENGAGEMENT_TYPE_INVALID",
					"La modalidad del profesional debe ser STAFF o EXTERNAL."
			);
		}
		return resolved;
	}

	private void requireBranch(GymOsPrincipal principal) {
		if (principal == null) {
			throw new BusinessException("AUTHENTICATION_REQUIRED", "Debes iniciar sesion para continuar.");
		}
		if (principal.branchId() == null) {
			throw new BusinessException("BRANCH_REQUIRED", "Selecciona una sucursal para operar.");
		}
	}

	public record AvailabilitySlotRecord(OffsetDateTime startsAt, OffsetDateTime endsAt, boolean available) {
	}
}
