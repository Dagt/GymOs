package com.gymos.wellness.application;

import com.gymos.audit.api.AuditService;
import com.gymos.common.api.BusinessException;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.wellness.application.command.CreateProfessionalAvailabilityCommand;
import com.gymos.wellness.application.command.CreateProfessionalCommand;
import com.gymos.wellness.application.command.CreateProfessionalServiceCommand;
import com.gymos.wellness.infrastructure.WellnessRepository;
import com.gymos.wellness.infrastructure.WellnessRepository.AvailabilityRecord;
import com.gymos.wellness.infrastructure.WellnessRepository.CreateAvailabilityData;
import com.gymos.wellness.infrastructure.WellnessRepository.CreateProfessionalData;
import com.gymos.wellness.infrastructure.WellnessRepository.CreateServiceData;
import com.gymos.wellness.infrastructure.WellnessRepository.ProfessionalRecord;
import com.gymos.wellness.infrastructure.WellnessRepository.ProfessionalServiceRecord;
import java.util.List;
import java.util.UUID;
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
						blankToNull(command.bio())
				)
		);
		auditService.record(principal, "PROFESSIONAL_CREATED", "PROFESSIONAL", professionalId, "BACKOFFICE", null, correlationId);
		return wellnessRepository.professional(principal.organizationId(), principal.branchId(), professionalId).orElseThrow();
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
		requireActiveProfessional(principal, professionalId);
		if (command.dayOfWeek() < 1 || command.dayOfWeek() > 7) {
			throw new BusinessException("PROFESSIONAL_AVAILABILITY_DAY_INVALID", "El dia debe estar entre 1 y 7.");
		}
		if (command.opensAt() == null || command.closesAt() == null || !command.closesAt().isAfter(command.opensAt())) {
			throw new BusinessException("PROFESSIONAL_AVAILABILITY_HOURS_INVALID", "El horario de disponibilidad es invalido.");
		}
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

	private void requireBranch(GymOsPrincipal principal) {
		if (principal == null) {
			throw new BusinessException("AUTHENTICATION_REQUIRED", "Debes iniciar sesion para continuar.");
		}
		if (principal.branchId() == null) {
			throw new BusinessException("BRANCH_REQUIRED", "Selecciona una sucursal para operar.");
		}
	}
}
