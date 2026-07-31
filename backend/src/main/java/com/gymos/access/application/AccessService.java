package com.gymos.access.application;

import com.gymos.access.application.command.AccessRequestCommand;
import com.gymos.access.application.command.ConsumeVisitPassCommand;
import com.gymos.access.application.command.CreateVisitPassCommand;
import com.gymos.access.application.command.ManualOpenCommand;
import com.gymos.access.application.command.TemporaryPassCommand;
import com.gymos.access.infrastructure.AccessRepository;
import com.gymos.access.infrastructure.AccessRepository.AccessDecisionResponse;
import com.gymos.access.infrastructure.AccessRepository.AccessDeviceRecord;
import com.gymos.access.infrastructure.AccessRepository.AccessLogRecord;
import com.gymos.access.infrastructure.AccessRepository.ClientAccessRecord;
import com.gymos.access.infrastructure.AccessRepository.EffectiveAccessBenefitRecord;
import com.gymos.access.infrastructure.AccessRepository.TemporaryPassRecord;
import com.gymos.access.infrastructure.AccessRepository.VisitPassRecord;
import com.gymos.audit.api.AuditService;
import com.gymos.common.api.BusinessException;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.common.time.BusinessTime;
import com.gymos.eligibility.api.EligibilityPolicy;
import com.gymos.eligibility.api.EligibilityPolicy.ActiveMembership;
import com.gymos.eligibility.api.EligibilityPolicy.EligibilityDecision;
import com.gymos.eligibility.api.EligibilityPolicy.EligibilityRequest;
import com.gymos.eligibility.api.EligibilityPolicy.Reason;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccessService {
	private final AccessRepository accessRepository;
	private final AuditService auditService;
	private final EligibilityPolicy eligibilityPolicy;

	public AccessService(
			AccessRepository accessRepository,
			AuditService auditService,
			EligibilityPolicy eligibilityPolicy
	) {
		this.accessRepository = accessRepository;
		this.auditService = auditService;
		this.eligibilityPolicy = eligibilityPolicy;
	}

	@Transactional(readOnly = true)
	public List<AccessDeviceRecord> devices(GymOsPrincipal principal) {
		requireBranch(principal);
		return accessRepository.devices(principal.organizationId(), principal.branchId());
	}

	@Transactional(readOnly = true)
	public List<AccessLogRecord> logs(GymOsPrincipal principal, int limit) {
		requireBranch(principal);
		int boundedLimit = Math.max(1, Math.min(limit, 100));
		return accessRepository.logs(principal.organizationId(), principal.branchId(), boundedLimit);
	}

	@Transactional(readOnly = true)
	public List<VisitPassRecord> visitPasses(GymOsPrincipal principal, UUID clientId) {
		requireBranch(principal);
		return accessRepository.visitPasses(principal.organizationId(), principal.branchId(), clientId);
	}

	@Transactional(readOnly = true)
	public List<EffectiveAccessBenefitRecord> effectiveBenefits(GymOsPrincipal principal, UUID clientId) {
		requireBranch(principal);
		EligibilityDecision eligibility = eligibilityPolicy.evaluate(EligibilityRequest.access(
				principal.organizationId(), principal.branchId(), clientId, BusinessTime.today(), Set.of()));
		if (eligibility.reason() == Reason.CLIENT_NOT_FOUND) {
			throw new BusinessException("CLIENT_NOT_FOUND", "El cliente no existe o no esta disponible.");
		}
		if (!eligibility.eligible()) {
			return List.of();
		}
		return eligibility.benefits().stream()
				.map(benefit -> new EffectiveAccessBenefitRecord(
						benefit.id(), benefit.code(), benefit.name(),
						benefit.membershipId(), benefit.planId(), benefit.planName()))
				.toList();
	}

	@Transactional
	public List<UUID> replaceDeviceBenefits(GymOsPrincipal principal, UUID deviceId, List<UUID> benefitIds, String correlationId) {
		requireBranch(principal);
		AccessDeviceRecord device = accessRepository.devices(principal.organizationId(), principal.branchId()).stream()
				.filter(candidate -> candidate.id().equals(deviceId))
				.findFirst()
				.orElseThrow(() -> new BusinessException("ACCESS_DEVICE_NOT_FOUND", "El dispositivo no existe en la sucursal."));
		List<UUID> normalizedIds = benefitIds == null ? List.of() : benefitIds.stream().distinct().toList();
		if (!accessRepository.accessBenefitsExist(principal.organizationId(), principal.branchId(), normalizedIds)) {
			throw new BusinessException("ACCESS_BENEFIT_NOT_AVAILABLE", "Uno o mas beneficios de acceso no estan disponibles.");
		}
		accessRepository.replaceDeviceBenefits(
				principal.organizationId(), principal.branchId(), device.id(), principal.userId(), normalizedIds);
		auditService.record(principal, "ACCESS_DEVICE_BENEFITS_REPLACED", "ACCESS_DEVICE", device.id(),
				"BACKOFFICE", "benefits=" + normalizedIds.size(), correlationId);
		return accessRepository.deviceRequiredBenefitIds(
				principal.organizationId(), principal.branchId(), device.id());
	}

	@Transactional
	public AccessDecisionResponse requestAccess(GymOsPrincipal principal, AccessRequestCommand request, String correlationId) {
		requireBranch(principal);
		AccessDeviceRecord device = resolveDevice(principal, request.deviceCode());
		ClientAccessRecord client = resolveClient(principal, request.clientId(), request.identifier());
		OffsetDateTime now = OffsetDateTime.now();
		List<UUID> requiredBenefitIds = accessRepository.deviceRequiredBenefitIds(
				principal.organizationId(), principal.branchId(), device.id());
		EligibilityDecision eligibility = eligibilityPolicy.evaluate(EligibilityRequest.access(
				principal.organizationId(), principal.branchId(), client.id(), BusinessTime.today(),
				Set.copyOf(requiredBenefitIds)));
		ActiveMembership membership = eligibility.membership();

		if (eligibility.eligible()) {
			return recordDecision(principal, device, client, membership.id(), null, "MEMBERSHIP_CHECK", "GRANTED", "RECEPTION", "Membresia activa hasta " + membership.endsOn() + ".", now, correlationId);
		}
		if (eligibility.reason() == Reason.CLIENT_NOT_ACTIVE) {
			return recordDecision(principal, device, client, null, null, "MEMBERSHIP_CHECK", "DENIED",
					"RECEPTION", "Cliente inactivo o suspendido.", now, correlationId);
		}
		if (eligibility.reason() == Reason.BENEFIT_NOT_GRANTED) {
			return recordDecision(principal, device, client, membership.id(), null, "BENEFIT_CHECK",
					"DENIED", "RECEPTION", "El plan no incluye el acceso requerido por el dispositivo.",
					now, correlationId);
		}

		TemporaryPassRecord pass = accessRepository.validTemporaryPass(principal.organizationId(), principal.branchId(), client.id(), now)
				.orElse(null);
		if (pass != null) {
			accessRepository.consumeTemporaryPass(pass.id(), now);
			return recordDecision(principal, device, client, null, pass.id(), "TEMPORARY_PASS", "GRANTED", "RECEPTION", "Pase temporal vigente.", now, correlationId);
		}

		return recordDecision(principal, device, client, null, null, "MEMBERSHIP_CHECK", "DENIED", "RECEPTION", "Sin membresia activa ni pase temporal vigente.", now, correlationId);
	}

	@Transactional
	public AccessDecisionResponse manualOpen(GymOsPrincipal principal, ManualOpenCommand request, String correlationId) {
		requireBranch(principal);
		AccessDeviceRecord device = resolveDevice(principal, request.deviceCode());
		ClientAccessRecord client = request.clientId() == null ? null : accessRepository.clientById(principal.organizationId(), request.clientId())
				.orElseThrow(() -> new BusinessException("CLIENT_NOT_FOUND", "El cliente no existe o no esta disponible."));
		return recordDecision(principal, device, client, null, null, "MANUAL_OPEN", "GRANTED", "RECEPTION", request.reason(), OffsetDateTime.now(), correlationId);
	}

	@Transactional
	public TemporaryPassRecord createTemporaryPass(GymOsPrincipal principal, TemporaryPassCommand request, String correlationId) {
		requireBranch(principal);
		ClientAccessRecord client = accessRepository.clientById(principal.organizationId(), request.clientId())
				.orElseThrow(() -> new BusinessException("CLIENT_NOT_FOUND", "El cliente no existe o no esta disponible."));
		if (!"ACTIVE".equals(client.status())) {
			throw new BusinessException("CLIENT_NOT_ACTIVE", "Solo puedes crear pases temporales para clientes activos.");
		}
		OffsetDateTime now = OffsetDateTime.now();
		OffsetDateTime startsAt = request.startsAt() == null ? now : request.startsAt();
		if (!request.expiresAt().isAfter(startsAt)) {
			throw new BusinessException("TEMPORARY_PASS_INVALID_RANGE", "La expiracion debe ser posterior al inicio.");
		}
		UUID passId = accessRepository.createTemporaryPass(
				principal.organizationId(),
				principal.branchId(),
				client.id(),
				principal.userId(),
				startsAt,
				request.expiresAt(),
				request.maxUses(),
				request.reason()
		);
		auditService.record(principal, "ACCESS_TEMPORARY_PASS_CREATED", "ACCESS_TEMPORARY_PASS", passId, "RECEPTION", request.reason(), correlationId);
		return accessRepository.temporaryPass(principal.organizationId(), passId)
				.orElseThrow(() -> new BusinessException("TEMPORARY_PASS_NOT_FOUND", "El pase temporal no esta disponible."));
	}

	@Transactional
	public VisitPassRecord createVisitPass(GymOsPrincipal principal, CreateVisitPassCommand request, String correlationId) {
		requireBranch(principal);
		ClientAccessRecord client = accessRepository.clientById(principal.organizationId(), request.clientId())
				.orElseThrow(() -> new BusinessException("CLIENT_NOT_FOUND", "El cliente no existe o no esta disponible."));
		if (!"ACTIVE".equals(client.status())) {
			throw new BusinessException("CLIENT_NOT_ACTIVE", "Solo puedes crear paquetes de visita para clientes activos.");
		}
		LocalDate validFrom = request.validFrom() == null ? BusinessTime.today() : request.validFrom();
		if (request.validUntil() != null && request.validUntil().isBefore(validFrom)) {
			throw new BusinessException("VISIT_PASS_INVALID_RANGE", "La vigencia final no puede ser anterior al inicio.");
		}
		UUID passId = accessRepository.createVisitPass(
				principal.organizationId(),
				principal.branchId(),
				request.clientId(),
				request.saleId(),
				request.passType(),
				request.totalVisits(),
				validFrom,
				request.validUntil()
		);
		auditService.record(principal, "VISIT_PASS_CREATED", "VISIT_PASS", passId, "RECEPTION", client.fullName(), correlationId);
		return accessRepository.visitPass(principal.organizationId(), principal.branchId(), passId)
				.orElseThrow(() -> new BusinessException("VISIT_PASS_NOT_FOUND", "El paquete de visitas no esta disponible."));
	}

	@Transactional
	public VisitPassRecord consumeVisitPass(GymOsPrincipal principal, UUID visitPassId, ConsumeVisitPassCommand request, String correlationId) {
		requireBranch(principal);
		VisitPassRecord current = accessRepository.visitPass(principal.organizationId(), principal.branchId(), visitPassId)
				.orElseThrow(() -> new BusinessException("VISIT_PASS_NOT_FOUND", "El paquete de visitas no esta disponible."));
		LocalDate today = BusinessTime.today();
		if (!"ACTIVE".equals(current.status())) {
			throw new BusinessException("VISIT_PASS_NOT_ACTIVE", "El paquete de visitas no esta activo.");
		}
		if (current.validFrom().isAfter(today) || (current.validUntil() != null && current.validUntil().isBefore(today))) {
			throw new BusinessException("VISIT_PASS_OUT_OF_RANGE", "El paquete no esta vigente hoy.");
		}
		if (current.remainingVisits() <= 0) {
			throw new BusinessException("VISIT_PASS_CONSUMED", "El paquete ya no tiene visitas disponibles.");
		}
		boolean consumed = accessRepository.consumeVisitPass(principal.organizationId(), principal.branchId(), visitPassId, OffsetDateTime.now());
		if (!consumed) {
			throw new BusinessException("VISIT_PASS_CONFLICT", "El paquete fue actualizado por otra operacion. Intenta de nuevo.");
		}
		auditService.record(principal, "VISIT_PASS_CONSUMED", "VISIT_PASS", visitPassId, "RECEPTION", request.reason(), correlationId);
		return accessRepository.visitPass(principal.organizationId(), principal.branchId(), visitPassId)
				.orElseThrow(() -> new BusinessException("VISIT_PASS_NOT_FOUND", "El paquete de visitas no esta disponible."));
	}

	private AccessDecisionResponse recordDecision(
			GymOsPrincipal principal,
			AccessDeviceRecord device,
			ClientAccessRecord client,
			UUID membershipId,
			UUID temporaryPassId,
			String accessType,
			String decision,
			String source,
			String reason,
			OffsetDateTime now,
			String correlationId
	) {
		UUID logId = accessRepository.createAccessLog(
				principal.organizationId(),
				principal.branchId(),
				device.id(),
				client == null ? null : client.id(),
				membershipId,
				temporaryPassId,
				accessType,
				decision,
				source,
				reason,
				"GRANTED".equals(decision) ? "SIMULATED_OPEN" : "NOT_SENT",
				now,
				principal.userId(),
				UUID.fromString(correlationId)
		);
		auditService.record(principal, "GRANTED".equals(decision) ? "ACCESS_GRANTED" : "ACCESS_DENIED", "ACCESS_LOG", logId, source, reason, correlationId);
		return accessRepository.decision(logId)
				.orElseThrow(() -> new BusinessException("ACCESS_LOG_NOT_FOUND", "No fue posible recuperar la decision de acceso."));
	}

	private AccessDeviceRecord resolveDevice(GymOsPrincipal principal, String deviceCode) {
		if (deviceCode != null && !deviceCode.isBlank()) {
			return accessRepository.deviceByCode(principal.organizationId(), principal.branchId(), deviceCode.trim())
					.orElseThrow(() -> new BusinessException("ACCESS_DEVICE_NOT_FOUND", "El dispositivo de acceso no existe o no esta activo."));
		}
		return accessRepository.defaultDevice(principal.organizationId(), principal.branchId())
				.orElseThrow(() -> new BusinessException("ACCESS_DEVICE_NOT_FOUND", "No hay dispositivo de acceso activo para esta sucursal."));
	}

	private ClientAccessRecord resolveClient(GymOsPrincipal principal, UUID clientId, String identifier) {
		if (clientId != null) {
			return accessRepository.clientById(principal.organizationId(), clientId)
					.orElseThrow(() -> new BusinessException("CLIENT_NOT_FOUND", "El cliente no existe o no esta disponible."));
		}
		if (identifier != null && !identifier.isBlank()) {
			return accessRepository.clientByIdentifier(principal.organizationId(), identifier)
					.orElseThrow(() -> new BusinessException("CLIENT_NOT_FOUND", "El cliente no existe o no esta disponible."));
		}
		throw new BusinessException("ACCESS_CLIENT_REQUIRED", "Selecciona un cliente o captura un identificador.");
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
