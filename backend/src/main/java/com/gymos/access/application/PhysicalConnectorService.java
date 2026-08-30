package com.gymos.access.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymos.access.infrastructure.AccessRepository;
import com.gymos.access.infrastructure.PhysicalAccessRepository;
import com.gymos.access.infrastructure.PhysicalAccessRepository.ConnectionRecord;
import com.gymos.access.infrastructure.PhysicalAccessRepository.ConnectorContext;
import com.gymos.access.infrastructure.PhysicalAccessRepository.JobCounts;
import com.gymos.access.infrastructure.PhysicalAccessRepository.PersonCandidateRecord;
import com.gymos.access.infrastructure.PhysicalAccessRepository.DeviceCandidateRecord;
import com.gymos.access.infrastructure.PhysicalAccessRepository.SyncJobRecord;
import com.gymos.audit.api.AuditService;
import com.gymos.common.api.BusinessException;
import com.gymos.common.api.ApiAuthenticationException;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.common.security.RateLimiter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PhysicalConnectorService {
	private static final Logger LOGGER = LoggerFactory.getLogger(PhysicalConnectorService.class);
	private static final Duration ACTIVATION_TTL = Duration.ofMinutes(15);
	private static final Duration OFFLINE_AFTER = Duration.ofMinutes(5);
	private static final Set<String> SENSITIVE_METADATA_KEYS = Set.of(
			"BIOMETRIC", "BIOMETRICS", "FACE", "FACE_IMAGE", "FINGERPRINT", "TEMPLATE",
			"PHOTO", "IMAGE", "PASSWORD", "SECRET", "TOKEN", "CREDENTIAL", "API_KEY",
			"IP_ADDRESS", "LAN_ADDRESS", "URL", "HOST", "HOSTNAME", "PORT", "ENDPOINT", "MAC_ADDRESS"
	);
	private static final Pattern NETWORK_DIAGNOSTIC = Pattern.compile(
			"(?i)(?:https?|tcp)://|(?<!\\d)(?:\\d{1,3}\\.){3}\\d{1,3}(?!\\d)");
	private static final Pattern SECRET_DIAGNOSTIC = Pattern.compile(
			"(?i)\\b(?:password|secret|token|credential|api[-_ ]?key)\\b\\s*[:=]|\\bgf[as]_");

	private final PhysicalAccessRepository repository;
	private final AccessRepository accessRepository;
	private final PhysicalAccessSynchronizationService synchronizationService;
	private final PhysicalAccessProviderCatalog providerCatalog;
	private final PasswordEncoder passwordEncoder;
	private final ObjectMapper objectMapper;
	private final AuditService auditService;
	private final PhysicalConnectorSecurityAudit securityAudit;
	private final RateLimiter rateLimiter;
	private final PhysicalConnectorProperties properties;
	private final String dummyCredentialHash;
	private final SecureRandom secureRandom = new SecureRandom();

	public PhysicalConnectorService(
			PhysicalAccessRepository repository,
			AccessRepository accessRepository,
			PhysicalAccessSynchronizationService synchronizationService,
			PhysicalAccessProviderCatalog providerCatalog,
			PasswordEncoder passwordEncoder,
			ObjectMapper objectMapper,
			AuditService auditService,
			PhysicalConnectorSecurityAudit securityAudit,
			RateLimiter rateLimiter,
			PhysicalConnectorProperties properties
	) {
		this.repository = repository;
		this.accessRepository = accessRepository;
		this.synchronizationService = synchronizationService;
		this.providerCatalog = providerCatalog;
		this.passwordEncoder = passwordEncoder;
		this.objectMapper = objectMapper;
		this.auditService = auditService;
		this.securityAudit = securityAudit;
		this.rateLimiter = rateLimiter;
		this.properties = properties;
		this.dummyCredentialHash = passwordEncoder.encode("physical-connector-dummy-" + UUID.randomUUID());
	}

	Duration onlineAuthorizationMaxEventAge() {
		return properties.onlineAuthorizationMaxEventAge();
	}

	@Transactional
	public ActivationCodeView issueActivation(
			GymOsPrincipal principal, String purpose, String correlationId
	) {
		requirePhysicalManagement(principal);
		ConnectionRecord connection = requiredConnection(principal);
		if (!connection.enabled()) {
			throw new BusinessException(
					"PHYSICAL_ACCESS_INTEGRATION_DISABLED",
					"Activa la conexión física antes de generar un código de activación."
			);
		}
		if (connection.connectorKey() == null || connection.connectorKey().isBlank()) {
			throw new BusinessException(
					"PHYSICAL_ACCESS_CONNECTOR_REQUIRED",
					"Configura un identificador público y único para el Connector."
			);
		}
		providerCatalog.requireProductive(connection.providerKey());
		String normalizedPurpose = "ROTATION".equalsIgnoreCase(purpose) ? "ROTATION" : "ACTIVATION";
		OffsetDateTime now = OffsetDateTime.now();
		repository.revokeOpenActivations(connection.id(), now);
		String activationCode = randomToken("gfa_", 24);
		OffsetDateTime expiresAt = now.plus(ACTIVATION_TTL);
		var activation = repository.createActivation(
				connection, passwordEncoder.encode(activationCode), normalizedPurpose,
				expiresAt, principal.userId(), now);
		auditService.record(
				principal, "PHYSICAL_CONNECTOR_ACTIVATION_ISSUED", "ACCESS_CONNECTOR_ACTIVATION",
				activation.id(), "BACKOFFICE", "purpose=" + normalizedPurpose, correlationId);
		return new ActivationCodeView(
				connection.connectorKey(), activationCode, normalizedPurpose, expiresAt,
				"El código sólo se muestra ahora y no contiene credenciales de HikCentral.");
	}

	@Transactional
	public ConnectorActivationView activate(
			String connectorKey, String activationCode, String originIp, String correlationId
	) {
		String safeKey = requiredText(connectorKey, "CONNECTOR_KEY_REQUIRED", "Indica el identificador del Connector.", 120);
		String safeCode = requiredText(activationCode, "CONNECTOR_ACTIVATION_CODE_REQUIRED", "Indica el código de activación.", 200);
		AbuseKeys abuseKeys = abuseKeys("activate", safeKey, originIp);
		enforceNotLimited(
				abuseKeys, properties.activationMaxFailures(),
				properties.activationFailureWindow(), correlationId);
		ConnectionRecord connection = repository.connectionByConnectorKey(safeKey).orElse(null);
		if (connection != null && connection.enabled()) {
			providerCatalog.requireProductive(connection.providerKey());
		}
		OffsetDateTime now = OffsetDateTime.now();
		var activation = connection == null || !connection.enabled()
				? null : repository.openActivationForUpdate(connection.id()).orElse(null);
		boolean codeMatches = passwordEncoder.matches(
				safeCode, activation == null ? dummyCredentialHash : activation.activationCodeHash());
		boolean valid = connection != null && connection.enabled() && activation != null
				&& !activation.expiresAt().isBefore(now) && codeMatches;
		if (!valid || !repository.consumeActivation(activation.id(), now)) {
			throw authenticationFailure(
					connection, safeKey, correlationId, abuseKeys,
					properties.activationMaxFailures(), properties.activationFailureWindow());
		}

		String connectorSecret = randomToken("gfs_", 32);
		repository.revokeActiveCredentials(connection.id(), now);
		var credential = repository.createCredential(
				connection, passwordEncoder.encode(connectorSecret), null, now);
		repository.markConnectionActivated(connection.id(), now);
		auditService.recordSystem(
				connection.organizationId(), connection.branchId(), null,
				"PHYSICAL_CONNECTOR_ACTIVATED", "ACCESS_CONNECTOR_CREDENTIAL", credential.id(),
				"CONNECTOR", "purpose=" + activation.purpose(), correlationId);
		return new ConnectorActivationView(
				credential.id(), connection.connectorKey(), connection.providerKey(), connectorSecret,
				credential.activatedAt(),
				"Guarda el secreto en el almacén seguro del Connector: GymFort no volverá a mostrarlo.");
	}

	@Transactional
	public void revoke(GymOsPrincipal principal, String correlationId) {
		requirePhysicalManagement(principal);
		ConnectionRecord connection = requiredConnection(principal);
		OffsetDateTime now = OffsetDateTime.now();
		repository.revokeOpenActivations(connection.id(), now);
		repository.revokeActiveCredentials(connection.id(), now);
		repository.markConnectionRevoked(connection.id(), now);
		auditService.record(
				principal, "PHYSICAL_CONNECTOR_REVOKED", "ACCESS_PROVIDER_CONNECTION", connection.id(),
				"BACKOFFICE", "Credencial de Connector revocada", correlationId);
	}

	@Transactional
	public ConnectorContext authenticate(
			String connectorKey, String connectorSecret, String originIp, String correlationId
	) {
		String safeKey = trim(connectorKey, 120);
		String safeSecret = trim(connectorSecret, 200);
		AbuseKeys abuseKeys = abuseKeys("authenticate", safeKey, originIp);
		enforceNotLimited(
				abuseKeys, properties.authenticationMaxFailures(),
				properties.authenticationFailureWindow(), correlationId);
		ConnectionRecord connection = safeKey == null
				? null : repository.connectionByConnectorKey(safeKey).orElse(null);
		var credential = connection == null ? null : repository.activeCredential(connection.id()).orElse(null);
		boolean secretMatches = passwordEncoder.matches(
				safeSecret == null ? "" : safeSecret,
				credential == null ? dummyCredentialHash : credential.secretHash());
		if (safeKey == null || safeSecret == null || connection == null || !connection.enabled()
				|| credential == null || !secretMatches) {
			throw authenticationFailure(
					connection, safeKey, correlationId, abuseKeys,
					properties.authenticationMaxFailures(), properties.authenticationFailureWindow());
		}
		OffsetDateTime now = OffsetDateTime.now();
		repository.touchCredentialIfStale(
				credential.id(), now, now.minus(properties.lastAuthenticatedWriteInterval()));
		return new ConnectorContext(
				credential.id(), connection.organizationId(), connection.branchId(), connection.id(),
				connection.providerKey(), connection.connectorKey());
	}

	@Transactional
	public ConnectorHealthView heartbeat(
			ConnectorContext connector,
			String reportedStatus,
			String connectorVersion,
			String instanceId,
			Set<String> capabilities,
			String lastError
	) {
		String status = reportedStatus == null || reportedStatus.isBlank()
				? "HEALTHY" : reportedStatus.trim().toUpperCase(Locale.ROOT);
		if (!Set.of("HEALTHY", "DEGRADED").contains(status)) {
			throw new BusinessException("CONNECTOR_HEARTBEAT_STATUS_INVALID", "El estado del heartbeat no es válido.");
		}
		String safeError = "HEALTHY".equals(status) ? null
				: safeDiagnostic(trim(lastError, 1000));
		List<String> safeCapabilities = normalizeCapabilities(capabilities);
		OffsetDateTime now = OffsetDateTime.now();
		repository.updateHeartbeat(
				connector, status, trim(connectorVersion, 80),
				safeDiagnostic(trim(instanceId, 120)), toJson(safeCapabilities), safeError, now);
		JobCounts counts = repository.jobCounts(
				connector.organizationId(), connector.branchId(), connector.connectionId());
		return new ConnectorHealthView(status, now, counts.pending(), counts.failed());
	}

	public ConnectorHealthView heartbeat(
			ConnectorContext connector, String reportedStatus, String connectorVersion,
			String instanceId, String lastError
	) {
		return heartbeat(connector, reportedStatus, connectorVersion, instanceId, Set.of(), lastError);
	}

	@Transactional
	public List<ConnectorJobView> claimJobs(
			ConnectorContext connector, int requestedLimit, int requestedLeaseSeconds
	) {
		int limit = Math.max(1, Math.min(requestedLimit, 50));
		int leaseSeconds = Math.max(30, Math.min(requestedLeaseSeconds, 300));
		OffsetDateTime now = OffsetDateTime.now();
		return repository.claimJobs(connector, limit, now, now.plusSeconds(leaseSeconds)).stream()
				.map(this::jobView)
				.toList();
	}

	@Transactional
	public ConnectorJobView completeJob(
			ConnectorContext connector, UUID jobId, UUID claimToken, String externalOperationRef
	) {
		OffsetDateTime now = OffsetDateTime.now();
		var completed = repository.completeJob(
				connector, jobId, requiredClaimToken(claimToken),
				safeDiagnostic(trim(externalOperationRef, 180)), now);
		if (completed.isPresent()) {
			return jobView(completed.get());
		}
		SyncJobRecord current = requiredJob(connector, jobId);
		if ("SUCCEEDED".equals(current.status())) {
			return jobView(current);
		}
		throw new BusinessException(
				"CONNECTOR_JOB_LEASE_INVALID",
				"El trabajo no está reclamado por esta credencial o su lease venció."
		);
	}

	@Transactional
	public ConnectorJobView failJob(
			ConnectorContext connector,
			UUID jobId,
			UUID claimToken,
			String error,
			boolean retryable,
			int retryAfterSeconds
	) {
		String safeError = requiredText(
				error, "CONNECTOR_JOB_ERROR_REQUIRED", "Reporta un error sanitizado para el trabajo.", 1000);
		safeError = safeDiagnostic(safeError);
		int delay = Math.max(0, Math.min(retryAfterSeconds, 3600));
		OffsetDateTime now = OffsetDateTime.now();
		var failed = repository.failJob(
				connector, jobId, requiredClaimToken(claimToken), safeError,
				retryable, now.plusSeconds(delay), now);
		if (failed.isPresent()) {
			return jobView(failed.get());
		}
		SyncJobRecord current = requiredJob(connector, jobId);
		if (Set.of("FAILED", "CANCELLED").contains(current.status())) {
			return jobView(current);
		}
		throw new BusinessException(
				"CONNECTOR_JOB_CLAIM_INVALID",
				"El trabajo no está reclamado por esta credencial."
		);
	}

	@Transactional
	public ConnectorJobView extendLease(
			ConnectorContext connector, UUID jobId, UUID claimToken, int requestedLeaseSeconds
	) {
		int leaseSeconds = Math.max(30, Math.min(requestedLeaseSeconds, 300));
		OffsetDateTime now = OffsetDateTime.now();
		if (!repository.extendLease(
				connector, jobId, requiredClaimToken(claimToken), now, now.plusSeconds(leaseSeconds))) {
			throw new BusinessException(
					"CONNECTOR_JOB_LEASE_INVALID",
					"El trabajo no está reclamado por esta credencial o su lease venció."
			);
		}
		return jobView(requiredJob(connector, jobId));
	}

	@Transactional
	public PhysicalEventView ingestEvent(ConnectorContext connector, PhysicalEventInput input, String correlationId) {
		String providerKey = providerCatalog.normalize(input.providerKey());
		if (!connector.providerKey().equals(providerKey)) {
			throw new BusinessException(
					"PHYSICAL_EVENT_PROVIDER_MISMATCH",
					"El evento no pertenece al proveedor autenticado."
			);
		}
		String externalEventId = requiredText(
				input.externalEventId(), "PHYSICAL_EVENT_ID_REQUIRED", "El evento requiere un identificador externo.", 180);
		String externalDeviceRef = requiredText(
				input.externalDeviceRef(), "PHYSICAL_EVENT_DEVICE_REQUIRED", "El evento requiere un recurso externo.", 180);
		var deviceLink = repository.deviceLinkByExternalRef(
				connector.organizationId(), connector.branchId(), connector.connectionId(), externalDeviceRef)
				.orElseThrow(() -> new BusinessException(
						"PHYSICAL_EVENT_DEVICE_UNRESOLVED",
						"El recurso externo no está vinculado a un punto de esta sucursal."));
		if (!"PROVIDER_MANAGED".equals(deviceLink.authorizationMode())) {
			throw new BusinessException(
					"PHYSICAL_EVENT_AUTHORIZATION_REQUIRED",
					"Este punto requiere autorización en línea de GymFort antes de abrir.");
		}
		String externalPersonRef = trim(input.externalPersonRef(), 180);
		var clientLink = externalPersonRef == null ? null : repository.clientLinkByExternalRef(
				connector.organizationId(), connector.branchId(), connector.connectionId(), externalPersonRef).orElse(null);
		String decision = input.decision() == null ? "DENIED" : input.decision().trim().toUpperCase(Locale.ROOT);
		if (!Set.of("GRANTED", "DENIED").contains(decision)) {
			throw new BusinessException("PHYSICAL_EVENT_DECISION_INVALID", "La decisión física no es válida.");
		}
		String eventType = normalizedCode(input.eventType(), "ACCESS_ATTEMPT", 80);
		String reason = clientLink == null && externalPersonRef != null
				? "EXTERNAL_PERSON_UNRESOLVED"
				: normalizedCode(input.reasonCode(), "GRANTED".equals(decision) ? "PROVIDER_GRANTED" : "PROVIDER_DENIED", 80);
		OffsetDateTime now = OffsetDateTime.now();
		OffsetDateTime occurredAt = input.occurredAt() == null ? now : input.occurredAt();
		if (occurredAt.isAfter(now.plusMinutes(5))) {
			throw new BusinessException("PHYSICAL_EVENT_TIME_INVALID", "El evento no puede estar fechado en el futuro.");
		}
		Map<String, String> metadata = sanitizeMetadata(input.metadata());
		UUID correlationUuid = safeUuid(correlationId + ":" + externalEventId);
		var result = repository.insertExternalEvent(
				connector, deviceLink.accessDeviceId(), clientLink == null ? null : clientLink.clientId(),
				providerKey, externalEventId, externalPersonRef, externalDeviceRef, eventType,
				toJson(metadata), decision, reason, occurredAt, correlationUuid, now);
		return new PhysicalEventView(
				result.accessLogId(), result.created(), result.clientId(), result.deviceId(), result.reason());
	}

	@Transactional
	public PersonCandidateRecord ingestPersonCandidate(
			ConnectorContext connector, PersonCandidateInput input
	) {
		String externalRef = requiredText(
				input.externalPersonRef(), "PHYSICAL_PERSON_REF_REQUIRED", "La persona externa requiere una referencia.", 180);
		var existingLink = repository.clientLinkByExternalRef(
				connector.organizationId(), connector.branchId(), connector.connectionId(), externalRef).orElse(null);
		List<UUID> matches = existingLink == null
				? repository.safeCandidateMatches(connector.organizationId(), connector.branchId(), input.email(), input.phone())
				: List.of(existingLink.clientId());
		String status = existingLink != null ? "MATCHED" : matches.size() == 1 ? "SUGGESTED"
				: matches.size() > 1 ? "CONFLICT" : "UNMATCHED";
		String reason = existingLink != null ? "ALREADY_LINKED"
				: matches.size() == 1 ? "UNIQUE_EXACT_CONTACT" : matches.size() > 1 ? "AMBIGUOUS_CONTACT" : null;
		var candidate = repository.upsertPersonCandidate(
				connector, externalRef, trim(input.externalDisplayName(), 180), normalizeEmail(input.email()),
				trim(input.phone(), 40), trim(input.externalEmployeeNumber(), 120), status,
				matches.size() == 1 ? matches.getFirst() : null, reason, OffsetDateTime.now());
		if (existingLink != null) {
			repository.markCandidateMatched(candidate.id(), existingLink.clientId(), null, OffsetDateTime.now());
			return repository.personCandidate(
					connector.organizationId(), connector.branchId(), connector.connectionId(), candidate.id()).orElse(candidate);
		}
		return candidate;
	}

	@Transactional
	public DeviceCandidateRecord ingestDeviceCandidate(
			ConnectorContext connector, DeviceCandidateInput input
	) {
		String externalRef = requiredText(
				input.externalDeviceRef(), "PHYSICAL_DEVICE_REF_REQUIRED",
				"El recurso externo requiere una referencia.", 180);
		Map<String, String> metadata = sanitizeMetadata(input.metadata());
		return repository.upsertDeviceCandidate(
				connector, externalRef, trim(input.externalDeviceName(), 180),
				normalizedCode(input.externalDeviceType(), "UNKNOWN", 80),
				toJson(metadata), OffsetDateTime.now());
	}

	@Transactional(readOnly = true)
	public List<PersonCandidateRecord> candidates(GymOsPrincipal principal, int limit) {
		requirePhysicalManagement(principal);
		ConnectionRecord connection = requiredConnection(principal);
		return repository.personCandidates(
				principal.organizationId(), principal.branchId(), connection.id(), Math.max(1, Math.min(limit, 200)));
	}

	@Transactional
	public PersonCandidateRecord acceptCandidate(
			GymOsPrincipal principal, UUID candidateId, UUID clientId, String correlationId
	) {
		requirePhysicalManagement(principal);
		ConnectionRecord connection = requiredConnection(principal);
		var candidate = repository.personCandidate(
				principal.organizationId(), principal.branchId(), connection.id(), candidateId)
				.orElseThrow(() -> new BusinessException("PHYSICAL_PERSON_CANDIDATE_NOT_FOUND", "El candidato no está disponible."));
		var client = accessRepository.clientById(principal.organizationId(), principal.branchId(), clientId)
				.orElseThrow(() -> new BusinessException("CLIENT_NOT_FOUND", "El socio no está visible en esta sucursal."));
		var duplicate = repository.clientLinkByExternalRef(
				principal.organizationId(), principal.branchId(), connection.id(), candidate.externalPersonRef()).orElse(null);
		if (duplicate != null && !duplicate.clientId().equals(client.id())) {
			throw new BusinessException(
					"PHYSICAL_ACCESS_PERSON_DUPLICATE",
					"La referencia externa ya está vinculada a otro socio."
			);
		}
		accessRepository.upsertPhysicalClientLink(
				principal.organizationId(), principal.branchId(), connection.id(), client.id(),
				candidate.externalPersonRef(), principal.userId());
		repository.markCandidateMatched(candidate.id(), client.id(), principal.userId(), OffsetDateTime.now());
		synchronizationService.reconcileClient(
				connection, client.id(), "candidate-linked:" + candidate.id() + ":" + correlationId,
				null, "CANDIDATE_LINK", principal.userId());
		auditService.record(
				principal, "PHYSICAL_PERSON_CANDIDATE_MATCHED", "ACCESS_PROVIDER_PERSON_CANDIDATE",
				candidate.id(), "BACKOFFICE", "clientId=" + client.id(), correlationId);
		return repository.personCandidate(
				principal.organizationId(), principal.branchId(), connection.id(), candidate.id()).orElseThrow();
	}

	@Transactional
	public PersonCandidateRecord rejectCandidate(
			GymOsPrincipal principal, UUID candidateId, String correlationId
	) {
		requirePhysicalManagement(principal);
		ConnectionRecord connection = requiredConnection(principal);
		var candidate = repository.personCandidate(
				principal.organizationId(), principal.branchId(), connection.id(), candidateId)
				.orElseThrow(() -> new BusinessException("PHYSICAL_PERSON_CANDIDATE_NOT_FOUND", "El candidato no está disponible."));
		repository.markCandidateRejected(candidate.id(), principal.userId(), OffsetDateTime.now());
		auditService.record(
				principal, "PHYSICAL_PERSON_CANDIDATE_REJECTED", "ACCESS_PROVIDER_PERSON_CANDIDATE",
				candidate.id(), "BACKOFFICE", null, correlationId);
		return repository.personCandidate(
				principal.organizationId(), principal.branchId(), connection.id(), candidate.id()).orElseThrow();
	}

	@Transactional(readOnly = true)
	public ConnectionHealthView connectionHealth(ConnectionRecord connection) {
		JobCounts counts = repository.jobCounts(
				connection.organizationId(), connection.branchId(), connection.id());
		boolean credentialActive = repository.activeCredential(connection.id()).isPresent();
		String derivedStatus;
		if (!connection.enabled()) {
			derivedStatus = "DISABLED";
		} else if (!credentialActive || connection.lastHeartbeatAt() == null) {
			derivedStatus = "AWAITING_CONNECTOR";
		} else if (connection.lastHeartbeatAt().isBefore(OffsetDateTime.now().minus(OFFLINE_AFTER))) {
			derivedStatus = "OFFLINE";
		} else if ("DEGRADED".equals(connection.connectorReportedStatus()) || counts.failed() > 0) {
			derivedStatus = "DEGRADED";
		} else {
			derivedStatus = "HEALTHY";
		}
		return new ConnectionHealthView(
				derivedStatus, credentialActive, connection.lastHeartbeatAt(), connection.lastSynchronizedAt(),
				connection.lastError(), counts.pending(), counts.failed(), connection.connectorVersion());
	}

	private SyncJobRecord requiredJob(ConnectorContext connector, UUID jobId) {
		return repository.job(connector.connectionId(), jobId)
				.orElseThrow(() -> new BusinessException("CONNECTOR_JOB_NOT_FOUND", "El trabajo no pertenece a este Connector."));
	}

	private ConnectionRecord requiredConnection(GymOsPrincipal principal) {
		return repository.connection(principal.organizationId(), principal.branchId())
				.orElseThrow(() -> new BusinessException(
						"PHYSICAL_ACCESS_INTEGRATION_REQUIRED",
						"Configura primero una conexión física para esta sucursal."));
	}

	private ApiAuthenticationException authenticationFailure(
			ConnectionRecord connection,
			String connectorKey,
			String correlationId,
			AbuseKeys abuseKeys,
			int maxFailures,
			Duration failureWindow
	) {
		recordFailedAttempt(abuseKeys, maxFailures, failureWindow, correlationId);
		UUID entityId = connection == null ? safeUuid("connector:" + connectorKey) : connection.id();
		try {
			securityAudit.authenticationRejected(
					connection == null ? null : connection.organizationId(),
					connection == null ? null : connection.branchId(), entityId, correlationId);
		} catch (RuntimeException auditFailure) {
			LOGGER.warn("Could not persist rejected Connector authentication. correlationId={}", correlationId);
		}
		return invalidAuthentication();
	}

	private void enforceNotLimited(
			AbuseKeys keys, int maxFailures, Duration window, String correlationId
	) {
		try {
			if (rateLimiter.isLimited(keys.origin(), maxFailures, window)
					|| rateLimiter.isLimited(keys.connectorKey(), maxFailures, window)) {
				throw invalidAuthentication();
			}
		} catch (ApiAuthenticationException exception) {
			throw exception;
		} catch (RuntimeException limiterFailure) {
			LOGGER.warn("Connector abuse protection unavailable. correlationId={}", correlationId);
		}
	}

	private void recordFailedAttempt(
			AbuseKeys keys, int maxFailures, Duration window, String correlationId
	) {
		for (String key : List.of(keys.origin(), keys.connectorKey())) {
			try {
				rateLimiter.check(key, maxFailures, window);
			} catch (BusinessException limited) {
				// The public response intentionally remains CONNECTOR_AUTH_INVALID.
			} catch (RuntimeException limiterFailure) {
				LOGGER.warn("Could not record Connector authentication failure. correlationId={}", correlationId);
			}
		}
	}

	private AbuseKeys abuseKeys(String operation, String connectorKey, String originIp) {
		String normalizedOrigin = originIp == null ? "<missing>" : originIp;
		String normalizedConnectorKey = connectorKey == null ? "<missing>" : connectorKey;
		return new AbuseKeys(
				"physical-connector:" + operation + ":origin:" + fingerprint(normalizedOrigin),
				"physical-connector:" + operation + ":origin-key:"
						+ fingerprint(normalizedOrigin + "\n" + normalizedConnectorKey));
	}

	private String fingerprint(String value) {
		String normalized = value == null || value.isBlank()
				? "<missing>" : value.trim().toLowerCase(Locale.ROOT);
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256")
					.digest(normalized.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest, 0, 16);
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is required for Connector abuse protection.", exception);
		}
	}

	private ApiAuthenticationException invalidAuthentication() {
		return new ApiAuthenticationException(
				"CONNECTOR_AUTH_INVALID", "La autenticación del Connector no es válida o fue revocada.");
	}

	private ConnectorJobView jobView(SyncJobRecord job) {
		return new ConnectorJobView(
				job.id(), job.idempotencyKey(), job.action(), job.clientId(), job.externalPersonRef(),
				fromJson(job.desiredAccessLevelRefsJson()), job.status(), job.attempts(), job.retryable(),
				job.leaseUntil(), job.claimToken(), job.lastError());
	}

	private UUID requiredClaimToken(UUID claimToken) {
		if (claimToken == null) {
			throw new BusinessException(
					"CONNECTOR_JOB_CLAIM_TOKEN_REQUIRED",
					"El trabajo requiere el token de la reclamación vigente."
			);
		}
		return claimToken;
	}

	Map<String, String> sanitizeMetadata(Map<String, String> requested) {
		if (requested == null || requested.isEmpty()) {
			return Map.of();
		}
		if (requested.size() > 20) {
			throw new BusinessException("PHYSICAL_EVENT_METADATA_INVALID", "El evento contiene demasiados metadatos.");
		}
		Map<String, String> result = new LinkedHashMap<>();
		for (var entry : requested.entrySet()) {
			String key = normalizedCode(entry.getKey(), null, 60);
			if (key == null || SENSITIVE_METADATA_KEYS.contains(key)
					|| SENSITIVE_METADATA_KEYS.stream().anyMatch(key::contains)) {
				throw new BusinessException(
						"PHYSICAL_EVENT_SENSITIVE_METADATA",
						"El evento incluye un campo sensible que GymFort Cloud no acepta."
				);
			}
			String value = trim(entry.getValue(), 240);
			if (value != null) {
				safeDiagnostic(value);
				result.put(key, value);
			}
		}
		return Map.copyOf(result);
	}

	private String randomToken(String prefix, int bytes) {
		byte[] entropy = new byte[bytes];
		secureRandom.nextBytes(entropy);
		return prefix + Base64.getUrlEncoder().withoutPadding().encodeToString(entropy);
	}

	private String requiredText(String value, String code, String message, int maxLength) {
		String normalized = trim(value, maxLength);
		if (normalized == null) {
			throw new BusinessException(code, message);
		}
		return normalized;
	}

	private String safeDiagnostic(String value) {
		if (value != null && (NETWORK_DIAGNOSTIC.matcher(value).find()
				|| SECRET_DIAGNOSTIC.matcher(value).find())) {
			throw new BusinessException(
					"CONNECTOR_DIAGNOSTIC_SENSITIVE",
					"El diagnóstico contiene red local o credenciales y no puede guardarse en GymFort Cloud."
			);
		}
		return value;
	}

	private String trim(String value, int maxLength) {
		if (value == null || value.isBlank()) {
			return null;
		}
		String normalized = value.trim();
		return normalized.substring(0, Math.min(normalized.length(), maxLength));
	}

	private String normalizeEmail(String email) {
		String value = trim(email, 254);
		return value == null ? null : value.toLowerCase(Locale.ROOT);
	}

	private String normalizedCode(String value, String fallback, int maxLength) {
		String source = value == null || value.isBlank() ? fallback : value;
		if (source == null) {
			return null;
		}
		String normalized = source.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9_]", "_");
		return normalized.substring(0, Math.min(normalized.length(), maxLength));
	}

	private UUID safeUuid(String value) {
		try {
			return UUID.fromString(value);
		} catch (RuntimeException exception) {
			return UUID.nameUUIDFromBytes(("gymfort:" + value).getBytes(java.nio.charset.StandardCharsets.UTF_8));
		}
	}

	private List<String> normalizeCapabilities(Set<String> capabilities) {
		if (capabilities == null) return List.of();
		if (capabilities.size() > 30) {
			throw new BusinessException("CONNECTOR_CAPABILITIES_INVALID",
					"El Connector reportó demasiadas capacidades.");
		}
		return capabilities.stream().map(value -> value == null ? "" : value.trim().toUpperCase(Locale.ROOT))
				.peek(value -> {
					if (!value.matches("[A-Z][A-Z0-9_]{0,79}")) {
						throw new BusinessException("CONNECTOR_CAPABILITIES_INVALID",
								"El Connector reportó una capacidad inválida.");
					}
				})
				.distinct().sorted().toList();
	}

	private String toJson(Object value) {
		try {
			return objectMapper.writeValueAsString(value);
		} catch (JsonProcessingException exception) {
			throw new IllegalStateException("No fue posible serializar metadatos físicos.", exception);
		}
	}

	private List<String> fromJson(String value) {
		try {
			return objectMapper.readValue(value, new TypeReference<List<String>>() { });
		} catch (JsonProcessingException exception) {
			throw new IllegalStateException("El trabajo físico contiene derechos inválidos.", exception);
		}
	}

	private void requirePhysicalManagement(GymOsPrincipal principal) {
		if (principal == null || !principal.permissions().contains("access.physical.manage")) {
			throw new BusinessException(
					"ACCESS_PHYSICAL_MANAGE_FORBIDDEN",
					"No tienes permiso para administrar la integración física."
			);
		}
		if (principal.branchId() == null) {
			throw new BusinessException("BRANCH_REQUIRED", "Selecciona una sucursal para continuar.");
		}
	}

	public record ActivationCodeView(
			String connectorKey, String activationCode, String purpose,
			OffsetDateTime expiresAt, String warning
	) {
	}

	public record ConnectorActivationView(
			UUID connectorId, String connectorKey, String providerKey, String connectorSecret,
			OffsetDateTime activatedAt, String warning
	) {
	}

	public record ConnectorHealthView(
			String status, OffsetDateTime heartbeatAt, int pendingJobs, int failedJobs
	) {
	}

	public record ConnectionHealthView(
			String status, boolean credentialActive, OffsetDateTime lastHeartbeatAt,
			OffsetDateTime lastSynchronizedAt, String lastError, int pendingJobs,
			int failedJobs, String connectorVersion
	) {
	}

	public record ConnectorJobView(
			UUID id, String idempotencyKey, String action, UUID clientId,
			String externalPersonRef, List<String> desiredAccessLevelRefs, String status,
			int attempts, boolean retryable, OffsetDateTime leaseUntil, UUID claimToken, String lastError
	) {
	}

	public record PhysicalEventInput(
			String providerKey, String externalEventId, String externalPersonRef,
			String externalDeviceRef, OffsetDateTime occurredAt, String decision,
			String eventType, String reasonCode, Map<String, String> metadata
	) {
	}

	public record PhysicalEventView(
			UUID accessLogId, boolean created, UUID clientId, UUID accessDeviceId, String normalizedReason
	) {
	}

	public record PersonCandidateInput(
			String externalPersonRef, String externalDisplayName, String email, String phone,
			String externalEmployeeNumber
	) {
	}

	public record DeviceCandidateInput(
			String externalDeviceRef, String externalDeviceName,
			String externalDeviceType, Map<String, String> metadata
	) {
	}

	private record AbuseKeys(String origin, String connectorKey) {
	}
}
