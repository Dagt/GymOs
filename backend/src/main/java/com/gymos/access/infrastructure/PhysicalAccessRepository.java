package com.gymos.access.infrastructure;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class PhysicalAccessRepository {
	private final JdbcClient jdbcClient;

	public PhysicalAccessRepository(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	public Optional<ConnectionRecord> connection(UUID organizationId, UUID branchId) {
		return jdbcClient.sql(connectionSelect() + """
				WHERE c.organization_id = :organizationId
				  AND c.branch_id = :branchId
				ORDER BY c.created_at
				LIMIT 1
				""")
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.query(this::mapConnection)
				.optional();
	}

	public Optional<ConnectionRecord> connection(UUID organizationId, UUID branchId, UUID connectionId) {
		return jdbcClient.sql(connectionSelect() + """
				WHERE c.organization_id = :organizationId
				  AND c.branch_id = :branchId
				  AND c.id = :connectionId
				""")
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("connectionId", connectionId)
				.query(this::mapConnection)
				.optional();
	}

	public Optional<ConnectionRecord> connectionByConnectorKey(String connectorKey) {
		return jdbcClient.sql(connectionSelect() + """
				WHERE LOWER(c.connector_key) = LOWER(:connectorKey)
				""")
				.param("connectorKey", connectorKey)
				.query(this::mapConnection)
				.optional();
	}

	public List<ConnectionRecord> connectionsForLinkedClient(UUID organizationId, UUID clientId) {
		return jdbcClient.sql(connectionSelect() + """
				JOIN access_provider_client_link link
				  ON link.connection_id = c.id
				 AND link.organization_id = c.organization_id
				 AND link.branch_id = c.branch_id
				WHERE c.organization_id = :organizationId
				  AND link.client_id = :clientId
				ORDER BY c.branch_id, c.created_at
				""")
				.param("organizationId", organizationId)
				.param("clientId", clientId)
				.query(this::mapConnection)
				.list();
	}

	public List<ConnectionRecord> connections(UUID organizationId, UUID branchId) {
		if (branchId == null) {
			return jdbcClient.sql(connectionSelect() + """
					WHERE c.organization_id = :organizationId
					ORDER BY c.branch_id, c.created_at
					""")
					.param("organizationId", organizationId)
					.query(this::mapConnection)
					.list();
		}
		return jdbcClient.sql(connectionSelect() + """
				WHERE c.organization_id = :organizationId
				  AND c.branch_id = :branchId
				ORDER BY c.created_at
				""")
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.query(this::mapConnection)
				.list();
	}

	public void updateHeartbeat(
			ConnectorContext connector,
			String reportedStatus,
			String connectorVersion,
			String instanceId,
			String lastError,
			OffsetDateTime now
	) {
		jdbcClient.sql("""
				UPDATE access_provider_connection
				SET last_heartbeat_at = :now,
				    connector_reported_status = :reportedStatus,
				    connector_version = :connectorVersion,
				    last_connector_instance_id = :instanceId,
				    sync_status = :reportedStatus,
				    last_error = :lastError,
				    updated_at = :now,
				    version = version + 1
				WHERE id = :connectionId
				  AND organization_id = :organizationId
				  AND branch_id = :branchId
				  AND enabled = TRUE
				""")
				.param("now", now)
				.param("reportedStatus", reportedStatus)
				.param("connectorVersion", connectorVersion)
				.param("instanceId", instanceId)
				.param("lastError", lastError)
				.param("connectionId", connector.connectionId())
				.param("organizationId", connector.organizationId())
				.param("branchId", connector.branchId())
				.update();
	}

	public void markConnectionActivated(UUID connectionId, OffsetDateTime now) {
		jdbcClient.sql("""
				UPDATE access_provider_connection
				SET connector_activated_at = :now,
				    sync_status = CASE WHEN enabled THEN 'AWAITING_CONNECTOR' ELSE 'DISABLED' END,
				    last_error = NULL,
				    updated_at = :now,
				    version = version + 1
				WHERE id = :connectionId
				""")
				.param("now", now)
				.param("connectionId", connectionId)
				.update();
	}

	public void markConnectionRevoked(UUID connectionId, OffsetDateTime now) {
		jdbcClient.sql("""
				UPDATE access_provider_connection
				SET connector_activated_at = NULL,
				    connector_reported_status = NULL,
				    sync_status = CASE WHEN enabled THEN 'AWAITING_CONNECTOR' ELSE 'DISABLED' END,
				    updated_at = :now,
				    version = version + 1
				WHERE id = :connectionId
				""")
				.param("now", now)
				.param("connectionId", connectionId)
				.update();
	}

	public void revokeOpenActivations(UUID connectionId, OffsetDateTime now) {
		jdbcClient.sql("""
				UPDATE access_connector_activation
				SET revoked_at = :now
				WHERE connection_id = :connectionId
				  AND used_at IS NULL
				  AND revoked_at IS NULL
				""")
				.param("now", now)
				.param("connectionId", connectionId)
				.update();
	}

	public ActivationRecord createActivation(
			ConnectionRecord connection,
			String activationCodeHash,
			String purpose,
			OffsetDateTime expiresAt,
			UUID actorUserId,
			OffsetDateTime now
	) {
		return jdbcClient.sql("""
				INSERT INTO access_connector_activation (
				    id, organization_id, branch_id, connection_id, activation_code_hash,
				    purpose, expires_at, created_at, created_by
				)
				VALUES (
				    :id, :organizationId, :branchId, :connectionId, :activationCodeHash,
				    :purpose, :expiresAt, :now, :actorUserId
				)
				RETURNING id, organization_id, branch_id, connection_id, activation_code_hash,
				          purpose, expires_at, used_at, revoked_at
				""")
				.param("id", UUID.randomUUID())
				.param("organizationId", connection.organizationId())
				.param("branchId", connection.branchId())
				.param("connectionId", connection.id())
				.param("activationCodeHash", activationCodeHash)
				.param("purpose", purpose)
				.param("expiresAt", expiresAt)
				.param("now", now)
				.param("actorUserId", actorUserId)
				.query(this::mapActivation)
				.single();
	}

	public Optional<ActivationRecord> openActivationForUpdate(UUID connectionId) {
		return jdbcClient.sql("""
				SELECT id, organization_id, branch_id, connection_id, activation_code_hash,
				       purpose, expires_at, used_at, revoked_at
				FROM access_connector_activation
				WHERE connection_id = :connectionId
				  AND used_at IS NULL
				  AND revoked_at IS NULL
				ORDER BY created_at DESC
				LIMIT 1
				FOR UPDATE
				""")
				.param("connectionId", connectionId)
				.query(this::mapActivation)
				.optional();
	}

	public boolean consumeActivation(UUID activationId, OffsetDateTime now) {
		return jdbcClient.sql("""
				UPDATE access_connector_activation
				SET used_at = :now
				WHERE id = :activationId
				  AND used_at IS NULL
				  AND revoked_at IS NULL
				  AND expires_at >= :now
				""")
				.param("activationId", activationId)
				.param("now", now)
				.update() == 1;
	}

	public void revokeActiveCredentials(UUID connectionId, OffsetDateTime now) {
		jdbcClient.sql("""
				UPDATE access_connector_credential
				SET status = 'REVOKED', revoked_at = :now, version = version + 1
				WHERE connection_id = :connectionId
				  AND status = 'ACTIVE'
				""")
				.param("connectionId", connectionId)
				.param("now", now)
				.update();
	}

	public CredentialRecord createCredential(
			ConnectionRecord connection,
			String secretHash,
			UUID actorUserId,
			OffsetDateTime now
	) {
		return jdbcClient.sql("""
				INSERT INTO access_connector_credential (
				    id, organization_id, branch_id, connection_id, secret_hash, status,
				    activated_at, created_at, created_by, version
				)
				VALUES (
				    :id, :organizationId, :branchId, :connectionId, :secretHash, 'ACTIVE',
				    :now, :now, :actorUserId, 0
				)
				RETURNING id, organization_id, branch_id, connection_id, secret_hash, status,
				          activated_at, revoked_at, last_authenticated_at
				""")
				.param("id", UUID.randomUUID())
				.param("organizationId", connection.organizationId())
				.param("branchId", connection.branchId())
				.param("connectionId", connection.id())
				.param("secretHash", secretHash)
				.param("now", now)
				.param("actorUserId", actorUserId)
				.query(this::mapCredential)
				.single();
	}

	public Optional<CredentialRecord> activeCredential(UUID connectionId) {
		return jdbcClient.sql("""
				SELECT id, organization_id, branch_id, connection_id, secret_hash, status,
				       activated_at, revoked_at, last_authenticated_at
				FROM access_connector_credential
				WHERE connection_id = :connectionId
				  AND status = 'ACTIVE'
				""")
				.param("connectionId", connectionId)
				.query(this::mapCredential)
				.optional();
	}

	public boolean touchCredentialIfStale(
			UUID credentialId, OffsetDateTime now, OffsetDateTime staleBefore
	) {
		return jdbcClient.sql("""
				UPDATE access_connector_credential
				SET last_authenticated_at = :now, version = version + 1
				WHERE id = :credentialId
				  AND status = 'ACTIVE'
				  AND (
				      last_authenticated_at IS NULL
				      OR last_authenticated_at < :staleBefore
				  )
				""")
				.param("credentialId", credentialId)
				.param("now", now)
				.param("staleBefore", staleBefore)
				.update() == 1;
	}

	public List<DeviceLinkRecord> deviceLinks(UUID organizationId, UUID branchId, UUID connectionId) {
		return jdbcClient.sql("""
				SELECT link.id, link.access_device_id, d.name AS device_name, d.device_code,
				       link.external_device_ref, link.external_device_name, link.updated_at
				FROM access_provider_device_link link
				JOIN access_device d
				  ON d.id = link.access_device_id
				 AND d.organization_id = link.organization_id
				 AND d.branch_id = link.branch_id
				WHERE link.organization_id = :organizationId
				  AND link.branch_id = :branchId
				  AND link.connection_id = :connectionId
				ORDER BY d.name, d.device_code
				""")
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("connectionId", connectionId)
				.query(this::mapDeviceLink)
				.list();
	}

	public Optional<DeviceLinkRecord> deviceLinkByExternalRef(
			UUID organizationId, UUID branchId, UUID connectionId, String externalDeviceRef
	) {
		return jdbcClient.sql("""
				SELECT link.id, link.access_device_id, d.name AS device_name, d.device_code,
				       link.external_device_ref, link.external_device_name, link.updated_at
				FROM access_provider_device_link link
				JOIN access_device d
				  ON d.id = link.access_device_id
				 AND d.organization_id = link.organization_id
				 AND d.branch_id = link.branch_id
				WHERE link.organization_id = :organizationId
				  AND link.branch_id = :branchId
				  AND link.connection_id = :connectionId
				  AND link.external_device_ref = :externalDeviceRef
				""")
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("connectionId", connectionId)
				.param("externalDeviceRef", externalDeviceRef)
				.query(this::mapDeviceLink)
				.optional();
	}

	public DeviceLinkRecord upsertDeviceLink(
			ConnectionRecord connection,
			UUID deviceId,
			String externalDeviceRef,
			String externalDeviceName,
			UUID actorUserId,
			OffsetDateTime now
	) {
		return jdbcClient.sql("""
				INSERT INTO access_provider_device_link (
				    id, organization_id, branch_id, connection_id, access_device_id,
				    external_device_ref, external_device_name, created_at, updated_at,
				    created_by, updated_by, version
				)
				VALUES (
				    :id, :organizationId, :branchId, :connectionId, :deviceId,
				    :externalDeviceRef, :externalDeviceName, :now, :now,
				    :actorUserId, :actorUserId, 0
				)
				ON CONFLICT (connection_id, access_device_id)
				DO UPDATE SET
				    external_device_ref = EXCLUDED.external_device_ref,
				    external_device_name = EXCLUDED.external_device_name,
				    updated_at = EXCLUDED.updated_at,
				    updated_by = EXCLUDED.updated_by,
				    version = access_provider_device_link.version + 1
				RETURNING id, access_device_id,
				          (SELECT name FROM access_device WHERE id = access_device_id) AS device_name,
				          (SELECT device_code FROM access_device WHERE id = access_device_id) AS device_code,
				          external_device_ref, external_device_name, updated_at
				""")
				.param("id", UUID.randomUUID())
				.param("organizationId", connection.organizationId())
				.param("branchId", connection.branchId())
				.param("connectionId", connection.id())
				.param("deviceId", deviceId)
				.param("externalDeviceRef", externalDeviceRef)
				.param("externalDeviceName", externalDeviceName)
				.param("now", now)
				.param("actorUserId", actorUserId)
				.query(this::mapDeviceLink)
				.single();
	}

	public boolean deleteDeviceLink(
			UUID organizationId, UUID branchId, UUID connectionId, UUID deviceId
	) {
		return jdbcClient.sql("""
				DELETE FROM access_provider_device_link
				WHERE organization_id = :organizationId
				  AND branch_id = :branchId
				  AND connection_id = :connectionId
				  AND access_device_id = :deviceId
				""")
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("connectionId", connectionId)
				.param("deviceId", deviceId)
				.update() == 1;
	}

	public Optional<ClientLinkRecord> clientLink(
			UUID organizationId, UUID branchId, UUID connectionId, UUID clientId
	) {
		return jdbcClient.sql("""
				SELECT client_id, external_person_ref, linked_at
				FROM access_provider_client_link
				WHERE organization_id = :organizationId
				  AND branch_id = :branchId
				  AND connection_id = :connectionId
				  AND client_id = :clientId
				""")
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("connectionId", connectionId)
				.param("clientId", clientId)
				.query((rs, rowNum) -> new ClientLinkRecord(
						rs.getObject("client_id", UUID.class),
						rs.getString("external_person_ref"),
						rs.getObject("linked_at", OffsetDateTime.class)))
				.optional();
	}

	public Optional<ClientLinkRecord> clientLinkByExternalRef(
			UUID organizationId, UUID branchId, UUID connectionId, String externalPersonRef
	) {
		return jdbcClient.sql("""
				SELECT client_id, external_person_ref, linked_at
				FROM access_provider_client_link
				WHERE organization_id = :organizationId
				  AND branch_id = :branchId
				  AND connection_id = :connectionId
				  AND external_person_ref = :externalPersonRef
				""")
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("connectionId", connectionId)
				.param("externalPersonRef", externalPersonRef)
				.query((rs, rowNum) -> new ClientLinkRecord(
						rs.getObject("client_id", UUID.class),
						rs.getString("external_person_ref"),
						rs.getObject("linked_at", OffsetDateTime.class)))
				.optional();
	}

	public List<UUID> linkedClientIds(UUID organizationId, UUID branchId, UUID connectionId) {
		return jdbcClient.sql("""
				SELECT client_id
				FROM access_provider_client_link
				WHERE organization_id = :organizationId
				  AND branch_id = :branchId
				  AND connection_id = :connectionId
				ORDER BY linked_at, client_id
				""")
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("connectionId", connectionId)
				.query(UUID.class)
				.list();
	}

	public DesiredStateRecord upsertDesiredState(
			ConnectionRecord connection,
			UUID clientId,
			String desiredAccessLevelRefsJson,
			String action,
			String status,
			String blockedReasonCode,
			String lastError,
			String sourceEventKey,
			UUID actorUserId,
			OffsetDateTime now
	) {
		return jdbcClient.sql("""
				INSERT INTO access_provider_sync_state (
				    id, organization_id, branch_id, connection_id, client_id,
				    desired_access_level_refs, action, status, requested_at,
				    blocked_reason_code, last_error, source_event_key,
				    desired_version, created_at, updated_at, created_by, updated_by, version
				)
				VALUES (
				    :id, :organizationId, :branchId, :connectionId, :clientId,
				    CAST(:desiredJson AS jsonb), :action, :status, :now,
				    :blockedReasonCode, :lastError, :sourceEventKey,
				    1, :now, :now, :actorUserId, :actorUserId, 0
				)
				ON CONFLICT (connection_id, client_id)
				DO UPDATE SET
				    desired_access_level_refs = CASE
				        WHEN access_provider_sync_state.source_event_key IS NOT DISTINCT FROM EXCLUDED.source_event_key
				        THEN access_provider_sync_state.desired_access_level_refs
				        ELSE EXCLUDED.desired_access_level_refs END,
				    action = CASE
				        WHEN access_provider_sync_state.source_event_key IS NOT DISTINCT FROM EXCLUDED.source_event_key
				        THEN access_provider_sync_state.action ELSE EXCLUDED.action END,
				    status = CASE
				        WHEN access_provider_sync_state.source_event_key IS NOT DISTINCT FROM EXCLUDED.source_event_key
				        THEN access_provider_sync_state.status ELSE EXCLUDED.status END,
				    requested_at = CASE
				        WHEN access_provider_sync_state.source_event_key IS NOT DISTINCT FROM EXCLUDED.source_event_key
				        THEN access_provider_sync_state.requested_at ELSE EXCLUDED.requested_at END,
				    blocked_reason_code = CASE
				        WHEN access_provider_sync_state.source_event_key IS NOT DISTINCT FROM EXCLUDED.source_event_key
				        THEN access_provider_sync_state.blocked_reason_code ELSE EXCLUDED.blocked_reason_code END,
				    last_error = CASE
				        WHEN access_provider_sync_state.source_event_key IS NOT DISTINCT FROM EXCLUDED.source_event_key
				        THEN access_provider_sync_state.last_error ELSE EXCLUDED.last_error END,
				    source_event_key = EXCLUDED.source_event_key,
				    desired_version = CASE
				        WHEN access_provider_sync_state.source_event_key IS NOT DISTINCT FROM EXCLUDED.source_event_key
				        THEN access_provider_sync_state.desired_version
				        ELSE access_provider_sync_state.desired_version + 1 END,
				    updated_at = CASE
				        WHEN access_provider_sync_state.source_event_key IS NOT DISTINCT FROM EXCLUDED.source_event_key
				        THEN access_provider_sync_state.updated_at ELSE EXCLUDED.updated_at END,
				    updated_by = CASE
				        WHEN access_provider_sync_state.source_event_key IS NOT DISTINCT FROM EXCLUDED.source_event_key
				        THEN access_provider_sync_state.updated_by ELSE EXCLUDED.updated_by END,
				    version = CASE
				        WHEN access_provider_sync_state.source_event_key IS NOT DISTINCT FROM EXCLUDED.source_event_key
				        THEN access_provider_sync_state.version ELSE access_provider_sync_state.version + 1 END
				RETURNING id, organization_id, branch_id, connection_id, client_id,
				          desired_access_level_refs::text AS desired_access_level_refs,
				          action, status, requested_at, last_attempt_at, last_synchronized_at,
				          last_error, desired_version, blocked_reason_code, source_event_key, latest_job_id
				""")
				.param("id", UUID.randomUUID())
				.param("organizationId", connection.organizationId())
				.param("branchId", connection.branchId())
				.param("connectionId", connection.id())
				.param("clientId", clientId)
				.param("desiredJson", desiredAccessLevelRefsJson)
				.param("action", action)
				.param("status", status)
				.param("now", now)
				.param("blockedReasonCode", blockedReasonCode)
				.param("lastError", lastError)
				.param("sourceEventKey", sourceEventKey)
				.param("actorUserId", actorUserId)
				.query(this::mapDesiredState)
				.single();
	}

	public Optional<DesiredStateRecord> desiredState(
			UUID organizationId, UUID branchId, UUID connectionId, UUID clientId
	) {
		return jdbcClient.sql("""
				SELECT id, organization_id, branch_id, connection_id, client_id,
				       desired_access_level_refs::text AS desired_access_level_refs,
				       action, status, requested_at, last_attempt_at, last_synchronized_at,
				       last_error, desired_version, blocked_reason_code, source_event_key, latest_job_id
				FROM access_provider_sync_state
				WHERE organization_id = :organizationId
				  AND branch_id = :branchId
				  AND connection_id = :connectionId
				  AND client_id = :clientId
				""")
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("connectionId", connectionId)
				.param("clientId", clientId)
				.query(this::mapDesiredState)
				.optional();
	}

	public SyncJobRecord enqueueJob(
			ConnectionRecord connection,
			DesiredStateRecord state,
			String externalPersonRef,
			String idempotencyKey,
			String requestSource,
			OffsetDateTime now
	) {
		Optional<SyncJobRecord> inserted = jdbcClient.sql("""
				INSERT INTO access_provider_sync_job (
				    id, organization_id, branch_id, connection_id, sync_state_id, client_id,
				    desired_version, action, external_person_ref, desired_access_level_refs,
				    idempotency_key, status, attempts, retryable, available_at, request_source,
				    created_at, updated_at, version
				)
				VALUES (
				    :id, :organizationId, :branchId, :connectionId, :syncStateId, :clientId,
				    :desiredVersion, :action, :externalPersonRef, CAST(:desiredJson AS jsonb),
				    :idempotencyKey, 'PENDING', 0, TRUE, :now, :requestSource,
				    :now, :now, 0
				)
				ON CONFLICT (connection_id, idempotency_key) DO NOTHING
				RETURNING id, organization_id, branch_id, connection_id, sync_state_id, client_id,
				          desired_version, action, external_person_ref,
				          desired_access_level_refs::text AS desired_access_level_refs,
				          idempotency_key, status, attempts, retryable, available_at, claimed_at,
				          lease_until, claimed_by_credential_id, claim_token, completed_at,
				          external_operation_ref, last_error, request_source, created_at, updated_at
				""")
				.param("id", UUID.randomUUID())
				.param("organizationId", connection.organizationId())
				.param("branchId", connection.branchId())
				.param("connectionId", connection.id())
				.param("syncStateId", state.id())
				.param("clientId", state.clientId())
				.param("desiredVersion", state.desiredVersion())
				.param("action", state.action())
				.param("externalPersonRef", externalPersonRef)
				.param("desiredJson", state.desiredAccessLevelRefsJson())
				.param("idempotencyKey", idempotencyKey)
				.param("requestSource", requestSource)
				.param("now", now)
				.query(this::mapSyncJob)
				.optional();
		SyncJobRecord job = inserted.orElseGet(() -> jobByIdempotency(connection.id(), idempotencyKey).orElseThrow());
		jdbcClient.sql("""
				UPDATE access_provider_sync_state
				SET latest_job_id = :jobId
				WHERE id = :stateId
				  AND desired_version = :desiredVersion
				""")
				.param("jobId", job.id())
				.param("stateId", state.id())
				.param("desiredVersion", state.desiredVersion())
				.update();
		jdbcClient.sql("""
				UPDATE access_provider_sync_job
				SET status = 'CANCELLED', retryable = FALSE, lease_until = NULL,
				    claim_token = NULL, updated_at = :now, version = version + 1
				WHERE connection_id = :connectionId
				  AND client_id = :clientId
				  AND id <> :jobId
				  AND desired_version < :desiredVersion
				  AND status IN ('PENDING', 'FAILED')
				""")
				.param("now", now)
				.param("connectionId", connection.id())
				.param("clientId", state.clientId())
				.param("jobId", job.id())
				.param("desiredVersion", state.desiredVersion())
				.update();
		return job;
	}

	public Optional<SyncJobRecord> jobByIdempotency(UUID connectionId, String idempotencyKey) {
		return jdbcClient.sql(syncJobSelect() + """
				WHERE connection_id = :connectionId
				  AND idempotency_key = :idempotencyKey
				""")
				.param("connectionId", connectionId)
				.param("idempotencyKey", idempotencyKey)
				.query(this::mapSyncJob)
				.optional();
	}

	public Optional<SyncJobRecord> job(UUID connectionId, UUID jobId) {
		return jdbcClient.sql(syncJobSelect() + """
				WHERE connection_id = :connectionId
				  AND id = :jobId
				""")
				.param("connectionId", connectionId)
				.param("jobId", jobId)
				.query(this::mapSyncJob)
				.optional();
	}

	public List<SyncJobRecord> claimJobs(
			ConnectorContext connector, int limit, OffsetDateTime now, OffsetDateTime leaseUntil
	) {
		jdbcClient.sql("""
				UPDATE access_provider_sync_job job
				SET status = 'CANCELLED', retryable = FALSE, lease_until = NULL,
				    claim_token = NULL, updated_at = :now, version = job.version + 1
				WHERE job.organization_id = :organizationId
				  AND job.branch_id = :branchId
				  AND job.connection_id = :connectionId
				  AND job.status = 'CLAIMED'
				  AND job.lease_until < :now
				  AND NOT EXISTS (
				      SELECT 1
				      FROM access_provider_sync_state state
				      WHERE state.id = job.sync_state_id
				        AND state.latest_job_id = job.id
				        AND state.desired_version = job.desired_version
				  )
				""")
				.param("organizationId", connector.organizationId())
				.param("branchId", connector.branchId())
				.param("connectionId", connector.connectionId())
				.param("now", now)
				.update();
		return jdbcClient.sql("""
				WITH candidates AS (
				    SELECT job.id
				    FROM access_provider_sync_job job
				    JOIN access_provider_sync_state state
				      ON state.id = job.sync_state_id
				     AND state.latest_job_id = job.id
				     AND state.desired_version = job.desired_version
				    WHERE job.organization_id = :organizationId
				      AND job.branch_id = :branchId
				      AND job.connection_id = :connectionId
				      AND (
				          (job.status IN ('PENDING', 'FAILED') AND job.retryable = TRUE AND job.available_at <= :now)
				          OR (job.status = 'CLAIMED' AND job.lease_until < :now)
				      )
				      AND NOT EXISTS (
				          SELECT 1
				          FROM access_provider_sync_job active_job
				          WHERE active_job.connection_id = job.connection_id
				            AND active_job.client_id = job.client_id
				            AND active_job.id <> job.id
				            AND active_job.status = 'CLAIMED'
				            AND active_job.lease_until >= :now
				      )
				    ORDER BY job.available_at, job.created_at, job.id
				    FOR UPDATE SKIP LOCKED
				    LIMIT :limit
				)
				UPDATE access_provider_sync_job job
				SET status = 'CLAIMED',
				    attempts = job.attempts + 1,
				    claimed_at = :now,
				    lease_until = :leaseUntil,
				    claimed_by_credential_id = :credentialId,
				    claim_token = gen_random_uuid(),
				    updated_at = :now,
				    version = job.version + 1
				FROM candidates
				WHERE job.id = candidates.id
				RETURNING job.id, job.organization_id, job.branch_id, job.connection_id,
				          job.sync_state_id, job.client_id, job.desired_version, job.action,
				          job.external_person_ref,
				          job.desired_access_level_refs::text AS desired_access_level_refs,
				          job.idempotency_key, job.status, job.attempts, job.retryable,
				          job.available_at, job.claimed_at, job.lease_until,
				          job.claimed_by_credential_id, job.claim_token, job.completed_at,
				          job.external_operation_ref, job.last_error, job.request_source,
				          job.created_at, job.updated_at
				""")
				.param("organizationId", connector.organizationId())
				.param("branchId", connector.branchId())
				.param("connectionId", connector.connectionId())
				.param("credentialId", connector.credentialId())
				.param("limit", limit)
				.param("now", now)
				.param("leaseUntil", leaseUntil)
				.query(this::mapSyncJob)
				.list();
	}

	public Optional<SyncJobRecord> completeJob(
			ConnectorContext connector,
			UUID jobId,
			UUID claimToken,
			String externalOperationRef,
			OffsetDateTime now
	) {
		Optional<SyncJobRecord> completed = jdbcClient.sql("""
				UPDATE access_provider_sync_job
				SET status = 'SUCCEEDED', completed_at = :now, lease_until = NULL,
				    claim_token = NULL,
				    external_operation_ref = :externalOperationRef, last_error = NULL,
				    updated_at = :now, version = version + 1
				WHERE id = :jobId
				  AND organization_id = :organizationId
				  AND branch_id = :branchId
				  AND connection_id = :connectionId
				  AND status = 'CLAIMED'
				  AND claimed_by_credential_id = :credentialId
				  AND claim_token = :claimToken
				  AND lease_until >= :now
				RETURNING id, organization_id, branch_id, connection_id, sync_state_id, client_id,
				          desired_version, action, external_person_ref,
				          desired_access_level_refs::text AS desired_access_level_refs,
				          idempotency_key, status, attempts, retryable, available_at, claimed_at,
				          lease_until, claimed_by_credential_id, claim_token, completed_at,
				          external_operation_ref, last_error, request_source, created_at, updated_at
				""")
				.param("jobId", jobId)
				.param("organizationId", connector.organizationId())
				.param("branchId", connector.branchId())
				.param("connectionId", connector.connectionId())
				.param("credentialId", connector.credentialId())
				.param("claimToken", claimToken)
				.param("externalOperationRef", externalOperationRef)
				.param("now", now)
				.query(this::mapSyncJob)
				.optional();
		if (completed.isPresent()) {
			SyncJobRecord job = completed.get();
			jdbcClient.sql("""
					UPDATE access_provider_sync_state
					SET status = 'SYNCED', last_synchronized_at = :now, last_attempt_at = :now,
					    last_error = NULL, blocked_reason_code = NULL,
					    updated_at = :now, version = version + 1
					WHERE id = :stateId
					  AND desired_version = :desiredVersion
					  AND latest_job_id = :jobId
					""")
					.param("now", now)
					.param("stateId", job.syncStateId())
					.param("desiredVersion", job.desiredVersion())
					.param("jobId", job.id())
					.update();
			jdbcClient.sql("""
					UPDATE access_provider_connection
					SET last_synchronized_at = :now, last_error = NULL,
					    updated_at = :now, version = version + 1
					WHERE id = :connectionId
					  AND organization_id = :organizationId
					  AND branch_id = :branchId
					""")
					.param("now", now)
					.param("connectionId", connector.connectionId())
					.param("organizationId", connector.organizationId())
					.param("branchId", connector.branchId())
					.update();
		}
		return completed;
	}

	public Optional<SyncJobRecord> failJob(
			ConnectorContext connector,
			UUID jobId,
			UUID claimToken,
			String error,
			boolean retryable,
			OffsetDateTime availableAt,
			OffsetDateTime now
	) {
		Optional<SyncJobRecord> failed = jdbcClient.sql("""
				UPDATE access_provider_sync_job job
				SET status = CASE
				        WHEN state.latest_job_id = job.id AND state.desired_version = job.desired_version
				        THEN 'FAILED' ELSE 'CANCELLED' END,
				    retryable = CASE
				        WHEN state.latest_job_id = job.id AND state.desired_version = job.desired_version
				        THEN :retryable ELSE FALSE END,
				    available_at = :availableAt, lease_until = NULL, claim_token = NULL,
				    last_error = :error, updated_at = :now, version = job.version + 1
				FROM access_provider_sync_state state
				WHERE job.id = :jobId
				  AND job.organization_id = :organizationId
				  AND job.branch_id = :branchId
				  AND job.connection_id = :connectionId
				  AND job.status = 'CLAIMED'
				  AND job.claimed_by_credential_id = :credentialId
				  AND job.claim_token = :claimToken
				  AND job.lease_until >= :now
				  AND state.id = job.sync_state_id
				RETURNING job.id, job.organization_id, job.branch_id, job.connection_id,
				          job.sync_state_id, job.client_id, job.desired_version, job.action,
				          job.external_person_ref,
				          job.desired_access_level_refs::text AS desired_access_level_refs,
				          job.idempotency_key, job.status, job.attempts, job.retryable,
				          job.available_at, job.claimed_at, job.lease_until,
				          job.claimed_by_credential_id, job.claim_token, job.completed_at,
				          job.external_operation_ref, job.last_error, job.request_source,
				          job.created_at, job.updated_at
				""")
				.param("jobId", jobId)
				.param("organizationId", connector.organizationId())
				.param("branchId", connector.branchId())
				.param("connectionId", connector.connectionId())
				.param("credentialId", connector.credentialId())
				.param("claimToken", claimToken)
				.param("retryable", retryable)
				.param("availableAt", availableAt)
				.param("error", error)
				.param("now", now)
				.query(this::mapSyncJob)
				.optional();
		if (failed.isPresent() && "FAILED".equals(failed.get().status())) {
			SyncJobRecord job = failed.get();
			jdbcClient.sql("""
					UPDATE access_provider_sync_state
					SET status = 'FAILED', last_attempt_at = :now, last_error = :error,
					    updated_at = :now, version = version + 1
					WHERE id = :stateId
					  AND desired_version = :desiredVersion
					  AND latest_job_id = :jobId
					""")
					.param("now", now)
					.param("error", error)
					.param("stateId", job.syncStateId())
					.param("desiredVersion", job.desiredVersion())
					.param("jobId", job.id())
					.update();
			jdbcClient.sql("""
					UPDATE access_provider_connection
					SET last_error = :error, sync_status = 'DEGRADED',
					    updated_at = :now, version = version + 1
					WHERE id = :connectionId
					  AND organization_id = :organizationId
					  AND branch_id = :branchId
					""")
					.param("error", error)
					.param("now", now)
					.param("connectionId", connector.connectionId())
					.param("organizationId", connector.organizationId())
					.param("branchId", connector.branchId())
					.update();
		}
		return failed;
	}

	public boolean extendLease(
			ConnectorContext connector, UUID jobId, UUID claimToken,
			OffsetDateTime now, OffsetDateTime leaseUntil
	) {
		return jdbcClient.sql("""
				UPDATE access_provider_sync_job
				SET lease_until = :leaseUntil, updated_at = :now, version = version + 1
				WHERE id = :jobId
				  AND organization_id = :organizationId
				  AND branch_id = :branchId
				  AND connection_id = :connectionId
				  AND status = 'CLAIMED'
				  AND claimed_by_credential_id = :credentialId
				  AND claim_token = :claimToken
				  AND lease_until >= :now
				  AND EXISTS (
				      SELECT 1
				      FROM access_provider_sync_state state
				      WHERE state.id = access_provider_sync_job.sync_state_id
				        AND state.latest_job_id = access_provider_sync_job.id
				        AND state.desired_version = access_provider_sync_job.desired_version
				  )
				""")
				.param("leaseUntil", leaseUntil)
				.param("now", now)
				.param("jobId", jobId)
				.param("organizationId", connector.organizationId())
				.param("branchId", connector.branchId())
				.param("connectionId", connector.connectionId())
				.param("credentialId", connector.credentialId())
				.param("claimToken", claimToken)
				.update() == 1;
	}

	public JobCounts jobCounts(UUID organizationId, UUID branchId, UUID connectionId) {
		return jdbcClient.sql("""
				SELECT COUNT(*) FILTER (WHERE status IN ('PENDING', 'CLAIMED')) AS pending_count,
				       COUNT(*) FILTER (WHERE status = 'FAILED') AS failed_count
				FROM access_provider_sync_job
				WHERE organization_id = :organizationId
				  AND branch_id = :branchId
				  AND connection_id = :connectionId
				""")
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("connectionId", connectionId)
				.query((rs, rowNum) -> new JobCounts(rs.getInt("pending_count"), rs.getInt("failed_count")))
				.single();
	}

	public List<UUID> safeCandidateMatches(
			UUID organizationId, UUID branchId, String email, String phone
	) {
		if ((email == null || email.isBlank()) && (phone == null || phone.isBlank())) {
			return List.of();
		}
		String normalizedEmail = email == null ? "" : email.trim().toLowerCase();
		String normalizedPhone = phone == null ? "" : phone.replaceAll("\\D", "");
		return jdbcClient.sql("""
				SELECT DISTINCT c.id
				FROM client c
				JOIN person p ON p.id = c.person_id AND p.organization_id = c.organization_id
				WHERE c.organization_id = :organizationId
				  AND c.deleted_at IS NULL
				  AND p.deleted_at IS NULL
				  AND c.status = 'ACTIVE'
				  AND (
				      (:email <> '' AND LOWER(COALESCE(p.email, '')) = :email)
				      OR (:phone <> '' AND REGEXP_REPLACE(COALESCE(p.phone, ''), '[^0-9]', '', 'g') = :phone)
				  )
				  AND (
				      c.home_branch_id IS NULL OR c.home_branch_id = :branchId
				      OR EXISTS (
				          SELECT 1 FROM membership m
				          JOIN membership_plan mp ON mp.id = m.plan_id
				          WHERE m.organization_id = c.organization_id
				            AND m.client_id = c.id
				            AND m.status = 'ACTIVE'
				            AND m.deleted_at IS NULL
				            AND (m.branch_id = :branchId OR mp.multi_branch_access = TRUE)
				      )
				  )
				ORDER BY c.id
				LIMIT 3
				""")
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("email", normalizedEmail)
				.param("phone", normalizedPhone)
				.query(UUID.class)
				.list();
	}

	public PersonCandidateRecord upsertPersonCandidate(
			ConnectorContext connector,
			String externalPersonRef,
			String displayName,
			String email,
			String phone,
			String employeeNumber,
			String matchStatus,
			UUID suggestedClientId,
			String matchReason,
			OffsetDateTime now
	) {
		return jdbcClient.sql("""
				INSERT INTO access_provider_person_candidate (
				    id, organization_id, branch_id, connection_id, external_person_ref,
				    external_display_name, email, phone, external_employee_number,
				    match_status, suggested_client_id, match_reason, created_at, updated_at, version
				)
				VALUES (
				    :id, :organizationId, :branchId, :connectionId, :externalPersonRef,
				    :displayName, :email, :phone, :employeeNumber,
				    :matchStatus, :suggestedClientId, :matchReason, :now, :now, 0
				)
				ON CONFLICT (connection_id, external_person_ref)
				DO UPDATE SET
				    external_display_name = EXCLUDED.external_display_name,
				    email = EXCLUDED.email,
				    phone = EXCLUDED.phone,
				    external_employee_number = EXCLUDED.external_employee_number,
				    match_status = CASE
				        WHEN access_provider_person_candidate.match_status = 'MATCHED'
				        THEN access_provider_person_candidate.match_status
				        ELSE EXCLUDED.match_status END,
				    suggested_client_id = CASE
				        WHEN access_provider_person_candidate.match_status = 'MATCHED'
				        THEN access_provider_person_candidate.suggested_client_id
				        ELSE EXCLUDED.suggested_client_id END,
				    match_reason = CASE
				        WHEN access_provider_person_candidate.match_status = 'MATCHED'
				        THEN access_provider_person_candidate.match_reason
				        ELSE EXCLUDED.match_reason END,
				    updated_at = EXCLUDED.updated_at,
				    version = access_provider_person_candidate.version + 1
				RETURNING id, external_person_ref, external_display_name, email, phone,
				          external_employee_number, match_status, suggested_client_id,
				          matched_client_id, match_reason, updated_at
				""")
				.param("id", UUID.randomUUID())
				.param("organizationId", connector.organizationId())
				.param("branchId", connector.branchId())
				.param("connectionId", connector.connectionId())
				.param("externalPersonRef", externalPersonRef)
				.param("displayName", displayName)
				.param("email", email)
				.param("phone", phone)
				.param("employeeNumber", employeeNumber)
				.param("matchStatus", matchStatus)
				.param("suggestedClientId", suggestedClientId)
				.param("matchReason", matchReason)
				.param("now", now)
				.query(this::mapCandidate)
				.single();
	}

	public List<PersonCandidateRecord> personCandidates(
			UUID organizationId, UUID branchId, UUID connectionId, int limit
	) {
		return jdbcClient.sql("""
				SELECT id, external_person_ref, external_display_name, email, phone,
				       external_employee_number, match_status, suggested_client_id,
				       matched_client_id, match_reason, updated_at
				FROM access_provider_person_candidate
				WHERE organization_id = :organizationId
				  AND branch_id = :branchId
				  AND connection_id = :connectionId
				ORDER BY CASE match_status
				             WHEN 'CONFLICT' THEN 0 WHEN 'SUGGESTED' THEN 1
				             WHEN 'UNMATCHED' THEN 2 ELSE 3 END,
				         updated_at DESC
				LIMIT :limit
				""")
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("connectionId", connectionId)
				.param("limit", limit)
				.query(this::mapCandidate)
				.list();
	}

	public Optional<PersonCandidateRecord> personCandidate(
			UUID organizationId, UUID branchId, UUID connectionId, UUID candidateId
	) {
		return jdbcClient.sql("""
				SELECT id, external_person_ref, external_display_name, email, phone,
				       external_employee_number, match_status, suggested_client_id,
				       matched_client_id, match_reason, updated_at
				FROM access_provider_person_candidate
				WHERE id = :candidateId
				  AND organization_id = :organizationId
				  AND branch_id = :branchId
				  AND connection_id = :connectionId
				""")
				.param("candidateId", candidateId)
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("connectionId", connectionId)
				.query(this::mapCandidate)
				.optional();
	}

	public void markCandidateMatched(
			UUID candidateId, UUID clientId, UUID actorUserId, OffsetDateTime now
	) {
		jdbcClient.sql("""
				UPDATE access_provider_person_candidate
				SET match_status = 'MATCHED', matched_client_id = :clientId,
				    suggested_client_id = :clientId, match_reason = 'ADMIN_CONFIRMED',
				    reviewed_at = :now, reviewed_by = :actorUserId,
				    updated_at = :now, version = version + 1
				WHERE id = :candidateId
				""")
				.param("candidateId", candidateId)
				.param("clientId", clientId)
				.param("actorUserId", actorUserId)
				.param("now", now)
				.update();
	}

	public void markCandidateRejected(UUID candidateId, UUID actorUserId, OffsetDateTime now) {
		jdbcClient.sql("""
				UPDATE access_provider_person_candidate
				SET match_status = 'REJECTED', suggested_client_id = NULL,
				    matched_client_id = NULL, match_reason = 'ADMIN_REJECTED',
				    rejected_at = :now, reviewed_at = :now, reviewed_by = :actorUserId,
				    updated_at = :now, version = version + 1
				WHERE id = :candidateId
				""")
				.param("candidateId", candidateId)
				.param("actorUserId", actorUserId)
				.param("now", now)
				.update();
	}

	public ExternalEventInsertResult insertExternalEvent(
			ConnectorContext connector,
			UUID deviceId,
			UUID clientId,
			String providerKey,
			String externalEventId,
			String externalPersonRef,
			String externalDeviceRef,
			String eventType,
			String metadataJson,
			String decision,
			String reason,
			OffsetDateTime occurredAt,
			UUID correlationId,
			OffsetDateTime now
	) {
		UUID newId = UUID.randomUUID();
		Optional<UUID> inserted = jdbcClient.sql("""
				INSERT INTO access_log (
				    id, organization_id, branch_id, device_id, client_id,
				    access_type, decision, source, reason, command_status,
				    occurred_at, created_at, created_by, correlation_id,
				    provider_connection_id, provider_key, external_event_id,
				    external_person_ref, external_device_ref, external_event_type,
				    external_metadata_json
				)
				VALUES (
				    :id, :organizationId, :branchId, :deviceId, :clientId,
				    'PHYSICAL_EVENT', :decision, 'CONNECTOR', :reason, 'PROVIDER_REPORTED',
				    :occurredAt, :now, NULL, :correlationId,
				    :connectionId, :providerKey, :externalEventId,
				    :externalPersonRef, :externalDeviceRef, :eventType,
				    CAST(:metadataJson AS jsonb)
				)
				ON CONFLICT (organization_id, branch_id, provider_connection_id, external_event_id)
				    WHERE provider_connection_id IS NOT NULL AND external_event_id IS NOT NULL
				DO NOTHING
				RETURNING id
				""")
				.param("id", newId)
				.param("organizationId", connector.organizationId())
				.param("branchId", connector.branchId())
				.param("connectionId", connector.connectionId())
				.param("deviceId", deviceId)
				.param("clientId", clientId)
				.param("decision", decision)
				.param("reason", reason)
				.param("occurredAt", occurredAt)
				.param("now", now)
				.param("correlationId", correlationId)
				.param("providerKey", providerKey)
				.param("externalEventId", externalEventId)
				.param("externalPersonRef", externalPersonRef)
				.param("externalDeviceRef", externalDeviceRef)
				.param("eventType", eventType)
				.param("metadataJson", metadataJson)
				.query(UUID.class)
				.optional();
		ExternalEventInsertResult result = inserted
				.map(logId -> new ExternalEventInsertResult(
						logId, true, clientId, deviceId, reason))
				.orElseGet(() -> jdbcClient.sql("""
						SELECT id, client_id, device_id, reason
						FROM access_log
						WHERE organization_id = :organizationId
						  AND branch_id = :branchId
						  AND provider_connection_id = :connectionId
						  AND external_event_id = :externalEventId
						""")
						.param("organizationId", connector.organizationId())
						.param("branchId", connector.branchId())
						.param("connectionId", connector.connectionId())
						.param("externalEventId", externalEventId)
						.query((rs, rowNum) -> new ExternalEventInsertResult(
								rs.getObject("id", UUID.class), false,
								rs.getObject("client_id", UUID.class),
								rs.getObject("device_id", UUID.class),
								rs.getString("reason")))
						.single());
		if (result.created()) {
			jdbcClient.sql("""
					UPDATE access_device
					SET last_seen_at = GREATEST(COALESCE(last_seen_at, :occurredAt), :occurredAt),
					    updated_at = :now, version = version + 1
					WHERE id = :deviceId
					  AND organization_id = :organizationId
					  AND branch_id = :branchId
					""")
					.param("occurredAt", occurredAt)
					.param("now", now)
					.param("deviceId", deviceId)
					.param("organizationId", connector.organizationId())
					.param("branchId", connector.branchId())
					.update();
		}
		return result;
	}

	private String connectionSelect() {
		return """
				SELECT c.id, c.organization_id, c.branch_id, c.provider_key, c.display_name,
				       c.connector_key, c.enabled, c.sync_status, c.last_heartbeat_at,
				       c.last_synchronized_at, c.last_error, c.connector_activated_at,
				       c.connector_reported_status, c.connector_version, c.last_connector_instance_id
				FROM access_provider_connection c
				""";
	}

	private String syncJobSelect() {
		return """
				SELECT id, organization_id, branch_id, connection_id, sync_state_id, client_id,
				       desired_version, action, external_person_ref,
				       desired_access_level_refs::text AS desired_access_level_refs,
				       idempotency_key, status, attempts, retryable, available_at, claimed_at,
				       lease_until, claimed_by_credential_id, claim_token, completed_at,
				       external_operation_ref, last_error, request_source, created_at, updated_at
				FROM access_provider_sync_job
				""";
	}

	private ConnectionRecord mapConnection(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
		return new ConnectionRecord(
				rs.getObject("id", UUID.class), rs.getObject("organization_id", UUID.class),
				rs.getObject("branch_id", UUID.class), rs.getString("provider_key"),
				rs.getString("display_name"), rs.getString("connector_key"), rs.getBoolean("enabled"),
				rs.getString("sync_status"), rs.getObject("last_heartbeat_at", OffsetDateTime.class),
				rs.getObject("last_synchronized_at", OffsetDateTime.class), rs.getString("last_error"),
				rs.getObject("connector_activated_at", OffsetDateTime.class),
				rs.getString("connector_reported_status"), rs.getString("connector_version"),
				rs.getString("last_connector_instance_id"));
	}

	private ActivationRecord mapActivation(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
		return new ActivationRecord(
				rs.getObject("id", UUID.class), rs.getObject("organization_id", UUID.class),
				rs.getObject("branch_id", UUID.class), rs.getObject("connection_id", UUID.class),
				rs.getString("activation_code_hash"), rs.getString("purpose"),
				rs.getObject("expires_at", OffsetDateTime.class), rs.getObject("used_at", OffsetDateTime.class),
				rs.getObject("revoked_at", OffsetDateTime.class));
	}

	private CredentialRecord mapCredential(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
		return new CredentialRecord(
				rs.getObject("id", UUID.class), rs.getObject("organization_id", UUID.class),
				rs.getObject("branch_id", UUID.class), rs.getObject("connection_id", UUID.class),
				rs.getString("secret_hash"), rs.getString("status"),
				rs.getObject("activated_at", OffsetDateTime.class), rs.getObject("revoked_at", OffsetDateTime.class),
				rs.getObject("last_authenticated_at", OffsetDateTime.class));
	}

	private DeviceLinkRecord mapDeviceLink(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
		return new DeviceLinkRecord(
				rs.getObject("id", UUID.class), rs.getObject("access_device_id", UUID.class),
				rs.getString("device_name"), rs.getString("device_code"),
				rs.getString("external_device_ref"), rs.getString("external_device_name"),
				rs.getObject("updated_at", OffsetDateTime.class));
	}

	private DesiredStateRecord mapDesiredState(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
		return new DesiredStateRecord(
				rs.getObject("id", UUID.class), rs.getObject("organization_id", UUID.class),
				rs.getObject("branch_id", UUID.class), rs.getObject("connection_id", UUID.class),
				rs.getObject("client_id", UUID.class), rs.getString("desired_access_level_refs"),
				rs.getString("action"), rs.getString("status"),
				rs.getObject("requested_at", OffsetDateTime.class),
				rs.getObject("last_attempt_at", OffsetDateTime.class),
				rs.getObject("last_synchronized_at", OffsetDateTime.class), rs.getString("last_error"),
				rs.getLong("desired_version"), rs.getString("blocked_reason_code"),
				rs.getString("source_event_key"), rs.getObject("latest_job_id", UUID.class));
	}

	private SyncJobRecord mapSyncJob(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
		return new SyncJobRecord(
				rs.getObject("id", UUID.class), rs.getObject("organization_id", UUID.class),
				rs.getObject("branch_id", UUID.class), rs.getObject("connection_id", UUID.class),
				rs.getObject("sync_state_id", UUID.class), rs.getObject("client_id", UUID.class),
				rs.getLong("desired_version"), rs.getString("action"),
				rs.getString("external_person_ref"), rs.getString("desired_access_level_refs"),
				rs.getString("idempotency_key"), rs.getString("status"), rs.getInt("attempts"),
				rs.getBoolean("retryable"), rs.getObject("available_at", OffsetDateTime.class),
				rs.getObject("claimed_at", OffsetDateTime.class), rs.getObject("lease_until", OffsetDateTime.class),
				rs.getObject("claimed_by_credential_id", UUID.class),
				rs.getObject("claim_token", UUID.class),
				rs.getObject("completed_at", OffsetDateTime.class), rs.getString("external_operation_ref"),
				rs.getString("last_error"), rs.getString("request_source"),
				rs.getObject("created_at", OffsetDateTime.class), rs.getObject("updated_at", OffsetDateTime.class));
	}

	private PersonCandidateRecord mapCandidate(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
		return new PersonCandidateRecord(
				rs.getObject("id", UUID.class), rs.getString("external_person_ref"),
				rs.getString("external_display_name"), rs.getString("email"), rs.getString("phone"),
				rs.getString("external_employee_number"), rs.getString("match_status"),
				rs.getObject("suggested_client_id", UUID.class), rs.getObject("matched_client_id", UUID.class),
				rs.getString("match_reason"), rs.getObject("updated_at", OffsetDateTime.class));
	}

	public record ConnectionRecord(
			UUID id, UUID organizationId, UUID branchId, String providerKey, String displayName,
			String connectorKey, boolean enabled, String syncStatus, OffsetDateTime lastHeartbeatAt,
			OffsetDateTime lastSynchronizedAt, String lastError, OffsetDateTime connectorActivatedAt,
			String connectorReportedStatus, String connectorVersion, String lastConnectorInstanceId
	) {
	}

	public record ActivationRecord(
			UUID id, UUID organizationId, UUID branchId, UUID connectionId, String activationCodeHash,
			String purpose, OffsetDateTime expiresAt, OffsetDateTime usedAt, OffsetDateTime revokedAt
	) {
	}

	public record CredentialRecord(
			UUID id, UUID organizationId, UUID branchId, UUID connectionId, String secretHash,
			String status, OffsetDateTime activatedAt, OffsetDateTime revokedAt,
			OffsetDateTime lastAuthenticatedAt
	) {
	}

	public record ConnectorContext(
			UUID credentialId, UUID organizationId, UUID branchId, UUID connectionId,
			String providerKey, String connectorKey
	) {
	}

	public record DeviceLinkRecord(
			UUID id, UUID accessDeviceId, String deviceName, String deviceCode,
			String externalDeviceRef, String externalDeviceName, OffsetDateTime updatedAt
	) {
	}

	public record ClientLinkRecord(UUID clientId, String externalPersonRef, OffsetDateTime linkedAt) {
	}

	public record DesiredStateRecord(
			UUID id, UUID organizationId, UUID branchId, UUID connectionId, UUID clientId,
			String desiredAccessLevelRefsJson, String action, String status,
			OffsetDateTime requestedAt, OffsetDateTime lastAttemptAt, OffsetDateTime lastSynchronizedAt,
			String lastError, long desiredVersion, String blockedReasonCode, String sourceEventKey,
			UUID latestJobId
	) {
	}

	public record SyncJobRecord(
			UUID id, UUID organizationId, UUID branchId, UUID connectionId, UUID syncStateId,
			UUID clientId, long desiredVersion, String action, String externalPersonRef,
			String desiredAccessLevelRefsJson, String idempotencyKey, String status, int attempts,
			boolean retryable, OffsetDateTime availableAt, OffsetDateTime claimedAt,
			OffsetDateTime leaseUntil, UUID claimedByCredentialId, UUID claimToken, OffsetDateTime completedAt,
			String externalOperationRef, String lastError, String requestSource,
			OffsetDateTime createdAt, OffsetDateTime updatedAt
	) {
	}

	public record JobCounts(int pending, int failed) {
	}

	public record PersonCandidateRecord(
			UUID id, String externalPersonRef, String externalDisplayName, String email, String phone,
			String externalEmployeeNumber, String matchStatus, UUID suggestedClientId,
			UUID matchedClientId, String matchReason, OffsetDateTime updatedAt
	) {
	}

	public record ExternalEventInsertResult(
			UUID accessLogId, boolean created, UUID clientId, UUID deviceId, String reason
	) {
	}
}
