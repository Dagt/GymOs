package com.gymos.access.application;

import com.gymos.access.api.AccessController.AccessRequest;
import com.gymos.access.api.AccessController.ManualOpenRequest;
import com.gymos.access.api.AccessController.TemporaryPassRequest;
import com.gymos.access.infrastructure.AccessRepository;
import com.gymos.access.infrastructure.AccessRepository.AccessDecisionResponse;
import com.gymos.access.infrastructure.AccessRepository.AccessDeviceRecord;
import com.gymos.access.infrastructure.AccessRepository.AccessLogRecord;
import com.gymos.access.infrastructure.AccessRepository.ActiveMembershipRecord;
import com.gymos.access.infrastructure.AccessRepository.ClientAccessRecord;
import com.gymos.access.infrastructure.AccessRepository.TemporaryPassRecord;
import com.gymos.audit.api.AuditService;
import com.gymos.common.api.BusinessException;
import com.gymos.common.security.GymOsPrincipal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccessService {
	private final JdbcClient jdbcClient;
	private final AccessRepository accessRepository;
	private final AuditService auditService;

	public AccessService(JdbcClient jdbcClient, AccessRepository accessRepository, AuditService auditService) {
		this.jdbcClient = jdbcClient;
		this.accessRepository = accessRepository;
		this.auditService = auditService;
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

	@Transactional
	public AccessDecisionResponse requestAccess(GymOsPrincipal principal, AccessRequest request, String correlationId) {
		requireBranch(principal);
		AccessDeviceRecord device = resolveDevice(principal, request.deviceCode());
		ClientAccessRecord client = resolveClient(principal, request.clientId(), request.identifier());
		OffsetDateTime now = OffsetDateTime.now();

		if (!"ACTIVE".equals(client.status())) {
			return recordDecision(principal, device, client, null, null, "MEMBERSHIP_CHECK", "DENIED", "RECEPTION", "Cliente inactivo o suspendido.", now, correlationId);
		}

		ActiveMembershipRecord membership = accessRepository.activeMembership(principal.organizationId(), principal.branchId(), client.id(), LocalDate.now())
				.orElse(null);
		if (membership != null) {
			return recordDecision(principal, device, client, membership.id(), null, "MEMBERSHIP_CHECK", "GRANTED", "RECEPTION", "Membresia activa hasta " + membership.endsOn() + ".", now, correlationId);
		}

		TemporaryPassRecord pass = accessRepository.validTemporaryPass(principal.organizationId(), principal.branchId(), client.id(), now)
				.orElse(null);
		if (pass != null) {
			consumeTemporaryPass(pass.id(), now);
			return recordDecision(principal, device, client, null, pass.id(), "TEMPORARY_PASS", "GRANTED", "RECEPTION", "Pase temporal vigente.", now, correlationId);
		}

		return recordDecision(principal, device, client, null, null, "MEMBERSHIP_CHECK", "DENIED", "RECEPTION", "Sin membresia activa ni pase temporal vigente.", now, correlationId);
	}

	@Transactional
	public AccessDecisionResponse manualOpen(GymOsPrincipal principal, ManualOpenRequest request, String correlationId) {
		requireBranch(principal);
		AccessDeviceRecord device = resolveDevice(principal, request.deviceCode());
		ClientAccessRecord client = request.clientId() == null ? null : accessRepository.clientById(principal.organizationId(), request.clientId())
				.orElseThrow(() -> new BusinessException("CLIENT_NOT_FOUND", "El cliente no existe o no esta disponible."));
		return recordDecision(principal, device, client, null, null, "MANUAL_OPEN", "GRANTED", "RECEPTION", request.reason(), OffsetDateTime.now(), correlationId);
	}

	@Transactional
	public TemporaryPassRecord createTemporaryPass(GymOsPrincipal principal, TemporaryPassRequest request, String correlationId) {
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
		UUID passId = UUID.randomUUID();
		jdbcClient.sql("""
				INSERT INTO access_temporary_pass (
				    id, organization_id, branch_id, client_id, status, starts_at, expires_at,
				    max_uses, uses_count, reason, created_at, updated_at, created_by, updated_by, version
				)
				VALUES (
				    :id, :organizationId, :branchId, :clientId, 'ACTIVE', :startsAt, :expiresAt,
				    :maxUses, 0, :reason, :createdAt, :updatedAt, :createdBy, :updatedBy, 0
				)
				""")
				.param("id", passId)
				.param("organizationId", principal.organizationId())
				.param("branchId", principal.branchId())
				.param("clientId", client.id())
				.param("startsAt", startsAt)
				.param("expiresAt", request.expiresAt())
				.param("maxUses", request.maxUses())
				.param("reason", request.reason())
				.param("createdAt", now)
				.param("updatedAt", now)
				.param("createdBy", principal.userId())
				.param("updatedBy", principal.userId())
				.update();
		auditService.record(principal, "ACCESS_TEMPORARY_PASS_CREATED", "ACCESS_TEMPORARY_PASS", passId, "RECEPTION", request.reason(), correlationId);
		return accessRepository.temporaryPass(principal.organizationId(), passId)
				.orElseThrow(() -> new BusinessException("TEMPORARY_PASS_NOT_FOUND", "El pase temporal no esta disponible."));
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
		UUID logId = UUID.randomUUID();
		jdbcClient.sql("""
				INSERT INTO access_log (
				    id, organization_id, branch_id, device_id, client_id, membership_id,
				    temporary_pass_id, access_type, decision, source, reason, command_status,
				    occurred_at, created_at, created_by, correlation_id
				)
				VALUES (
				    :id, :organizationId, :branchId, :deviceId, :clientId, :membershipId,
				    :temporaryPassId, :accessType, :decision, :source, :reason, :commandStatus,
				    :occurredAt, :createdAt, :createdBy, :correlationId
				)
				""")
				.param("id", logId)
				.param("organizationId", principal.organizationId())
				.param("branchId", principal.branchId())
				.param("deviceId", device.id())
				.param("clientId", client == null ? null : client.id())
				.param("membershipId", membershipId)
				.param("temporaryPassId", temporaryPassId)
				.param("accessType", accessType)
				.param("decision", decision)
				.param("source", source)
				.param("reason", reason)
				.param("commandStatus", "GRANTED".equals(decision) ? "SIMULATED_OPEN" : "NOT_SENT")
				.param("occurredAt", now)
				.param("createdAt", now)
				.param("createdBy", principal.userId())
				.param("correlationId", UUID.fromString(correlationId))
				.update();
		auditService.record(principal, "GRANTED".equals(decision) ? "ACCESS_GRANTED" : "ACCESS_DENIED", "ACCESS_LOG", logId, source, reason, correlationId);
		return accessRepository.decision(logId)
				.orElseThrow(() -> new BusinessException("ACCESS_LOG_NOT_FOUND", "No fue posible recuperar la decision de acceso."));
	}

	private void consumeTemporaryPass(UUID passId, OffsetDateTime now) {
		jdbcClient.sql("""
				UPDATE access_temporary_pass
				SET uses_count = uses_count + 1,
				    status = CASE WHEN uses_count + 1 >= max_uses THEN 'CONSUMED' ELSE status END,
				    updated_at = :updatedAt,
				    version = version + 1
				WHERE id = :passId
				""")
				.param("updatedAt", now)
				.param("passId", passId)
				.update();
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
