package com.gymos.access.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymos.access.infrastructure.AccessRepository;
import com.gymos.access.infrastructure.AccessRepository.PhysicalBenefitMappingRecord;
import com.gymos.access.infrastructure.PhysicalAccessRepository;
import com.gymos.access.infrastructure.PhysicalAccessRepository.ConnectionRecord;
import com.gymos.access.infrastructure.PhysicalAccessRepository.DesiredStateRecord;
import com.gymos.audit.api.AuditService;
import com.gymos.common.time.BusinessTime;
import com.gymos.eligibility.api.EligibilityPolicy;
import com.gymos.eligibility.api.EligibilityPolicy.EligibilityDecision;
import com.gymos.eligibility.api.EligibilityPolicy.EligibilityRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Recalculates provider-neutral desired rights and emits durable connector jobs. */
@Service
public class PhysicalAccessSynchronizationService {
	private final AccessRepository accessRepository;
	private final PhysicalAccessRepository physicalRepository;
	private final EligibilityPolicy eligibilityPolicy;
	private final ObjectMapper objectMapper;
	private final AuditService auditService;

	public PhysicalAccessSynchronizationService(
			AccessRepository accessRepository,
			PhysicalAccessRepository physicalRepository,
			EligibilityPolicy eligibilityPolicy,
			ObjectMapper objectMapper,
			AuditService auditService
	) {
		this.accessRepository = accessRepository;
		this.physicalRepository = physicalRepository;
		this.eligibilityPolicy = eligibilityPolicy;
		this.objectMapper = objectMapper;
		this.auditService = auditService;
	}

	@Transactional
	public DesiredStateRecord reconcileClient(
			ConnectionRecord connection,
			UUID clientId,
			String sourceEventKey,
			String requestedAction,
			String requestSource,
			UUID actorUserId
	) {
		String safeEventKey = requiredEventKey(sourceEventKey);
		OffsetDateTime now = OffsetDateTime.now();
		var client = accessRepository.clientById(connection.organizationId(), clientId).orElse(null);
		var link = physicalRepository.clientLink(
				connection.organizationId(), connection.branchId(), connection.id(), clientId).orElse(null);

		List<EligibilityPolicy.EligibleBenefit> effectiveBenefits = List.of();
		if (client != null) {
			EligibilityDecision eligibility = eligibilityPolicy.evaluate(EligibilityRequest.access(
					connection.organizationId(), connection.branchId(), clientId, BusinessTime.today(), java.util.Set.of()));
			if (eligibility.eligible()) {
				effectiveBenefits = eligibility.benefits().stream()
						.filter(benefit -> benefit.benefitType() == EligibilityPolicy.BenefitType.ACCESS)
						.toList();
			}
		}

		Map<UUID, PhysicalBenefitMappingRecord> mappings = new LinkedHashMap<>();
		for (PhysicalBenefitMappingRecord mapping : accessRepository.physicalBenefitMappings(
				connection.organizationId(), connection.branchId(), connection.id())) {
			mappings.putIfAbsent(mapping.benefitId(), mapping);
		}

		List<String> missingBenefits = effectiveBenefits.stream()
				.filter(benefit -> !mappings.containsKey(benefit.id()))
				.map(EligibilityPolicy.EligibleBenefit::name)
				.toList();
		List<String> desiredRefs = missingBenefits.isEmpty()
				? effectiveBenefits.stream()
						.map(benefit -> mappings.get(benefit.id()).externalAccessLevelRef())
						.distinct()
						.sorted()
						.toList()
				: List.of();

		String action = normalizeAction(requestedAction, desiredRefs);
		String blockedCode = null;
		String error = null;
		if (client == null) {
			blockedCode = "CLIENT_NOT_FOUND";
			error = "El socio ya no existe dentro de la organización.";
		} else if (link == null) {
			blockedCode = "EXTERNAL_PERSON_LINK_REQUIRED";
			error = "Falta vincular al socio con la persona del proveedor físico.";
		} else if (!missingBenefits.isEmpty()) {
			blockedCode = "ACCESS_LEVEL_MAPPING_REQUIRED";
			error = "Falta mapear el nivel externo para: " + String.join(", ", missingBenefits) + ".";
		} else if (!connection.enabled()) {
			blockedCode = "CONNECTION_DISABLED";
			error = "La conexión física está desactivada.";
		}

		String status = blockedCode == null ? "PENDING" : "BLOCKED";
		DesiredStateRecord state = physicalRepository.upsertDesiredState(
				connection, clientId, toJson(desiredRefs), action, status, blockedCode, error,
				safeEventKey, actorUserId, now);
		if ("PENDING".equals(state.status())) {
			physicalRepository.enqueueJob(
					connection, state, link.externalPersonRef(),
					idempotencyKey(connection, state, safeEventKey), normalizeSource(requestSource), now);
		}

		auditService.recordSystem(
				connection.organizationId(), connection.branchId(), actorUserId,
				"PHYSICAL_ACCESS_RIGHTS_RECALCULATED", "ACCESS_PROVIDER_SYNC_STATE", state.id(),
				normalizeSource(requestSource),
				"action=" + state.action() + "; status=" + state.status() + "; levels=" + desiredRefs.size(),
				safeEventKey);
		return physicalRepository.desiredState(
				connection.organizationId(), connection.branchId(), connection.id(), clientId).orElse(state);
	}

	@Transactional
	public int reconcileClientAcrossConnections(
			UUID organizationId,
			UUID clientId,
			String sourceEventKey,
			String requestedAction,
			String requestSource
	) {
		int reconciled = 0;
		for (ConnectionRecord connection : physicalRepository.connectionsForLinkedClient(organizationId, clientId)) {
			reconcileClient(connection, clientId, sourceEventKey, requestedAction, requestSource, null);
			reconciled++;
		}
		return reconciled;
	}

	@Transactional
	public int reconcileConnection(
			ConnectionRecord connection,
			String sourceEventKey,
			String requestedAction,
			String requestSource,
			UUID actorUserId
	) {
		int reconciled = 0;
		for (UUID clientId : physicalRepository.linkedClientIds(
				connection.organizationId(), connection.branchId(), connection.id())) {
			reconcileClient(connection, clientId, sourceEventKey, requestedAction, requestSource, actorUserId);
			reconciled++;
		}
		return reconciled;
	}

	private String normalizeAction(String requestedAction, List<String> desiredRefs) {
		if (requestedAction == null || requestedAction.isBlank()) {
			return desiredRefs.isEmpty() ? "REVOKE" : "UPSERT";
		}
		String normalized = requestedAction.trim().toUpperCase(java.util.Locale.ROOT);
		if (!java.util.Set.of("UPSERT", "REVOKE", "RECONCILE").contains(normalized)) {
			throw new IllegalArgumentException("Unsupported physical synchronization action: " + requestedAction);
		}
		return normalized;
	}

	private String normalizeSource(String requestSource) {
		if (requestSource == null || requestSource.isBlank()) {
			return "SYSTEM";
		}
		String normalized = requestSource.trim().toUpperCase(java.util.Locale.ROOT)
				.replaceAll("[^A-Z0-9_]", "_");
		return normalized.substring(0, Math.min(normalized.length(), 40));
	}

	private String requiredEventKey(String sourceEventKey) {
		if (sourceEventKey == null || sourceEventKey.isBlank()) {
			throw new IllegalArgumentException("A source event key is required for physical synchronization.");
		}
		String normalized = sourceEventKey.trim();
		return normalized.substring(0, Math.min(normalized.length(), 240));
	}

	private String idempotencyKey(ConnectionRecord connection, DesiredStateRecord state, String eventKey) {
		String source = connection.id() + "|" + state.clientId() + "|" + state.action() + "|"
				+ state.desiredAccessLevelRefsJson() + "|" + eventKey;
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8));
			return "physical-access:" + HexFormat.of().formatHex(digest);
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is required by the JVM.", exception);
		}
	}

	private String toJson(List<String> values) {
		try {
			return objectMapper.writeValueAsString(values);
		} catch (JsonProcessingException exception) {
			throw new IllegalStateException("No fue posible serializar derechos físicos.", exception);
		}
	}
}
