package com.gymos.access.application;

import com.gymos.access.application.command.AccessRequestCommand;
import com.gymos.access.application.command.ConfigurePhysicalIntegrationCommand;
import com.gymos.access.application.command.ConsumeVisitPassCommand;
import com.gymos.access.application.command.CreateVisitPassCommand;
import com.gymos.access.application.command.LinkPhysicalClientCommand;
import com.gymos.access.application.command.ManualOpenCommand;
import com.gymos.access.application.command.TemporaryPassCommand;
import com.gymos.access.infrastructure.AccessRepository;
import com.gymos.access.infrastructure.AccessRepository.AccessDecisionResponse;
import com.gymos.access.infrastructure.AccessRepository.AccessDeviceRecord;
import com.gymos.access.infrastructure.AccessRepository.AccessLogRecord;
import com.gymos.access.infrastructure.AccessRepository.ClientAccessRecord;
import com.gymos.access.infrastructure.AccessRepository.EffectiveAccessBenefitRecord;
import com.gymos.access.infrastructure.AccessRepository.PhysicalAccessBenefitRecord;
import com.gymos.access.infrastructure.AccessRepository.PhysicalBenefitMappingRecord;
import com.gymos.access.infrastructure.AccessRepository.PhysicalProviderConnectionRecord;
import com.gymos.access.infrastructure.AccessRepository.TemporaryPassRecord;
import com.gymos.access.infrastructure.AccessRepository.VisitPassRecord;
import com.gymos.access.infrastructure.PhysicalAccessRepository;
import com.gymos.access.infrastructure.PhysicalAccessRepository.ConnectionRecord;
import com.gymos.access.infrastructure.PhysicalAccessRepository.ConnectorContext;
import com.gymos.access.infrastructure.PhysicalAccessRepository.DesiredStateRecord;
import com.gymos.access.infrastructure.PhysicalAccessRepository.DeviceLinkRecord;
import com.gymos.access.infrastructure.PhysicalAccessRepository.PhysicalAuthorizationRecord;
import com.gymos.access.infrastructure.PhysicalAccessRepository.DeviceCandidateRecord;
import com.gymos.audit.api.AuditService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymos.common.api.BusinessException;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.common.time.BusinessTime;
import com.gymos.eligibility.api.EligibilityPolicy;
import com.gymos.eligibility.api.EligibilityPolicy.ActiveMembership;
import com.gymos.eligibility.api.EligibilityPolicy.EligibilityDecision;
import com.gymos.eligibility.api.EligibilityPolicy.EligibilityRequest;
import com.gymos.eligibility.api.EligibilityPolicy.Reason;
import com.gymos.visitpackages.api.operations.VisitPackageAccessOperations;
import com.gymos.visitpackages.api.operations.VisitPackageAccessOperations.VisitPackageAccessResult;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccessService {
	private final AccessRepository accessRepository;
	private final AuditService auditService;
	private final EligibilityPolicy eligibilityPolicy;
	private final ObjectMapper objectMapper;
	private final PhysicalAccessRepository physicalAccessRepository;
	private final PhysicalAccessSynchronizationService physicalSynchronizationService;
	private final PhysicalAccessProviderCatalog providerCatalog;
	private final PhysicalConnectorService connectorService;
	private final VisitPackageAccessOperations visitPackages;

	public AccessService(
			AccessRepository accessRepository,
			AuditService auditService,
			EligibilityPolicy eligibilityPolicy,
			ObjectMapper objectMapper,
			PhysicalAccessRepository physicalAccessRepository,
			PhysicalAccessSynchronizationService physicalSynchronizationService,
			PhysicalAccessProviderCatalog providerCatalog,
			PhysicalConnectorService connectorService,
			VisitPackageAccessOperations visitPackages
	) {
		this.accessRepository = accessRepository;
		this.auditService = auditService;
		this.eligibilityPolicy = eligibilityPolicy;
		this.objectMapper = objectMapper;
		this.physicalAccessRepository = physicalAccessRepository;
		this.physicalSynchronizationService = physicalSynchronizationService;
		this.providerCatalog = providerCatalog;
		this.connectorService = connectorService;
		this.visitPackages = visitPackages;
	}

	@Transactional(readOnly = true)
	public List<AccessDeviceRecord> devices(GymOsPrincipal principal) {
		requireBranch(principal);
		return accessRepository.devices(principal.organizationId(), principal.branchId());
	}

	@Transactional
	public AccessDeviceRecord createDevice(
			GymOsPrincipal principal,
			String name,
			String deviceCode,
			String deviceType,
			String provider,
			UUID requiredBenefitId,
			String correlationId
	) {
		requireBranch(principal);
		requirePhysicalManagement(principal);
		String normalizedCode = normalizeDeviceCode(deviceCode);
		if (accessRepository.deviceCodeExists(principal.organizationId(), normalizedCode)) {
			throw new BusinessException("ACCESS_DEVICE_CODE_IN_USE", "El código del punto de acceso ya está registrado.");
		}
		String normalizedDeviceType = normalizeDeviceType(deviceType);
		String normalizedProvider = normalizeProvider(provider);
		List<UUID> requiredBenefitIds = requiredBenefitId == null ? List.of() : List.of(requiredBenefitId);
		if (providerCatalog.isProductive(normalizedProvider) && requiredBenefitIds.isEmpty()) {
			throw new BusinessException(
					"PHYSICAL_ACCESS_BENEFIT_REQUIRED",
					"Un punto físico productivo debe exigir exactamente un beneficio ACCESS."
			);
		}
		if (!requiredBenefitIds.isEmpty() && !accessRepository.accessBenefitsExist(
				principal.organizationId(), principal.branchId(), requiredBenefitIds)) {
			throw new BusinessException(
					"ACCESS_BENEFIT_NOT_AVAILABLE",
					"El beneficio ACCESS no está disponible en esta sucursal."
			);
		}
		AccessDeviceRecord device = accessRepository.createDevice(
				principal.organizationId(), principal.branchId(), normalizeRequiredText(name), normalizedCode,
				normalizedDeviceType, normalizedProvider, principal.userId());
		accessRepository.replaceDeviceBenefits(
				principal.organizationId(), principal.branchId(), device.id(), principal.userId(), requiredBenefitIds);
		auditService.record(principal, "ACCESS_DEVICE_CREATED", "ACCESS_DEVICE", device.id(),
				"BACKOFFICE", "code=" + device.deviceCode() + "; benefit=" + requiredBenefitId, correlationId);
		return device;
	}

	@Transactional(readOnly = true)
	public List<UUID> deviceRequiredBenefits(GymOsPrincipal principal, UUID deviceId) {
		requireBranch(principal);
		accessDevice(principal, deviceId);
		return accessRepository.deviceRequiredBenefitIds(
				principal.organizationId(), principal.branchId(), deviceId);
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

	@Transactional(readOnly = true)
	public PhysicalIntegrationView physicalIntegration(GymOsPrincipal principal) {
		requireBranch(principal);
		ConnectionRecord connection = physicalAccessRepository
				.connection(principal.organizationId(), principal.branchId()).orElse(null);
		List<PhysicalAccessBenefitView> benefits = accessRepository
				.physicalAccessBenefits(principal.organizationId(), principal.branchId())
				.stream()
				.map(this::physicalAccessBenefitView)
				.toList();
		List<PhysicalBenefitMappingView> mappings = connection == null
				? List.of()
				: accessRepository.physicalBenefitMappings(
						principal.organizationId(), principal.branchId(), connection.id())
						.stream()
						.map(this::physicalBenefitMappingView)
						.toList();
		List<PhysicalDeviceLinkView> deviceLinks = connection == null ? List.of()
				: physicalAccessRepository.deviceLinks(
						principal.organizationId(), principal.branchId(), connection.id()).stream()
						.map(this::physicalDeviceLinkView)
						.toList();
		return new PhysicalIntegrationView(
				providerCatalog.providers(),
				connection == null ? defaultPhysicalConnection() : physicalConnectionView(connection),
				benefits, mappings, deviceLinks);
	}

	@Transactional(readOnly = true)
	public List<DeviceCandidateRecord> physicalDeviceCandidates(GymOsPrincipal principal, int limit) {
		requireBranch(principal);
		requirePhysicalManagement(principal);
		ConnectionRecord connection = requiredPhysicalConnection(principal);
		return physicalAccessRepository.deviceCandidates(
				principal.organizationId(), principal.branchId(), connection.id(),
				Math.max(1, Math.min(limit, 200)));
	}

	@Transactional
	public PhysicalIntegrationView configurePhysicalIntegration(
			GymOsPrincipal principal,
			ConfigurePhysicalIntegrationCommand request,
			String correlationId
	) {
		requireBranch(principal);
		requirePhysicalManagement(principal);
		String providerKey = providerCatalog.requireProductive(request.providerKey()).providerKey();
		String displayName = requiredText(request.displayName(), "PHYSICAL_ACCESS_PROVIDER_NAME_REQUIRED",
				"Indica un nombre para la conexion de acceso fisico.");
		String connectorKey = trimToNull(request.connectorKey());
		if (request.enabled() && connectorKey == null) {
			throw new BusinessException(
					"PHYSICAL_ACCESS_CONNECTOR_REQUIRED",
					"Indica el identificador del conector local antes de activar la integracion."
			);
		}
		ConnectionRecord currentConnection = physicalAccessRepository
				.connection(principal.organizationId(), principal.branchId()).orElse(null);
		if (currentConnection != null && !currentConnection.providerKey().equals(providerKey)) {
			throw new BusinessException(
					"PHYSICAL_ACCESS_PROVIDER_IMMUTABLE",
					"La conexión V1 conserva su proveedor; revoca el Connector antes de una migración asistida."
			);
		}
		if (connectorKey != null) {
			ConnectionRecord keyOwner = physicalAccessRepository.connectionByConnectorKey(connectorKey).orElse(null);
			if (keyOwner != null && (currentConnection == null || !keyOwner.id().equals(currentConnection.id()))) {
				throw new BusinessException(
						"PHYSICAL_ACCESS_CONNECTOR_KEY_IN_USE",
						"El identificador del Connector ya está asignado a otra conexión."
				);
			}
		}
		List<PhysicalBenefitMappingRecord> mappings = normalizedPhysicalMappings(principal, request.benefitMappings());
		PhysicalProviderConnectionRecord connection = accessRepository.upsertPhysicalProviderConnection(
				principal.organizationId(), principal.branchId(), providerKey, displayName, connectorKey,
				request.enabled(), principal.userId());
		accessRepository.replacePhysicalBenefitMappings(
				principal.organizationId(), principal.branchId(), connection.id(), mappings, principal.userId());
		ConnectionRecord updated = physicalAccessRepository.connection(
				principal.organizationId(), principal.branchId(), connection.id()).orElseThrow();
		physicalSynchronizationService.reconcileConnection(
				updated, "physical-configuration:" + correlationId, null,
				"MAPPING_CONFIGURATION", principal.userId());
		auditService.record(principal, "PHYSICAL_ACCESS_INTEGRATION_CONFIGURED", "ACCESS_PROVIDER_CONNECTION",
				connection.id(), "BACKOFFICE", "provider=" + providerKey + "; mappings=" + mappings.size(), correlationId);
		return physicalIntegration(principal);
	}

	@Transactional(readOnly = true)
	public PhysicalClientStateView physicalClientState(GymOsPrincipal principal, UUID clientId) {
		requireBranch(principal);
		ClientAccessRecord client = accessRepository.clientById(
				principal.organizationId(), principal.branchId(), clientId)
				.orElseThrow(() -> new BusinessException("CLIENT_NOT_FOUND", "El cliente no existe o no esta disponible."));
		ConnectionRecord connection = physicalAccessRepository
				.connection(principal.organizationId(), principal.branchId()).orElse(null);
		List<PhysicalAccessBenefitView> effectiveBenefits = effectivePhysicalBenefits(principal, client.id());
		if (connection == null) {
			return new PhysicalClientStateView(
					client.id(), client.fullName(), null, effectiveBenefits, List.of(), null);
		}
		Map<UUID, PhysicalBenefitMappingRecord> mappingsByBenefit = accessRepository
				.physicalBenefitMappings(principal.organizationId(), principal.branchId(), connection.id())
				.stream()
				.collect(java.util.stream.Collectors.toMap(
						PhysicalBenefitMappingRecord::benefitId,
						mapping -> mapping,
						(first, ignored) -> first,
						LinkedHashMap::new));
		List<PhysicalBenefitMappingView> effectiveMappings = effectiveBenefits.stream()
				.map(benefit -> {
					PhysicalBenefitMappingRecord mapping = mappingsByBenefit.get(benefit.id());
					return new PhysicalBenefitMappingView(
							benefit.id(), benefit.code(), benefit.name(),
							mapping == null ? null : mapping.externalAccessLevelRef(),
							mapping == null ? null : mapping.externalAccessLevelName());
				})
				.toList();
		var link = physicalAccessRepository.clientLink(
				principal.organizationId(), principal.branchId(), connection.id(), client.id()).orElse(null);
		DesiredStateRecord syncState = physicalAccessRepository.desiredState(
				principal.organizationId(), principal.branchId(), connection.id(), client.id()).orElse(null);
		return new PhysicalClientStateView(
				client.id(), client.fullName(),
				link == null ? null : link.externalPersonRef(),
				effectiveBenefits, effectiveMappings, syncState == null ? null : physicalSyncStateView(syncState));
	}

	@Transactional
	public PhysicalClientStateView linkPhysicalClient(
			GymOsPrincipal principal,
			UUID clientId,
			LinkPhysicalClientCommand request,
			String correlationId
	) {
		requireBranch(principal);
		requirePhysicalManagement(principal);
		ClientAccessRecord client = accessRepository.clientById(
				principal.organizationId(), principal.branchId(), clientId)
				.orElseThrow(() -> new BusinessException("CLIENT_NOT_FOUND", "El cliente no existe o no esta disponible."));
		ConnectionRecord connection = requiredPhysicalConnection(principal);
		String externalPersonRef = requiredText(request.externalPersonRef(), "PHYSICAL_ACCESS_PERSON_REQUIRED",
				"Indica la referencia de persona asignada por el proveedor fisico.");
		var duplicate = physicalAccessRepository.clientLinkByExternalRef(
				principal.organizationId(), principal.branchId(), connection.id(), externalPersonRef).orElse(null);
		if (duplicate != null && !duplicate.clientId().equals(client.id())) {
			throw new BusinessException(
					"PHYSICAL_ACCESS_PERSON_DUPLICATE",
					"La referencia externa ya está vinculada a otro socio."
			);
		}
		accessRepository.upsertPhysicalClientLink(
				principal.organizationId(), principal.branchId(), connection.id(), client.id(), externalPersonRef, principal.userId());
		physicalSynchronizationService.reconcileClient(
				connection, client.id(), "physical-client-link:" + correlationId,
				null, "CLIENT_LINK", principal.userId());
		auditService.record(principal, "PHYSICAL_ACCESS_CLIENT_LINKED", "ACCESS_PROVIDER_CLIENT_LINK",
				client.id(), "BACKOFFICE", "provider=" + connection.providerKey(), correlationId);
		return physicalClientState(principal, client.id());
	}

	@Transactional
	public PhysicalClientStateView queuePhysicalSynchronization(
			GymOsPrincipal principal,
			UUID clientId,
			String correlationId
	) {
		requireBranch(principal);
		requirePhysicalManagement(principal);
		ClientAccessRecord client = accessRepository.clientById(
				principal.organizationId(), principal.branchId(), clientId)
				.orElseThrow(() -> new BusinessException("CLIENT_NOT_FOUND", "El cliente no existe o no esta disponible."));
		ConnectionRecord connection = requiredPhysicalConnection(principal);
		if (!connection.enabled()) {
			throw new BusinessException("PHYSICAL_ACCESS_INTEGRATION_DISABLED",
					"Activa la integracion de acceso fisico antes de preparar sincronizaciones.");
		}
		if (physicalAccessRepository.clientLink(
				principal.organizationId(), principal.branchId(), connection.id(), client.id()).isEmpty()) {
			throw new BusinessException("PHYSICAL_ACCESS_CLIENT_LINK_REQUIRED",
					"Vincula primero al socio con su referencia de persona en el proveedor fisico.");
		}
		DesiredStateRecord state = physicalSynchronizationService.reconcileClient(
				connection, client.id(), "admin-reconcile:" + correlationId,
				"RECONCILE", "ADMIN_RECONCILE", principal.userId());
		auditService.record(principal, "PHYSICAL_ACCESS_SYNC_QUEUED", "ACCESS_PROVIDER_SYNC_STATE",
				state.id(), "BACKOFFICE", "action=" + state.action() + "; status=" + state.status(), correlationId);
		return physicalClientState(principal, client.id());
	}

	@Transactional
	public int reconcileAllPhysicalClients(GymOsPrincipal principal, String correlationId) {
		requireBranch(principal);
		requirePhysicalManagement(principal);
		ConnectionRecord connection = requiredPhysicalConnection(principal);
		int queued = physicalSynchronizationService.reconcileConnection(
				connection, "admin-reconcile-all:" + correlationId, "RECONCILE",
				"ADMIN_RECONCILE", principal.userId());
		auditService.record(principal, "PHYSICAL_ACCESS_RECONCILE_ALL", "ACCESS_PROVIDER_CONNECTION",
				connection.id(), "BACKOFFICE", "clients=" + queued, correlationId);
		return queued;
	}

	@Transactional
	public PhysicalDeviceLinkView linkPhysicalDevice(
			GymOsPrincipal principal,
			UUID deviceId,
			String externalDeviceRef,
			String externalDeviceName,
			String authorizationMode,
			String correlationId
	) {
		requireBranch(principal);
		requirePhysicalManagement(principal);
		AccessDeviceRecord device = accessDevice(principal, deviceId);
		if (!providerCatalog.isProductive(device.provider())) {
			throw new BusinessException(
					"PHYSICAL_ACCESS_DEVICE_NOT_PRODUCTIVE",
					"Un punto simulado no se vincula a recursos físicos externos."
			);
		}
		ConnectionRecord connection = requiredPhysicalConnection(principal);
		if (!connection.providerKey().equals(providerCatalog.normalize(device.provider()))) {
			throw new BusinessException(
					"PHYSICAL_ACCESS_DEVICE_PROVIDER_MISMATCH",
					"El punto no pertenece al proveedor configurado en la sucursal."
			);
		}
		String externalRef = requiredText(externalDeviceRef, "PHYSICAL_ACCESS_DEVICE_REF_REQUIRED",
				"Indica la referencia externa de la puerta o dispositivo.");
		String normalizedAuthorizationMode = normalizeAuthorizationMode(authorizationMode);
		if ("GYMFORT_ONLINE".equals(normalizedAuthorizationMode)) {
			Set<String> reportedCapabilities = Set.copyOf(fromJson(connection.connectorCapabilitiesJson()));
			if (!reportedCapabilities.containsAll(Set.of("EVENT_POLLING", "REMOTE_OPEN"))) {
				throw new BusinessException("PHYSICAL_ONLINE_CAPABILITIES_REQUIRED",
						"El Connector debe reportar sondeo de eventos y apertura remota antes de habilitar GymFort en línea.");
			}
		}
		var duplicate = physicalAccessRepository.deviceLinkByExternalRef(
				principal.organizationId(), principal.branchId(), connection.id(), externalRef).orElse(null);
		if (duplicate != null && !duplicate.accessDeviceId().equals(device.id())) {
			throw new BusinessException(
					"PHYSICAL_ACCESS_DEVICE_REF_DUPLICATE",
					"La referencia externa ya está vinculada a otro punto de acceso."
			);
		}
		DeviceLinkRecord link = physicalAccessRepository.upsertDeviceLink(
				connection, device.id(), externalRef, trimToNull(externalDeviceName),
				normalizedAuthorizationMode,
				principal.userId(), OffsetDateTime.now());
		ensureAuthorizationModesDoNotConflict(principal, connection);
		physicalSynchronizationService.reconcileConnection(
				connection, "physical-device-link:" + link.id() + ":" + safeCorrelationId(correlationId),
				"RECONCILE", "BACKOFFICE", principal.userId());
		auditService.record(principal, "PHYSICAL_ACCESS_DEVICE_LINKED", "ACCESS_PROVIDER_DEVICE_LINK",
				link.id(), "BACKOFFICE", "deviceId=" + device.id(), correlationId);
		return physicalDeviceLinkView(link);
	}

	public PhysicalDeviceLinkView linkPhysicalDevice(
			GymOsPrincipal principal,
			UUID deviceId,
			String externalDeviceRef,
			String externalDeviceName,
			String correlationId
	) {
		return linkPhysicalDevice(
				principal, deviceId, externalDeviceRef, externalDeviceName,
				"PROVIDER_MANAGED", correlationId);
	}

	@Transactional
	public void unlinkPhysicalDevice(GymOsPrincipal principal, UUID deviceId, String correlationId) {
		requireBranch(principal);
		requirePhysicalManagement(principal);
		accessDevice(principal, deviceId);
		ConnectionRecord connection = requiredPhysicalConnection(principal);
		if (!physicalAccessRepository.deleteDeviceLink(
				principal.organizationId(), principal.branchId(), connection.id(), deviceId)) {
			throw new BusinessException("PHYSICAL_ACCESS_DEVICE_LINK_NOT_FOUND", "El punto no tiene un vínculo externo.");
		}
		physicalSynchronizationService.reconcileConnection(
				connection, "physical-device-unlink:" + deviceId + ":" + safeCorrelationId(correlationId),
				"RECONCILE", "BACKOFFICE", principal.userId());
		auditService.record(principal, "PHYSICAL_ACCESS_DEVICE_UNLINKED", "ACCESS_DEVICE",
				deviceId, "BACKOFFICE", null, correlationId);
	}

	@Transactional
	public PhysicalAuthorizationView requestPhysicalAccess(
			ConnectorContext connector,
			PhysicalAuthorizationInput input,
			String correlationId
	) {
		String providerKey = providerCatalog.normalize(input.providerKey());
		if (!connector.providerKey().equals(providerKey)) {
			throw new BusinessException("PHYSICAL_EVENT_PROVIDER_MISMATCH",
					"El evento no pertenece al proveedor autenticado.");
		}
		String externalEventId = requiredText(input.externalEventId(), "PHYSICAL_EVENT_ID_REQUIRED",
				"El evento requiere un identificador externo.");
		String externalDeviceRef = requiredText(input.externalDeviceRef(), "PHYSICAL_EVENT_DEVICE_REQUIRED",
				"El evento requiere un recurso externo.");
		DeviceLinkRecord deviceLink = physicalAccessRepository.deviceLinkByExternalRef(
				connector.organizationId(), connector.branchId(), connector.connectionId(), externalDeviceRef)
				.orElseThrow(() -> new BusinessException("PHYSICAL_EVENT_DEVICE_UNRESOLVED",
						"El recurso externo no está vinculado a un punto de esta sucursal."));
		String externalPersonRef = trimToNull(input.externalPersonRef());
		var clientLink = externalPersonRef == null ? null : physicalAccessRepository.clientLinkByExternalRef(
				connector.organizationId(), connector.branchId(), connector.connectionId(), externalPersonRef)
				.orElse(null);
		String providerDecision = input.providerDecision() == null ? null
				: input.providerDecision().trim().toUpperCase(Locale.ROOT);
		if (providerDecision != null && !Set.of("GRANTED", "DENIED").contains(providerDecision)) {
			throw new BusinessException("PHYSICAL_EVENT_DECISION_INVALID", "La decisión física no es válida.");
		}
		OffsetDateTime now = OffsetDateTime.now();
		OffsetDateTime occurredAt = input.occurredAt() == null ? now : input.occurredAt();
		if (occurredAt.isAfter(now.plusMinutes(5))) {
			throw new BusinessException("PHYSICAL_EVENT_TIME_INVALID", "El evento no puede estar fechado en el futuro.");
		}
		PhysicalAuthorizationRecord authorization = physicalAccessRepository.lockOrCreateAuthorization(
				connector, deviceLink, clientLink == null ? null : clientLink.clientId(), externalEventId,
				externalPersonRef, externalDeviceRef, providerDecision, occurredAt, now);
		if (!authorization.accessDeviceId().equals(deviceLink.accessDeviceId())
				|| !java.util.Objects.equals(authorization.clientId(),
						clientLink == null ? null : clientLink.clientId())
				|| !java.util.Objects.equals(authorization.externalPersonRef(), externalPersonRef)
				|| !authorization.externalDeviceRef().equals(externalDeviceRef)
				|| !java.util.Objects.equals(authorization.providerDecision(), providerDecision)
				|| !authorization.authorizationMode().equals(deviceLink.authorizationMode())
				|| !authorization.occurredAt().toInstant().equals(occurredAt.toInstant())) {
			throw new BusinessException("PHYSICAL_EVENT_ID_COLLISION",
					"El identificador del evento ya fue utilizado con otros datos físicos.");
		}
		if (!"PENDING".equals(authorization.decision())) {
			if (isExpiredPendingOnlineActuation(authorization, now)) {
				authorization = physicalAccessRepository.reportActuation(
						connector, authorization.id(), "FAILED", null,
						"La autorización venció antes de que el Connector confirmara la apertura.", now)
						.orElse(authorization);
				if (authorization.accessLogId() != null) {
					physicalAccessRepository.updateAccessLogCommandStatus(
							connector.organizationId(), connector.branchId(), authorization.accessLogId(),
							"REMOTE_OPEN_EXPIRED");
				}
			}
			return physicalAuthorizationView(authorization);
		}

		String eventType = input.eventType() == null || input.eventType().isBlank()
				? "ACCESS_ATTEMPT" : input.eventType().trim().toUpperCase(Locale.ROOT);
		Map<String, String> metadata = connectorService.sanitizeMetadata(input.metadata());
		AccessDecisionResponse accessDecision;
		if ("PROVIDER_MANAGED".equals(deviceLink.authorizationMode())) {
			String decision = providerDecision == null ? "DENIED" : providerDecision;
			String reason = clientLink == null && externalPersonRef != null
					? "EXTERNAL_PERSON_UNRESOLVED"
					: ("GRANTED".equals(decision) ? "PROVIDER_GRANTED" : "PROVIDER_DENIED");
			var recorded = physicalAccessRepository.insertExternalEvent(
					connector, deviceLink.accessDeviceId(), clientLink == null ? null : clientLink.clientId(),
					providerKey, externalEventId, externalPersonRef, externalDeviceRef, eventType,
					toJson(metadata), decision, reason, occurredAt, safeCorrelationId(correlationId), now);
			accessDecision = accessRepository.decision(recorded.accessLogId())
					.orElseThrow(() -> new BusinessException("ACCESS_LOG_NOT_FOUND",
							"No fue posible recuperar la decisión física."));
		} else if (occurredAt.isBefore(now.minus(connectorService.onlineAuthorizationMaxEventAge()))) {
			AccessDeviceRecord device = accessRepository.deviceById(
					connector.organizationId(), connector.branchId(), deviceLink.accessDeviceId()).orElseThrow();
			ClientAccessRecord client = clientLink == null ? null : resolveClient(
					connectorPrincipal(connector), clientLink.clientId(), null);
			accessDecision = recordDecision(
					connectorPrincipal(connector), device, client, null, null, "STALE_PHYSICAL_EVENT", "DENIED",
					"CONNECTOR", "El evento llegó fuera de la ventana segura para una apertura en línea.",
					now, correlationId);
			physicalAccessRepository.markPhysicalDecision(
					connector, accessDecision.logId(), "NOT_SENT", externalEventId, externalPersonRef,
					externalDeviceRef, eventType, toJson(metadata));
			accessDecision = accessRepository.decision(accessDecision.logId()).orElse(accessDecision);
		} else if (clientLink == null) {
			AccessDeviceRecord device = accessRepository.deviceById(
					connector.organizationId(), connector.branchId(), deviceLink.accessDeviceId()).orElseThrow();
			accessDecision = recordDecision(
					connectorPrincipal(connector), device, null, null, null, "IDENTITY_CHECK", "DENIED",
					"CONNECTOR", "La persona física no está vinculada con un socio de GymFort.", now, correlationId);
			physicalAccessRepository.markPhysicalDecision(
					connector, accessDecision.logId(), "NOT_SENT", externalEventId, externalPersonRef,
					externalDeviceRef, eventType, toJson(metadata));
			accessDecision = accessRepository.decision(accessDecision.logId()).orElse(accessDecision);
		} else {
			accessDecision = requestAccess(
					connectorPrincipal(connector),
					new AccessRequestCommand(clientLink.clientId(), null, deviceLink.deviceCode(),
							"physical:" + connector.connectionId() + ":" + externalEventId),
					correlationId);
			physicalAccessRepository.markPhysicalDecision(
					connector, accessDecision.logId(), "GRANTED".equals(accessDecision.decision())
							? "REMOTE_OPEN_PENDING" : "NOT_SENT",
					externalEventId, externalPersonRef, externalDeviceRef, eventType, toJson(metadata));
			accessDecision = accessRepository.decision(accessDecision.logId()).orElse(accessDecision);
		}

		String actuationStatus = "GYMFORT_ONLINE".equals(deviceLink.authorizationMode())
				&& "GRANTED".equals(accessDecision.decision()) ? "PENDING" : "NOT_REQUIRED";
		authorization = physicalAccessRepository.completeAuthorization(
				connector, authorization.id(), accessDecision.logId(), accessDecision.decision(), actuationStatus, now);
		return physicalAuthorizationView(authorization);
	}

	@Transactional
	public PhysicalAuthorizationView reportPhysicalActuation(
			ConnectorContext connector, UUID authorizationId, String status,
			String externalOperationRef, String error
	) {
		String normalizedStatus = status == null ? "FAILED" : status.trim().toUpperCase(Locale.ROOT);
		if (!Set.of("SUCCEEDED", "FAILED", "UNCERTAIN").contains(normalizedStatus)) {
			throw new BusinessException("PHYSICAL_ACTUATION_STATUS_INVALID",
					"El estado de apertura física no es válido.");
		}
		PhysicalAuthorizationRecord authorization = physicalAccessRepository.reportActuation(
				connector, authorizationId, normalizedStatus, trimToNull(externalOperationRef),
				trimToNull(error), OffsetDateTime.now())
				.orElseThrow(() -> new BusinessException(
						"PHYSICAL_ACTUATION_TRANSITION_INVALID",
						"La autorización no admite ese resultado de apertura."));
		if (authorization.accessLogId() != null) {
			physicalAccessRepository.updateAccessLogCommandStatus(
					connector.organizationId(), connector.branchId(), authorization.accessLogId(),
					"SUCCEEDED".equals(normalizedStatus) ? "REMOTE_OPEN_SUCCEEDED"
							: "UNCERTAIN".equals(normalizedStatus) ? "REMOTE_OPEN_UNCERTAIN" : "REMOTE_OPEN_FAILED");
		}
		return physicalAuthorizationView(authorization);
	}

	@Transactional
	public List<UUID> replaceDeviceBenefits(GymOsPrincipal principal, UUID deviceId, List<UUID> benefitIds, String correlationId) {
		requireBranch(principal);
		requirePhysicalManagement(principal);
		AccessDeviceRecord device = accessDevice(principal, deviceId);
		List<UUID> normalizedIds = benefitIds == null ? List.of() : benefitIds.stream().distinct().toList();
		if (normalizedIds.size() > 1) {
			throw new BusinessException(
					"PHYSICAL_ACCESS_SINGLE_BENEFIT_REQUIRED",
					"La versión V1 permite exactamente un beneficio ACCESS principal por punto físico."
			);
		}
		if (providerCatalog.isProductive(device.provider()) && normalizedIds.isEmpty()) {
			throw new BusinessException(
					"PHYSICAL_ACCESS_BENEFIT_REQUIRED",
					"Un punto físico productivo no puede operar sin un beneficio ACCESS explícito."
			);
		}
		if (!accessRepository.accessBenefitsExist(principal.organizationId(), principal.branchId(), normalizedIds)) {
			throw new BusinessException("ACCESS_BENEFIT_NOT_AVAILABLE", "Uno o mas beneficios de acceso no estan disponibles.");
		}
		accessRepository.replaceDeviceBenefits(
				principal.organizationId(), principal.branchId(), device.id(), principal.userId(), normalizedIds);
		physicalAccessRepository.connection(principal.organizationId(), principal.branchId())
				.ifPresent(connection -> {
					ensureAuthorizationModesDoNotConflict(principal, connection);
					physicalSynchronizationService.reconcileConnection(
							connection, "physical-device-benefit:" + device.id() + ":"
									+ safeCorrelationId(correlationId),
							"RECONCILE", "BACKOFFICE", principal.userId());
				});
		auditService.record(principal, "ACCESS_DEVICE_BENEFITS_REPLACED", "ACCESS_DEVICE", device.id(),
				"BACKOFFICE", "benefits=" + normalizedIds.size(), correlationId);
		return accessRepository.deviceRequiredBenefitIds(
				principal.organizationId(), principal.branchId(), device.id());
	}

	private AccessDeviceRecord accessDevice(GymOsPrincipal principal, UUID deviceId) {
		return accessRepository.deviceById(principal.organizationId(), principal.branchId(), deviceId)
				.orElseThrow(() -> new BusinessException("ACCESS_DEVICE_NOT_FOUND", "El dispositivo no existe en la sucursal."));
	}

	private void ensureAuthorizationModesDoNotConflict(
			GymOsPrincipal principal, ConnectionRecord connection
	) {
		Map<UUID, Set<String>> modesByBenefit = new LinkedHashMap<>();
		for (var mode : accessRepository.physicalBenefitAuthorizationModes(
				principal.organizationId(), principal.branchId(), connection.id())) {
			modesByBenefit.computeIfAbsent(mode.benefitId(), ignored -> new java.util.HashSet<>())
					.add(mode.authorizationMode());
		}
		if (modesByBenefit.values().stream().anyMatch(modes -> modes.size() > 1)) {
			throw new BusinessException("PHYSICAL_AUTHORIZATION_MODE_CONFLICT",
					"Un mismo beneficio no puede abrirse a la vez por HikCentral y por GymFort en línea.");
		}
	}

	private String normalizeDeviceCode(String deviceCode) {
		String normalized = normalizeRequiredText(deviceCode).toUpperCase(Locale.ROOT);
		if (!normalized.matches("[A-Z0-9][A-Z0-9_-]{1,79}")) {
			throw new BusinessException("ACCESS_DEVICE_CODE_INVALID", "El código sólo puede usar letras, números, guiones y guiones bajos.");
		}
		return normalized;
	}

	private String normalizeDeviceType(String deviceType) {
		String normalized = normalizeRequiredText(deviceType).toUpperCase(Locale.ROOT);
		if (!Set.of("TURNSTILE", "DOOR", "GATE", "ZONE", "BIOMETRIC").contains(normalized)) {
			throw new BusinessException("ACCESS_DEVICE_TYPE_INVALID", "El tipo de punto de acceso no es válido.");
		}
		return normalized;
	}

	private String normalizeProvider(String provider) {
		return providerCatalog.requireAvailable(provider).providerKey();
	}

	private String normalizeRequiredText(String value) {
		if (value == null || value.isBlank()) {
			throw new BusinessException("ACCESS_DEVICE_DATA_INVALID", "Completa los datos requeridos del punto de acceso.");
		}
		return value.trim();
	}

	@Transactional
	public AccessDecisionResponse requestAccess(GymOsPrincipal principal, AccessRequestCommand request, String correlationId) {
		requireBranch(principal);
		AccessDeviceRecord device = resolveDevice(principal, request.deviceCode());
		ClientAccessRecord client = resolveClient(principal, request.clientId(), request.identifier());
		OffsetDateTime now = OffsetDateTime.now();
		String source = accessDecisionSource(principal);
		List<UUID> requiredBenefitIds = accessRepository.deviceRequiredBenefitIds(
				principal.organizationId(), principal.branchId(), device.id());
		if (requiredBenefitIds.size() > 1) {
			return recordDecision(principal, device, client, null, null, "CONFIGURATION_CHECK", "DENIED",
					source, "El punto físico tiene más de un beneficio requerido; corrige su configuración V1.",
					now, correlationId);
		}
		if (providerCatalog.isProductive(device.provider()) && requiredBenefitIds.isEmpty()) {
			return recordDecision(principal, device, client, null, null, "CONFIGURATION_CHECK", "DENIED",
					source, "El punto físico no tiene un beneficio ACCESS configurado.", now, correlationId);
		}
		EligibilityDecision eligibility = eligibilityPolicy.evaluate(EligibilityRequest.access(
				principal.organizationId(), principal.branchId(), client.id(), BusinessTime.today(),
				Set.copyOf(requiredBenefitIds)));
		ActiveMembership membership = eligibility.membership();

		if (eligibility.eligible()) {
			return recordDecision(principal, device, client, membership.id(), null, "MEMBERSHIP_CHECK", "GRANTED", source, "Membresia activa hasta " + membership.endsOn() + ".", now, correlationId);
		}
		if (eligibility.reason() == Reason.CLIENT_NOT_ACTIVE) {
			return recordDecision(principal, device, client, null, null, "MEMBERSHIP_CHECK", "DENIED",
					source, "Cliente inactivo o suspendido.", now, correlationId);
		}

		String visitIdempotencyKey = "access:" + (
				request.idempotencyKey() == null || request.idempotencyKey().isBlank()
						? safeCorrelationId(correlationId)
						: request.idempotencyKey().trim());
		Optional<VisitPackageAccessResult> packageAccess = visitPackages.authorizeForAccess(
				principal.organizationId(), principal.branchId(), client.id(), requiredBenefitIds,
				device.id(), visitIdempotencyKey, now);
		VisitPackageAccessResult deniedPackageUsage = null;
		if (packageAccess.isPresent()) {
			VisitPackageAccessResult packageUsage = packageAccess.get();
			if (!packageUsage.clientId().equals(client.id())
					|| !packageUsage.branchId().equals(principal.branchId())
					|| !java.util.Objects.equals(packageUsage.accessDeviceId(), device.id())) {
				throw new BusinessException(
						"ACCESS_IDEMPOTENCY_KEY_REUSED",
						"La llave de acceso ya corresponde a otro evento.");
			}
			if (packageUsage.accessLogId() != null) {
				return accessRepository.decision(packageUsage.accessLogId())
						.orElseThrow(() -> new BusinessException(
								"ACCESS_LOG_NOT_FOUND", "No fue posible recuperar la decisión de acceso."));
			}
			if (!packageUsage.granted()) {
				deniedPackageUsage = packageUsage;
			} else {
				String packageAccessType = packageUsage.consumedVisit()
					? "VISIT_PACKAGE" : "VISIT_PACKAGE_INCLUDED";
				String packageReason = packageUsage.consumedVisit()
					? "Paquete " + packageUsage.packageName() + ": " + packageUsage.remainingAfter()
							+ " visita(s) restante(s)."
					: "Acceso incluido en la visita registrada hoy en esta sucursal. Saldo: "
							+ packageUsage.remainingAfter() + ".";
				return recordPackageAwareDecision(
						principal, device, client, null, null, packageAccessType, "GRANTED",
						packageReason, now, correlationId, packageUsage);
			}
		}

		if (eligibility.reason() == Reason.BENEFIT_NOT_GRANTED) {
			return recordPackageAwareDecision(
					principal, device, client, membership.id(), null,
					deniedPackageUsage == null ? "BENEFIT_CHECK" : "VISIT_PACKAGE_INCLUDED", "DENIED",
					deniedPackageUsage == null
							? "El plan no incluye el acceso requerido por el dispositivo."
							: "Este acceso incluido requiere una visita del mismo paquete registrada hoy en esta sucursal.",
					now, correlationId, deniedPackageUsage);
		}

		TemporaryPassRecord pass = accessRepository.validTemporaryPass(principal.organizationId(), principal.branchId(), client.id(), now)
				.orElse(null);
		if (pass != null) {
			boolean consumed = accessRepository.consumeTemporaryPass(
					principal.organizationId(), principal.branchId(), pass.id(), now);
			if (!consumed) {
				return recordPackageAwareDecision(
						principal, device, client, null, pass.id(), "TEMPORARY_PASS", "DENIED",
						"La autorizacion temporal ya fue consumida o vencio.", now, correlationId,
						deniedPackageUsage);
			}
			return recordPackageAwareDecision(
					principal, device, client, null, pass.id(), "TEMPORARY_PASS", "GRANTED",
					"Autorizacion temporal vigente.", now, correlationId, deniedPackageUsage);
		}

		return recordPackageAwareDecision(
				principal, device, client, null, null,
				deniedPackageUsage == null ? "MEMBERSHIP_CHECK" : "VISIT_PACKAGE_INCLUDED", "DENIED",
				deniedPackageUsage == null
						? "Sin membresia activa ni pase temporal vigente."
						: "Este acceso incluido requiere una visita del mismo paquete registrada hoy en esta sucursal.",
				now, correlationId, deniedPackageUsage);
	}

	private AccessDecisionResponse recordPackageAwareDecision(
			GymOsPrincipal principal, AccessDeviceRecord device, ClientAccessRecord client,
			UUID membershipId, UUID temporaryPassId, String accessType, String decision,
			String reason, OffsetDateTime occurredAt, String correlationId,
			VisitPackageAccessResult packageUsage
	) {
		AccessDecisionResponse response = recordDecision(
				principal, device, client, membershipId, temporaryPassId, accessType, decision,
				accessDecisionSource(principal), reason, occurredAt, correlationId);
		if (packageUsage != null) {
			visitPackages.linkAccessLog(
					principal.organizationId(), packageUsage.usageId(), packageUsage.usageMode(),
					response.logId());
		}
		return response;
	}

	@Transactional
	public AccessDecisionResponse manualOpen(GymOsPrincipal principal, ManualOpenCommand request, String correlationId) {
		requireBranch(principal);
		requireManualOpenPermission(principal);
		if (request.deviceCode() == null || request.deviceCode().isBlank()) {
			throw new BusinessException("ACCESS_DEVICE_REQUIRED", "Selecciona explícitamente el punto para la apertura manual.");
		}
		AccessDeviceRecord device = resolveDevice(principal, request.deviceCode());
		ClientAccessRecord client = request.clientId() == null ? null : accessRepository.clientById(
				principal.organizationId(), principal.branchId(), request.clientId())
				.orElseThrow(() -> new BusinessException("CLIENT_NOT_FOUND", "El cliente no existe o no esta disponible."));
		return recordDecision(principal, device, client, null, null, "MANUAL_OPEN", "GRANTED", "RECEPTION", request.reason(), OffsetDateTime.now(), correlationId);
	}

	@Transactional
	public TemporaryPassRecord createTemporaryPass(GymOsPrincipal principal, TemporaryPassCommand request, String correlationId) {
		requireBranch(principal);
		ClientAccessRecord client = accessRepository.clientById(
				principal.organizationId(), principal.branchId(), request.clientId())
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
		throw new BusinessException(
				"VISIT_PASS_LEGACY_READ_ONLY",
				"Los pases históricos son sólo de consulta. Vende un paquete desde Catálogo o desde el expediente del socio.");
	}

	@Transactional
	public VisitPassRecord consumeVisitPass(GymOsPrincipal principal, UUID visitPassId, ConsumeVisitPassCommand request, String correlationId) {
		requireBranch(principal);
		throw new BusinessException(
				"VISIT_PASS_LEGACY_READ_ONLY",
				"Los pases históricos ya no se consumen manualmente. Usa el acceso normal para descontar un paquete vigente.");
	}

	private List<PhysicalAccessBenefitView> effectivePhysicalBenefits(GymOsPrincipal principal, UUID clientId) {
		EligibilityDecision eligibility = eligibilityPolicy.evaluate(EligibilityRequest.access(
				principal.organizationId(), principal.branchId(), clientId, BusinessTime.today(), Set.of()));
		if (eligibility.reason() == Reason.CLIENT_NOT_FOUND) {
			throw new BusinessException("CLIENT_NOT_FOUND", "El cliente no existe o no esta disponible.");
		}
		if (!eligibility.eligible()) {
			return List.of();
		}
		return eligibility.benefits().stream()
				.filter(benefit -> benefit.benefitType() == EligibilityPolicy.BenefitType.ACCESS)
				.map(benefit -> new PhysicalAccessBenefitView(benefit.id(), benefit.code(), benefit.name()))
				.toList();
	}

	private List<PhysicalBenefitMappingRecord> normalizedPhysicalMappings(
			GymOsPrincipal principal,
			List<ConfigurePhysicalIntegrationCommand.BenefitMapping> requestedMappings
	) {
		Map<UUID, PhysicalBenefitMappingRecord> byBenefit = new LinkedHashMap<>();
		Set<String> externalRefs = new java.util.HashSet<>();
		for (ConfigurePhysicalIntegrationCommand.BenefitMapping mapping : requestedMappings) {
			if (mapping == null || mapping.benefitId() == null) {
				throw new BusinessException("PHYSICAL_ACCESS_MAPPING_INVALID", "Cada mapeo debe incluir un beneficio de acceso.");
			}
			String externalAccessLevelRef = requiredText(mapping.externalAccessLevelRef(),
					"PHYSICAL_ACCESS_MAPPING_INVALID", "Cada mapeo debe incluir la referencia del nivel externo.");
			if (byBenefit.containsKey(mapping.benefitId()) || !externalRefs.add(externalAccessLevelRef)) {
				throw new BusinessException("PHYSICAL_ACCESS_MAPPING_DUPLICATE",
						"No repitas beneficios ni niveles externos dentro de la misma configuracion.");
			}
			byBenefit.put(mapping.benefitId(), new PhysicalBenefitMappingRecord(
					mapping.benefitId(), null, null, externalAccessLevelRef, trimToNull(mapping.externalAccessLevelName())));
		}
		List<UUID> benefitIds = List.copyOf(byBenefit.keySet());
		if (!accessRepository.accessBenefitsExist(principal.organizationId(), principal.branchId(), benefitIds)) {
			throw new BusinessException("ACCESS_BENEFIT_NOT_AVAILABLE",
					"Uno o mas beneficios de acceso no estan disponibles en esta sucursal.");
		}
		return List.copyOf(byBenefit.values());
	}

	private ConnectionRecord requiredPhysicalConnection(GymOsPrincipal principal) {
		return physicalAccessRepository.connection(principal.organizationId(), principal.branchId())
				.orElseThrow(() -> new BusinessException("PHYSICAL_ACCESS_INTEGRATION_REQUIRED",
						"Configura primero la integracion de acceso fisico para esta sucursal."));
	}

	private PhysicalIntegrationConnectionView defaultPhysicalConnection() {
		return new PhysicalIntegrationConnectionView(
				null, "", "", null, false, "DISABLED", false,
				null, null, null, 0, 0, null, List.of(), null);
	}

	private PhysicalIntegrationConnectionView physicalConnectionView(ConnectionRecord connection) {
		var health = connectorService.connectionHealth(connection);
		return new PhysicalIntegrationConnectionView(
				connection.id(), connection.providerKey(), connection.displayName(), connection.connectorKey(),
				connection.enabled(), health.status(), health.credentialActive(), health.lastHeartbeatAt(),
				health.lastSynchronizedAt(), health.lastError(), health.pendingJobs(), health.failedJobs(),
				health.connectorVersion(), fromJson(connection.connectorCapabilitiesJson()),
				connection.connectorActivatedAt());
	}

	private PhysicalAccessBenefitView physicalAccessBenefitView(PhysicalAccessBenefitRecord benefit) {
		return new PhysicalAccessBenefitView(benefit.id(), benefit.code(), benefit.name());
	}

	private PhysicalBenefitMappingView physicalBenefitMappingView(PhysicalBenefitMappingRecord mapping) {
		return new PhysicalBenefitMappingView(
				mapping.benefitId(), mapping.benefitCode(), mapping.benefitName(),
				mapping.externalAccessLevelRef(), mapping.externalAccessLevelName());
	}

	private PhysicalDeviceLinkView physicalDeviceLinkView(DeviceLinkRecord link) {
		return new PhysicalDeviceLinkView(
				link.id(), link.accessDeviceId(), link.deviceName(), link.deviceCode(),
				link.externalDeviceRef(), link.externalDeviceName(), link.authorizationMode(), link.updatedAt());
	}

	private String normalizeAuthorizationMode(String value) {
		String normalized = value == null || value.isBlank()
				? "PROVIDER_MANAGED" : value.trim().toUpperCase(Locale.ROOT);
		if (!Set.of("PROVIDER_MANAGED", "GYMFORT_ONLINE").contains(normalized)) {
			throw new BusinessException("PHYSICAL_AUTHORIZATION_MODE_INVALID",
					"El modo de autorización física no es válido.");
		}
		return normalized;
	}

	private GymOsPrincipal connectorPrincipal(ConnectorContext connector) {
		return new GymOsPrincipal(
				null, connector.organizationId(), connector.branchId(),
				"physical-connector", "GymFort Physical Connector", Set.of());
	}

	private PhysicalAuthorizationView physicalAuthorizationView(PhysicalAuthorizationRecord authorization) {
		String reason = authorization.accessLogId() == null ? null
				: accessRepository.decision(authorization.accessLogId()).map(AccessDecisionResponse::reason).orElse(null);
		return new PhysicalAuthorizationView(
				authorization.id(), authorization.accessLogId(), authorization.externalEventId(),
				authorization.decision(), authorization.authorizationMode(), authorization.actuationStatus(),
				"GYMFORT_ONLINE".equals(authorization.authorizationMode())
						&& "GRANTED".equals(authorization.decision())
						&& "PENDING".equals(authorization.actuationStatus()),
				reason, authorization.updatedAt());
	}

	private boolean isExpiredPendingOnlineActuation(
			PhysicalAuthorizationRecord authorization, OffsetDateTime now
	) {
		return "GYMFORT_ONLINE".equals(authorization.authorizationMode())
				&& "GRANTED".equals(authorization.decision())
				&& "PENDING".equals(authorization.actuationStatus())
				&& authorization.occurredAt().isBefore(
						now.minus(connectorService.onlineAuthorizationMaxEventAge()));
	}

	private String toJson(Map<String, String> value) {
		try {
			return objectMapper.writeValueAsString(value == null ? Map.of() : value);
		} catch (JsonProcessingException exception) {
			throw new BusinessException("PHYSICAL_EVENT_METADATA_INVALID",
					"Los metadatos del evento físico no son válidos.");
		}
	}

	private PhysicalSyncStateView physicalSyncStateView(DesiredStateRecord state) {
		return new PhysicalSyncStateView(
				fromJson(state.desiredAccessLevelRefsJson()), state.action(), state.status(),
				state.requestedAt(), state.lastAttemptAt(), state.lastSynchronizedAt(), state.lastError(),
				state.desiredVersion(), state.blockedReasonCode());
	}

	private List<String> fromJson(String value) {
		if (value == null || value.isBlank()) {
			return List.of();
		}
		try {
			return objectMapper.readValue(value, new TypeReference<List<String>>() { });
		} catch (JsonProcessingException exception) {
			throw new IllegalStateException("El estado de sincronizacion de acceso almacenado es invalido.", exception);
		}
	}

	private String requiredText(String value, String code, String message) {
		String normalized = trimToNull(value);
		if (normalized == null) {
			throw new BusinessException(code, message);
		}
		return normalized;
	}

	private String trimToNull(String value) {
		if (value == null) {
			return null;
		}
		String normalized = value.trim();
		return normalized.isEmpty() ? null : normalized;
	}

	private void requireManualOpenPermission(GymOsPrincipal principal) {
		if (!principal.permissions().contains("access.manual.open")) {
			throw new BusinessException("ACCESS_MANUAL_OPEN_FORBIDDEN",
					"No tienes permiso para realizar una apertura manual.");
		}
	}

	private void requirePhysicalManagement(GymOsPrincipal principal) {
		if (!principal.permissions().contains("access.physical.manage")) {
			throw new BusinessException(
					"ACCESS_PHYSICAL_MANAGE_FORBIDDEN",
					"No tienes permiso para administrar la integración física."
			);
		}
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
				safeCorrelationId(correlationId)
		);
		String auditAction = "GRANTED".equals(decision) ? "ACCESS_GRANTED" : "ACCESS_DENIED";
		if (principal.userId() == null && "CONNECTOR".equals(source)) {
			auditService.recordSystem(
					principal.organizationId(), principal.branchId(), null, auditAction,
					"ACCESS_LOG", logId, source, reason, correlationId);
		} else {
			auditService.record(
					principal, auditAction, "ACCESS_LOG", logId, source, reason, correlationId);
		}
		return accessRepository.decision(logId)
				.orElseThrow(() -> new BusinessException("ACCESS_LOG_NOT_FOUND", "No fue posible recuperar la decision de acceso."));
	}

	private String accessDecisionSource(GymOsPrincipal principal) {
		return principal.userId() == null && "physical-connector".equals(principal.username())
				? "CONNECTOR" : "RECEPTION";
	}

	private UUID safeCorrelationId(String correlationId) {
		try {
			return UUID.fromString(correlationId);
		} catch (RuntimeException exception) {
			return UUID.nameUUIDFromBytes(("gymfort:" + correlationId).getBytes(java.nio.charset.StandardCharsets.UTF_8));
		}
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
			return accessRepository.clientById(principal.organizationId(), principal.branchId(), clientId)
					.orElseThrow(() -> new BusinessException("CLIENT_NOT_FOUND", "El cliente no existe o no esta disponible."));
		}
		if (identifier != null && !identifier.isBlank()) {
			ClientAccessRecord candidate = accessRepository.clientByIdentifier(principal.organizationId(), identifier)
					.orElseThrow(() -> new BusinessException("CLIENT_NOT_FOUND", "El cliente no existe o no esta disponible."));
			return accessRepository.clientById(principal.organizationId(), principal.branchId(), candidate.id())
					.orElseThrow(() -> new BusinessException("CLIENT_NOT_FOUND", "El cliente no está visible en esta sucursal."));
		}
		throw new BusinessException("ACCESS_CLIENT_REQUIRED", "Selecciona un cliente o captura un identificador.");
	}

	public record PhysicalIntegrationView(
			List<PhysicalAccessProviderCatalog.ProviderDescriptor> providers,
			PhysicalIntegrationConnectionView connection,
			List<PhysicalAccessBenefitView> availableBenefits,
			List<PhysicalBenefitMappingView> mappings,
			List<PhysicalDeviceLinkView> deviceLinks
	) {
	}

	public record PhysicalIntegrationConnectionView(
			UUID id,
			String providerKey,
			String displayName,
			String connectorKey,
			boolean enabled,
			String syncStatus,
			boolean credentialActive,
			OffsetDateTime lastHeartbeatAt,
			OffsetDateTime lastSynchronizedAt,
			String lastError,
			int pendingJobs,
			int failedJobs,
			String connectorVersion,
			List<String> connectorCapabilities,
			OffsetDateTime connectorActivatedAt
	) {
		public PhysicalIntegrationConnectionView {
			connectorCapabilities = connectorCapabilities == null ? List.of() : List.copyOf(connectorCapabilities);
		}
	}

	public record PhysicalAccessBenefitView(UUID id, String code, String name) {
	}

	public record PhysicalBenefitMappingView(
			UUID benefitId,
			String benefitCode,
			String benefitName,
			String externalAccessLevelRef,
			String externalAccessLevelName
	) {
	}

	public record PhysicalDeviceLinkView(
			UUID id,
			UUID accessDeviceId,
			String deviceName,
			String deviceCode,
			String externalDeviceRef,
			String externalDeviceName,
			String authorizationMode,
			OffsetDateTime updatedAt
	) {
	}

	public record PhysicalAuthorizationInput(
			String providerKey,
			String externalEventId,
			String externalPersonRef,
			String externalDeviceRef,
			OffsetDateTime occurredAt,
			String providerDecision,
			String eventType,
			Map<String, String> metadata
	) {
	}

	public record PhysicalAuthorizationView(
			UUID authorizationId,
			UUID accessLogId,
			String externalEventId,
			String decision,
			String authorizationMode,
			String actuationStatus,
			boolean shouldOpen,
			String reason,
			OffsetDateTime updatedAt
	) {
	}

	public record PhysicalClientStateView(
			UUID clientId,
			String clientName,
			String externalPersonRef,
			List<PhysicalAccessBenefitView> effectiveBenefits,
			List<PhysicalBenefitMappingView> effectiveMappings,
			PhysicalSyncStateView syncState
	) {
	}

	public record PhysicalSyncStateView(
			List<String> desiredAccessLevelRefs,
			String action,
			String status,
			OffsetDateTime requestedAt,
			OffsetDateTime lastAttemptAt,
			OffsetDateTime lastSynchronizedAt,
			String lastError,
			long desiredVersion,
			String blockedReasonCode
	) {
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
