package com.gymos.access.infrastructure;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class AccessRepository {
	private final JdbcClient jdbcClient;

	public AccessRepository(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	public List<AccessDeviceRecord> devices(UUID organizationId, UUID branchId) {
		return jdbcClient.sql("""
				SELECT id, name, device_code, device_type, status, provider, last_seen_at
				FROM access_device
				WHERE organization_id = :organizationId
				  AND branch_id = :branchId
				  AND deleted_at IS NULL
				ORDER BY name
				""")
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.query((resultSet, rowNumber) -> new AccessDeviceRecord(
						resultSet.getObject("id", UUID.class),
						resultSet.getString("name"),
						resultSet.getString("device_code"),
						resultSet.getString("device_type"),
						resultSet.getString("status"),
						resultSet.getString("provider"),
						resultSet.getObject("last_seen_at", OffsetDateTime.class)
				))
				.list();
	}

	public boolean deviceCodeExists(UUID organizationId, String deviceCode) {
		return Boolean.TRUE.equals(jdbcClient.sql("""
				SELECT EXISTS (
				    SELECT 1
				    FROM access_device
				    WHERE organization_id = :organizationId
				      AND device_code = :deviceCode
				      AND deleted_at IS NULL
				)
				""")
				.param("organizationId", organizationId)
				.param("deviceCode", deviceCode)
				.query(Boolean.class)
				.single());
	}

	public AccessDeviceRecord createDevice(
			UUID organizationId,
			UUID branchId,
			String name,
			String deviceCode,
			String deviceType,
			String provider,
			UUID actorUserId
	) {
		OffsetDateTime now = OffsetDateTime.now();
		return jdbcClient.sql("""
				INSERT INTO access_device (
				    id, organization_id, branch_id, name, device_code, device_type,
				    status, provider, created_at, updated_at, created_by, updated_by, version
				)
				VALUES (
				    :id, :organizationId, :branchId, :name, :deviceCode, :deviceType,
				    'ACTIVE', :provider, :now, :now, :actorUserId, :actorUserId, 0
				)
				RETURNING id, name, device_code, device_type, status, provider, last_seen_at
				""")
				.param("id", UUID.randomUUID())
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("name", name)
				.param("deviceCode", deviceCode)
				.param("deviceType", deviceType)
				.param("provider", provider)
				.param("now", now)
				.param("actorUserId", actorUserId)
				.query(this::mapDevice)
				.single();
	}

	public Optional<AccessDeviceRecord> defaultDevice(UUID organizationId, UUID branchId) {
		return jdbcClient.sql("""
				SELECT id, name, device_code, device_type, status, provider, last_seen_at
				FROM access_device
				WHERE organization_id = :organizationId
				  AND branch_id = :branchId
				  AND status = 'ACTIVE'
				  AND deleted_at IS NULL
				ORDER BY name
				LIMIT 1
				""")
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.query(this::mapDevice)
				.optional();
	}

	public Optional<AccessDeviceRecord> deviceByCode(UUID organizationId, UUID branchId, String deviceCode) {
		return jdbcClient.sql("""
				SELECT id, name, device_code, device_type, status, provider, last_seen_at
				FROM access_device
				WHERE organization_id = :organizationId
				  AND branch_id = :branchId
				  AND device_code = :deviceCode
				  AND status = 'ACTIVE'
				  AND deleted_at IS NULL
				""")
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("deviceCode", deviceCode)
				.query(this::mapDevice)
				.optional();
	}

	public Optional<AccessDeviceRecord> deviceById(UUID organizationId, UUID branchId, UUID deviceId) {
		return jdbcClient.sql("""
				SELECT id, name, device_code, device_type, status, provider, last_seen_at
				FROM access_device
				WHERE organization_id = :organizationId
				  AND branch_id = :branchId
				  AND id = :deviceId
				  AND deleted_at IS NULL
				""")
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("deviceId", deviceId)
				.query(this::mapDevice)
				.optional();
	}

	public Optional<ClientAccessRecord> clientById(UUID organizationId, UUID clientId) {
		return jdbcClient.sql("""
				SELECT c.id, p.first_name || ' ' || p.last_name AS full_name,
				       p.phone, p.email, c.status
				FROM client c
				JOIN person p ON p.id = c.person_id
				WHERE c.organization_id = :organizationId
				  AND c.id = :clientId
				  AND c.deleted_at IS NULL
				  AND p.deleted_at IS NULL
				""")
				.param("organizationId", organizationId)
				.param("clientId", clientId)
				.query(this::mapClient)
				.optional();
	}

	public Optional<ClientAccessRecord> clientById(UUID organizationId, UUID branchId, UUID clientId) {
		return jdbcClient.sql("""
				SELECT c.id, p.first_name || ' ' || p.last_name AS full_name,
				       p.phone, p.email, c.status
				FROM client c
				JOIN person p ON p.id = c.person_id
				WHERE c.organization_id = :organizationId
				  AND c.id = :clientId
				  AND c.deleted_at IS NULL
				  AND p.deleted_at IS NULL
				  AND (
				      c.home_branch_id IS NULL
				      OR c.home_branch_id = :branchId
				      OR EXISTS (
				          SELECT 1
				          FROM membership m
				          JOIN membership_plan mp ON mp.id = m.plan_id
				          WHERE m.organization_id = c.organization_id
				            AND m.client_id = c.id
				            AND m.status = 'ACTIVE'
				            AND m.deleted_at IS NULL
				            AND (m.branch_id = :branchId OR mp.multi_branch_access = TRUE)
				      )
				  )
				""")
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("clientId", clientId)
				.query(this::mapClient)
				.optional();
	}

	public Optional<ClientAccessRecord> clientByIdentifier(UUID organizationId, String identifier) {
		String normalized = identifier == null ? "" : identifier.trim().toLowerCase();
		return jdbcClient.sql("""
				SELECT c.id, p.first_name || ' ' || p.last_name AS full_name,
				       p.phone, p.email, c.status
				FROM client c
				JOIN person p ON p.id = c.person_id
				WHERE c.organization_id = :organizationId
				  AND c.deleted_at IS NULL
				  AND p.deleted_at IS NULL
				  AND (
				      LOWER(COALESCE(p.email, '')) = :identifier
				      OR LOWER(COALESCE(p.phone, '')) = :identifier
				  )
				ORDER BY c.joined_at DESC
				LIMIT 1
				""")
				.param("organizationId", organizationId)
				.param("identifier", normalized)
				.query(this::mapClient)
				.optional();
	}

	public List<UUID> deviceRequiredBenefitIds(UUID organizationId, UUID branchId, UUID deviceId) {
		return jdbcClient.sql("""
				SELECT adb.benefit_id
				FROM access_device_benefit adb
				JOIN access_device d
				  ON d.id = adb.access_device_id
				 AND d.organization_id = adb.organization_id
				WHERE adb.organization_id = :organizationId
				  AND adb.access_device_id = :deviceId
				  AND d.branch_id = :branchId
				  AND d.status = 'ACTIVE'
				  AND d.deleted_at IS NULL
				ORDER BY adb.benefit_id
				""")
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("deviceId", deviceId)
				.query(UUID.class)
				.list();
	}

	public boolean accessBenefitsExist(UUID organizationId, UUID branchId, List<UUID> benefitIds) {
		if (benefitIds == null || benefitIds.isEmpty()) {
			return true;
		}
		List<UUID> distinctIds = benefitIds.stream().distinct().toList();
		Integer count = jdbcClient.sql("""
				SELECT COUNT(*)
				FROM benefit benefit
				WHERE benefit.organization_id = :organizationId
				  AND benefit.id IN (:benefitIds)
				  AND benefit.benefit_type = 'ACCESS'
				  AND benefit.status = 'ACTIVE'
				  AND benefit.deleted_at IS NULL
				  AND (
				      (benefit.access_scope = 'LOCAL'
				       AND (benefit.branch_id IS NULL OR benefit.branch_id = :branchId))
				      OR
				      (benefit.access_scope = 'PLAN_COVERAGE' AND EXISTS (
				          SELECT 1
				          FROM benefit_membership_plan benefit_plan
				          JOIN membership_plan membership_plan
				            ON membership_plan.organization_id = benefit_plan.organization_id
				           AND membership_plan.id = benefit_plan.membership_plan_id
				           AND membership_plan.status = 'ACTIVE'
				           AND membership_plan.deleted_at IS NULL
				          WHERE benefit_plan.organization_id = benefit.organization_id
				            AND benefit_plan.benefit_id = benefit.id
				            AND (
				                membership_plan.multi_branch_access = TRUE
				                OR EXISTS (
				                    SELECT 1 FROM catalog_product_branch product_branch
				                    WHERE product_branch.organization_id = membership_plan.organization_id
				                      AND product_branch.product_id = membership_plan.product_id
				                      AND product_branch.branch_id = :branchId
				                )
				            )
				      ))
				  )
				""")
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("benefitIds", distinctIds)
				.query(Integer.class)
				.single();
		return count == distinctIds.size();
	}

	public List<PhysicalAccessBenefitRecord> physicalAccessBenefits(UUID organizationId, UUID branchId) {
		return jdbcClient.sql("""
				SELECT benefit.id, benefit.code, benefit.name
				FROM benefit benefit
				WHERE benefit.organization_id = :organizationId
				  AND benefit.benefit_type = 'ACCESS'
				  AND benefit.status = 'ACTIVE'
				  AND benefit.deleted_at IS NULL
				  AND (
				      (benefit.access_scope = 'LOCAL'
				       AND (benefit.branch_id IS NULL OR benefit.branch_id = :branchId))
				      OR
				      (benefit.access_scope = 'PLAN_COVERAGE' AND EXISTS (
				          SELECT 1
				          FROM benefit_membership_plan benefit_plan
				          JOIN membership_plan membership_plan
				            ON membership_plan.organization_id = benefit_plan.organization_id
				           AND membership_plan.id = benefit_plan.membership_plan_id
				           AND membership_plan.status = 'ACTIVE'
				           AND membership_plan.deleted_at IS NULL
				          WHERE benefit_plan.organization_id = benefit.organization_id
				            AND benefit_plan.benefit_id = benefit.id
				            AND (
				                membership_plan.multi_branch_access = TRUE
				                OR EXISTS (
				                    SELECT 1 FROM catalog_product_branch product_branch
				                    WHERE product_branch.organization_id = membership_plan.organization_id
				                      AND product_branch.product_id = membership_plan.product_id
				                      AND product_branch.branch_id = :branchId
				                )
				            )
				      ))
				  )
				ORDER BY benefit.name, benefit.code
				""")
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.query((resultSet, rowNumber) -> new PhysicalAccessBenefitRecord(
						resultSet.getObject("id", UUID.class),
						resultSet.getString("code"),
						resultSet.getString("name")
				))
				.list();
	}

	public Optional<PhysicalProviderConnectionRecord> physicalProviderConnection(UUID organizationId, UUID branchId) {
		return jdbcClient.sql("""
				SELECT id, provider_key, display_name, connector_key, enabled, sync_status,
				       last_heartbeat_at, last_synchronized_at, last_error
				FROM access_provider_connection
				WHERE organization_id = :organizationId
				  AND branch_id = :branchId
				ORDER BY created_at
				LIMIT 1
				""")
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.query(this::mapPhysicalProviderConnection)
				.optional();
	}

	public PhysicalProviderConnectionRecord upsertPhysicalProviderConnection(
			UUID organizationId,
			UUID branchId,
			String providerKey,
			String displayName,
			String connectorKey,
			boolean enabled,
			UUID actorUserId
	) {
		OffsetDateTime now = OffsetDateTime.now();
		return jdbcClient.sql("""
				INSERT INTO access_provider_connection (
				    id, organization_id, branch_id, provider_key, display_name, connector_key,
				    enabled, sync_status, created_at, updated_at, created_by, updated_by, version
				)
				VALUES (
				    :id, :organizationId, :branchId, :providerKey, :displayName, :connectorKey,
				    :enabled, :syncStatus, :now, :now, :actorUserId, :actorUserId, 0
				)
				ON CONFLICT (organization_id, branch_id)
				DO UPDATE SET
				    provider_key = EXCLUDED.provider_key,
				    display_name = EXCLUDED.display_name,
				    connector_key = EXCLUDED.connector_key,
				    enabled = EXCLUDED.enabled,
				    sync_status = EXCLUDED.sync_status,
				    last_error = CASE WHEN EXCLUDED.enabled THEN access_provider_connection.last_error ELSE NULL END,
				    updated_at = EXCLUDED.updated_at,
				    updated_by = EXCLUDED.updated_by,
				    version = access_provider_connection.version + 1
				RETURNING id, provider_key, display_name, connector_key, enabled, sync_status,
				          last_heartbeat_at, last_synchronized_at, last_error
				""")
				.param("id", UUID.randomUUID())
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("providerKey", providerKey)
				.param("displayName", displayName)
				.param("connectorKey", connectorKey)
				.param("enabled", enabled)
				.param("syncStatus", enabled ? "AWAITING_CONNECTOR" : "DISABLED")
				.param("now", now)
				.param("actorUserId", actorUserId)
				.query(this::mapPhysicalProviderConnection)
				.single();
	}

	public List<PhysicalBenefitMappingRecord> physicalBenefitMappings(UUID organizationId, UUID branchId, UUID connectionId) {
		return jdbcClient.sql("""
				SELECT m.benefit_id, b.code AS benefit_code, b.name AS benefit_name,
				       m.external_access_level_ref, m.external_access_level_name
				FROM access_provider_benefit_mapping m
				JOIN benefit b ON b.id = m.benefit_id
				WHERE m.organization_id = :organizationId
				  AND m.branch_id = :branchId
				  AND m.connection_id = :connectionId
				ORDER BY b.name, b.code
				""")
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("connectionId", connectionId)
				.query((resultSet, rowNumber) -> new PhysicalBenefitMappingRecord(
						resultSet.getObject("benefit_id", UUID.class),
						resultSet.getString("benefit_code"),
						resultSet.getString("benefit_name"),
						resultSet.getString("external_access_level_ref"),
						resultSet.getString("external_access_level_name")
				))
				.list();
	}

	public void replacePhysicalBenefitMappings(
			UUID organizationId,
			UUID branchId,
			UUID connectionId,
			List<PhysicalBenefitMappingRecord> mappings,
			UUID actorUserId
	) {
		jdbcClient.sql("""
				DELETE FROM access_provider_benefit_mapping
				WHERE organization_id = :organizationId
				  AND branch_id = :branchId
				  AND connection_id = :connectionId
				""")
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("connectionId", connectionId)
				.update();
		OffsetDateTime now = OffsetDateTime.now();
		for (PhysicalBenefitMappingRecord mapping : mappings) {
			jdbcClient.sql("""
					INSERT INTO access_provider_benefit_mapping (
					    id, organization_id, branch_id, connection_id, benefit_id,
					    external_access_level_ref, external_access_level_name,
					    created_at, updated_at, created_by, updated_by, version
					)
					VALUES (
					    :id, :organizationId, :branchId, :connectionId, :benefitId,
					    :externalAccessLevelRef, :externalAccessLevelName,
					    :now, :now, :actorUserId, :actorUserId, 0
					)
					""")
					.param("id", UUID.randomUUID())
					.param("organizationId", organizationId)
					.param("branchId", branchId)
					.param("connectionId", connectionId)
					.param("benefitId", mapping.benefitId())
					.param("externalAccessLevelRef", mapping.externalAccessLevelRef())
					.param("externalAccessLevelName", mapping.externalAccessLevelName())
					.param("now", now)
					.param("actorUserId", actorUserId)
					.update();
		}
	}

	public Optional<PhysicalClientLinkRecord> physicalClientLink(UUID organizationId, UUID branchId, UUID connectionId, UUID clientId) {
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
				.query((resultSet, rowNumber) -> new PhysicalClientLinkRecord(
						resultSet.getObject("client_id", UUID.class),
						resultSet.getString("external_person_ref"),
						resultSet.getObject("linked_at", OffsetDateTime.class)
				))
				.optional();
	}

	public PhysicalClientLinkRecord upsertPhysicalClientLink(
			UUID organizationId,
			UUID branchId,
			UUID connectionId,
			UUID clientId,
			String externalPersonRef,
			UUID actorUserId
	) {
		OffsetDateTime now = OffsetDateTime.now();
		return jdbcClient.sql("""
				INSERT INTO access_provider_client_link (
				    id, organization_id, branch_id, connection_id, client_id, external_person_ref,
				    linked_at, linked_by, updated_at, updated_by, version
				)
				VALUES (
				    :id, :organizationId, :branchId, :connectionId, :clientId, :externalPersonRef,
				    :now, :actorUserId, :now, :actorUserId, 0
				)
				ON CONFLICT (connection_id, client_id)
				DO UPDATE SET
				    external_person_ref = EXCLUDED.external_person_ref,
				    updated_at = EXCLUDED.updated_at,
				    updated_by = EXCLUDED.updated_by,
				    version = access_provider_client_link.version + 1
				RETURNING client_id, external_person_ref, linked_at
				""")
				.param("id", UUID.randomUUID())
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("connectionId", connectionId)
				.param("clientId", clientId)
				.param("externalPersonRef", externalPersonRef)
				.param("now", now)
				.param("actorUserId", actorUserId)
				.query((resultSet, rowNumber) -> new PhysicalClientLinkRecord(
						resultSet.getObject("client_id", UUID.class),
						resultSet.getString("external_person_ref"),
						resultSet.getObject("linked_at", OffsetDateTime.class)
				))
				.single();
	}

	public Optional<PhysicalSyncStateRecord> physicalSyncState(UUID organizationId, UUID branchId, UUID connectionId, UUID clientId) {
		return jdbcClient.sql("""
				SELECT desired_access_level_refs::text AS desired_access_level_refs, action, status,
				       requested_at, last_synchronized_at, last_error
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
				.query(this::mapPhysicalSyncState)
				.optional();
	}

	public PhysicalSyncStateRecord upsertPhysicalSyncState(
			UUID organizationId,
			UUID branchId,
			UUID connectionId,
			UUID clientId,
			String desiredAccessLevelRefsJson,
			String action,
			String status,
			String lastError,
			UUID actorUserId
	) {
		OffsetDateTime now = OffsetDateTime.now();
		return jdbcClient.sql("""
				INSERT INTO access_provider_sync_state (
				    id, organization_id, branch_id, connection_id, client_id,
				    desired_access_level_refs, action, status, requested_at, last_error,
				    created_at, updated_at, created_by, updated_by, version
				)
				VALUES (
				    :id, :organizationId, :branchId, :connectionId, :clientId,
				    CAST(:desiredAccessLevelRefsJson AS jsonb), :action, :status, :now, :lastError,
				    :now, :now, :actorUserId, :actorUserId, 0
				)
				ON CONFLICT (connection_id, client_id)
				DO UPDATE SET
				    desired_access_level_refs = EXCLUDED.desired_access_level_refs,
				    action = EXCLUDED.action,
				    status = EXCLUDED.status,
				    requested_at = EXCLUDED.requested_at,
				    last_error = EXCLUDED.last_error,
				    updated_at = EXCLUDED.updated_at,
				    updated_by = EXCLUDED.updated_by,
				    version = access_provider_sync_state.version + 1
				RETURNING desired_access_level_refs::text AS desired_access_level_refs, action, status,
				          requested_at, last_synchronized_at, last_error
				""")
				.param("id", UUID.randomUUID())
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("connectionId", connectionId)
				.param("clientId", clientId)
				.param("desiredAccessLevelRefsJson", desiredAccessLevelRefsJson)
				.param("action", action)
				.param("status", status)
				.param("now", now)
				.param("lastError", lastError)
				.param("actorUserId", actorUserId)
				.query(this::mapPhysicalSyncState)
				.single();
	}

	public void replaceDeviceBenefits(
			UUID organizationId,
			UUID branchId,
			UUID deviceId,
			UUID actorUserId,
			List<UUID> benefitIds
	) {
		jdbcClient.sql("""
				DELETE FROM access_device_benefit
				WHERE organization_id = :organizationId
				  AND access_device_id = :deviceId
				  AND EXISTS (
				      SELECT 1
				      FROM access_device d
				      WHERE d.id = access_device_benefit.access_device_id
				        AND d.organization_id = access_device_benefit.organization_id
				        AND d.branch_id = :branchId
				  )
				""")
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("deviceId", deviceId)
				.update();
		OffsetDateTime now = OffsetDateTime.now();
		for (UUID benefitId : benefitIds.stream().distinct().toList()) {
			jdbcClient.sql("""
					INSERT INTO access_device_benefit (
					    access_device_id, benefit_id, organization_id, created_at, created_by
					)
					VALUES (:deviceId, :benefitId, :organizationId, :now, :actorUserId)
					""")
					.param("deviceId", deviceId)
					.param("benefitId", benefitId)
					.param("organizationId", organizationId)
					.param("now", now)
					.param("actorUserId", actorUserId)
					.update();
		}
	}

	public Optional<TemporaryPassRecord> validTemporaryPass(UUID organizationId, UUID branchId, UUID clientId, OffsetDateTime now) {
		return jdbcClient.sql("""
				SELECT p.id, p.client_id, person.first_name || ' ' || person.last_name AS client_name,
				       p.status, p.starts_at, p.expires_at, p.max_uses, p.uses_count, p.reason
				FROM access_temporary_pass p
				JOIN client c ON c.id = p.client_id
				JOIN person person ON person.id = c.person_id
				WHERE p.organization_id = :organizationId
				  AND p.branch_id = :branchId
				  AND p.client_id = :clientId
				  AND p.status = 'ACTIVE'
				  AND p.starts_at <= :now
				  AND p.expires_at >= :now
				  AND p.uses_count < p.max_uses
				  AND p.deleted_at IS NULL
				ORDER BY p.expires_at
				LIMIT 1
				""")
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("clientId", clientId)
				.param("now", now)
				.query(this::mapTemporaryPass)
				.optional();
	}

	public Optional<TemporaryPassRecord> temporaryPass(UUID organizationId, UUID passId) {
		return jdbcClient.sql("""
				SELECT p.id, p.client_id, person.first_name || ' ' || person.last_name AS client_name,
				       p.status, p.starts_at, p.expires_at, p.max_uses, p.uses_count, p.reason
				FROM access_temporary_pass p
				JOIN client c ON c.id = p.client_id
				JOIN person person ON person.id = c.person_id
				WHERE p.organization_id = :organizationId
				  AND p.id = :passId
				  AND p.deleted_at IS NULL
				""")
				.param("organizationId", organizationId)
				.param("passId", passId)
				.query(this::mapTemporaryPass)
				.optional();
	}

	public UUID createTemporaryPass(
			UUID organizationId,
			UUID branchId,
			UUID clientId,
			UUID actorUserId,
			OffsetDateTime startsAt,
			OffsetDateTime expiresAt,
			int maxUses,
			String reason
	) {
		OffsetDateTime now = OffsetDateTime.now();
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
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("clientId", clientId)
				.param("startsAt", startsAt)
				.param("expiresAt", expiresAt)
				.param("maxUses", maxUses)
				.param("reason", reason)
				.param("createdAt", now)
				.param("updatedAt", now)
				.param("createdBy", actorUserId)
				.param("updatedBy", actorUserId)
				.update();
		return passId;
	}

	public UUID createAccessLog(
			UUID organizationId,
			UUID branchId,
			UUID deviceId,
			UUID clientId,
			UUID membershipId,
			UUID temporaryPassId,
			String accessType,
			String decision,
			String source,
			String reason,
			String commandStatus,
			OffsetDateTime occurredAt,
			UUID actorUserId,
			UUID correlationId
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
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("deviceId", deviceId)
				.param("clientId", clientId)
				.param("membershipId", membershipId)
				.param("temporaryPassId", temporaryPassId)
				.param("accessType", accessType)
				.param("decision", decision)
				.param("source", source)
				.param("reason", reason)
				.param("commandStatus", commandStatus)
				.param("occurredAt", occurredAt)
				.param("createdAt", occurredAt)
				.param("createdBy", actorUserId)
				.param("correlationId", correlationId)
				.update();
		return logId;
	}

	public boolean consumeTemporaryPass(
			UUID organizationId,
			UUID branchId,
			UUID passId,
			OffsetDateTime now
	) {
		int updated = jdbcClient.sql("""
				UPDATE access_temporary_pass
				SET uses_count = uses_count + 1,
				    status = CASE WHEN uses_count + 1 >= max_uses THEN 'CONSUMED' ELSE status END,
				    updated_at = :updatedAt,
				    version = version + 1
				WHERE organization_id = :organizationId
				  AND branch_id = :branchId
				  AND id = :passId
				  AND status = 'ACTIVE'
				  AND starts_at <= :updatedAt
				  AND expires_at >= :updatedAt
				  AND uses_count < max_uses
				  AND deleted_at IS NULL
				""")
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("updatedAt", now)
				.param("passId", passId)
				.update();
		return updated == 1;
	}

	public Optional<AccessDecisionResponse> decision(UUID logId) {
		return jdbcClient.sql("""
				SELECT l.id, l.decision, l.access_type, l.reason, l.command_status, l.occurred_at,
				       d.name AS device_name, d.device_code,
				       c.id AS client_id, person.first_name || ' ' || person.last_name AS client_name,
				       m.id AS membership_id, cp.name AS plan_name,
				       p.id AS temporary_pass_id
				FROM access_log l
				LEFT JOIN access_device d ON d.id = l.device_id
				LEFT JOIN client c ON c.id = l.client_id
				LEFT JOIN person person ON person.id = c.person_id
				LEFT JOIN membership m ON m.id = l.membership_id
				LEFT JOIN membership_plan mp ON mp.id = m.plan_id
				LEFT JOIN catalog_product cp ON cp.id = mp.product_id
				LEFT JOIN access_temporary_pass p ON p.id = l.temporary_pass_id
				WHERE l.id = :logId
				""")
				.param("logId", logId)
				.query((resultSet, rowNumber) -> new AccessDecisionResponse(
						resultSet.getObject("id", UUID.class),
						resultSet.getString("decision"),
						resultSet.getString("access_type"),
						resultSet.getString("reason"),
						resultSet.getString("command_status"),
						resultSet.getObject("occurred_at", OffsetDateTime.class),
						resultSet.getString("device_name"),
						resultSet.getString("device_code"),
						resultSet.getObject("client_id", UUID.class),
						resultSet.getString("client_name"),
						resultSet.getObject("membership_id", UUID.class),
						resultSet.getString("plan_name"),
						resultSet.getObject("temporary_pass_id", UUID.class)
				))
				.optional();
	}

	public List<AccessLogRecord> logs(UUID organizationId, UUID branchId, int limit) {
		return jdbcClient.sql("""
				SELECT l.id, l.decision, l.access_type, l.source, l.reason, l.command_status, l.occurred_at,
				       d.name AS device_name, d.device_code,
				       c.id AS client_id, person.first_name || ' ' || person.last_name AS client_name,
				       reversal.occurred_at AS reversed_at
				FROM access_log l
				LEFT JOIN access_device d ON d.id = l.device_id
				LEFT JOIN client c ON c.id = l.client_id
				LEFT JOIN person person ON person.id = c.person_id
				LEFT JOIN access_manual_reversal reversal ON reversal.access_log_id = l.id
				WHERE l.organization_id = :organizationId
				  AND l.branch_id = :branchId
				ORDER BY l.occurred_at DESC
				LIMIT :limit
				""")
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("limit", limit)
				.query((resultSet, rowNumber) -> new AccessLogRecord(
						resultSet.getObject("id", UUID.class),
						resultSet.getString("decision"),
						resultSet.getString("access_type"),
						resultSet.getString("source"),
						resultSet.getString("reason"),
						resultSet.getString("command_status"),
						resultSet.getObject("occurred_at", OffsetDateTime.class),
						resultSet.getString("device_name"),
						resultSet.getString("device_code"),
						resultSet.getObject("client_id", UUID.class),
						resultSet.getString("client_name"),
						resultSet.getObject("reversed_at", OffsetDateTime.class)
				))
				.list();
	}

	public List<VisitPassRecord> visitPasses(UUID organizationId, UUID branchId, UUID clientId) {
		var statement = jdbcClient.sql("""
				SELECT vp.id, vp.client_id, person.first_name || ' ' || person.last_name AS client_name,
				       vp.sale_id, vp.pass_type, vp.status, vp.total_visits, vp.remaining_visits,
				       vp.valid_from, vp.valid_until, vp.created_at, vp.updated_at
				FROM visit_pass vp
				LEFT JOIN client c ON c.id = vp.client_id
				LEFT JOIN person person ON person.id = c.person_id
				WHERE vp.organization_id = :organizationId
				  AND vp.branch_id = :branchId
				  AND (:clientId IS NULL OR vp.client_id = :clientId)
				ORDER BY vp.updated_at DESC
				LIMIT 100
				""")
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("clientId", clientId);
		return statement.query(this::mapVisitPass).list();
	}

	public Optional<VisitPassRecord> visitPass(UUID organizationId, UUID branchId, UUID visitPassId) {
		return jdbcClient.sql("""
				SELECT vp.id, vp.client_id, person.first_name || ' ' || person.last_name AS client_name,
				       vp.sale_id, vp.pass_type, vp.status, vp.total_visits, vp.remaining_visits,
				       vp.valid_from, vp.valid_until, vp.created_at, vp.updated_at
				FROM visit_pass vp
				LEFT JOIN client c ON c.id = vp.client_id
				LEFT JOIN person person ON person.id = c.person_id
				WHERE vp.organization_id = :organizationId
				  AND vp.branch_id = :branchId
				  AND vp.id = :visitPassId
				""")
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("visitPassId", visitPassId)
				.query(this::mapVisitPass)
				.optional();
	}

	public UUID createVisitPass(
			UUID organizationId,
			UUID branchId,
			UUID clientId,
			UUID saleId,
			String passType,
			int totalVisits,
			LocalDate validFrom,
			LocalDate validUntil
	) {
		OffsetDateTime now = OffsetDateTime.now();
		UUID passId = UUID.randomUUID();
		jdbcClient.sql("""
				INSERT INTO visit_pass (
				    id, organization_id, branch_id, client_id, sale_id, pass_type, status,
				    total_visits, remaining_visits, valid_from, valid_until, created_at, updated_at, version
				)
				VALUES (
				    :id, :organizationId, :branchId, :clientId, :saleId, :passType, 'ACTIVE',
				    :totalVisits, :remainingVisits, :validFrom, :validUntil, :createdAt, :updatedAt, 0
				)
				""")
				.param("id", passId)
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("clientId", clientId)
				.param("saleId", saleId)
				.param("passType", passType == null ? "VISIT_PACK" : passType.trim().toUpperCase())
				.param("totalVisits", totalVisits)
				.param("remainingVisits", totalVisits)
				.param("validFrom", validFrom)
				.param("validUntil", validUntil)
				.param("createdAt", now)
				.param("updatedAt", now)
				.update();
		return passId;
	}

	public boolean consumeVisitPass(UUID organizationId, UUID branchId, UUID visitPassId, OffsetDateTime now) {
		int updated = jdbcClient.sql("""
				UPDATE visit_pass
				SET remaining_visits = remaining_visits - 1,
				    status = CASE WHEN remaining_visits - 1 <= 0 THEN 'CONSUMED' ELSE status END,
				    updated_at = :updatedAt,
				    version = version + 1
				WHERE organization_id = :organizationId
				  AND branch_id = :branchId
				  AND id = :visitPassId
				  AND status = 'ACTIVE'
				  AND remaining_visits > 0
				""")
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("visitPassId", visitPassId)
				.param("updatedAt", now)
				.update();
		return updated == 1;
	}

	private AccessDeviceRecord mapDevice(java.sql.ResultSet resultSet, int rowNumber) throws java.sql.SQLException {
		return new AccessDeviceRecord(
				resultSet.getObject("id", UUID.class),
				resultSet.getString("name"),
				resultSet.getString("device_code"),
				resultSet.getString("device_type"),
				resultSet.getString("status"),
				resultSet.getString("provider"),
				resultSet.getObject("last_seen_at", OffsetDateTime.class)
		);
	}

	private ClientAccessRecord mapClient(java.sql.ResultSet resultSet, int rowNumber) throws java.sql.SQLException {
		return new ClientAccessRecord(
				resultSet.getObject("id", UUID.class),
				resultSet.getString("full_name"),
				resultSet.getString("phone"),
				resultSet.getString("email"),
				resultSet.getString("status")
		);
	}

	private TemporaryPassRecord mapTemporaryPass(java.sql.ResultSet resultSet, int rowNumber) throws java.sql.SQLException {
		return new TemporaryPassRecord(
				resultSet.getObject("id", UUID.class),
				resultSet.getObject("client_id", UUID.class),
				resultSet.getString("client_name"),
				resultSet.getString("status"),
				resultSet.getObject("starts_at", OffsetDateTime.class),
				resultSet.getObject("expires_at", OffsetDateTime.class),
				resultSet.getInt("max_uses"),
				resultSet.getInt("uses_count"),
				resultSet.getString("reason")
		);
	}

	private VisitPassRecord mapVisitPass(java.sql.ResultSet resultSet, int rowNumber) throws java.sql.SQLException {
		return new VisitPassRecord(
				resultSet.getObject("id", UUID.class),
				resultSet.getObject("client_id", UUID.class),
				resultSet.getString("client_name"),
				resultSet.getObject("sale_id", UUID.class),
				resultSet.getString("pass_type"),
				resultSet.getString("status"),
				resultSet.getInt("total_visits"),
				resultSet.getInt("remaining_visits"),
				resultSet.getObject("valid_from", LocalDate.class),
				resultSet.getObject("valid_until", LocalDate.class),
				resultSet.getObject("created_at", OffsetDateTime.class),
				resultSet.getObject("updated_at", OffsetDateTime.class)
		);
	}

	private PhysicalProviderConnectionRecord mapPhysicalProviderConnection(
			java.sql.ResultSet resultSet,
			int rowNumber
	) throws java.sql.SQLException {
		return new PhysicalProviderConnectionRecord(
				resultSet.getObject("id", UUID.class),
				resultSet.getString("provider_key"),
				resultSet.getString("display_name"),
				resultSet.getString("connector_key"),
				resultSet.getBoolean("enabled"),
				resultSet.getString("sync_status"),
				resultSet.getObject("last_heartbeat_at", OffsetDateTime.class),
				resultSet.getObject("last_synchronized_at", OffsetDateTime.class),
				resultSet.getString("last_error")
		);
	}

	private PhysicalSyncStateRecord mapPhysicalSyncState(
			java.sql.ResultSet resultSet,
			int rowNumber
	) throws java.sql.SQLException {
		return new PhysicalSyncStateRecord(
				resultSet.getString("desired_access_level_refs"),
				resultSet.getString("action"),
				resultSet.getString("status"),
				resultSet.getObject("requested_at", OffsetDateTime.class),
				resultSet.getObject("last_synchronized_at", OffsetDateTime.class),
				resultSet.getString("last_error")
		);
	}

	public record AccessDeviceRecord(
			UUID id,
			String name,
			String deviceCode,
			String deviceType,
			String status,
			String provider,
			OffsetDateTime lastSeenAt
	) {
	}

	public record ClientAccessRecord(UUID id, String fullName, String phone, String email, String status) {
	}

	public record EffectiveAccessBenefitRecord(
			UUID benefitId,
			String code,
			String name,
			UUID membershipId,
			UUID planId,
			String planName
	) {
	}

	public record PhysicalAccessBenefitRecord(UUID id, String code, String name) {
	}

	public record PhysicalProviderConnectionRecord(
			UUID id,
			String providerKey,
			String displayName,
			String connectorKey,
			boolean enabled,
			String syncStatus,
			OffsetDateTime lastHeartbeatAt,
			OffsetDateTime lastSynchronizedAt,
			String lastError
	) {
	}

	public record PhysicalBenefitMappingRecord(
			UUID benefitId,
			String benefitCode,
			String benefitName,
			String externalAccessLevelRef,
			String externalAccessLevelName
	) {
	}

	public record PhysicalClientLinkRecord(
			UUID clientId,
			String externalPersonRef,
			OffsetDateTime linkedAt
	) {
	}

	public record PhysicalSyncStateRecord(
			String desiredAccessLevelRefsJson,
			String action,
			String status,
			OffsetDateTime requestedAt,
			OffsetDateTime lastSynchronizedAt,
			String lastError
	) {
	}

	public record TemporaryPassRecord(
			UUID id,
			UUID clientId,
			String clientName,
			String status,
			OffsetDateTime startsAt,
			OffsetDateTime expiresAt,
			int maxUses,
			int usesCount,
			String reason
	) {
	}

	public record VisitPassRecord(
			UUID id,
			UUID clientId,
			String clientName,
			UUID saleId,
			String passType,
			String status,
			int totalVisits,
			int remainingVisits,
			LocalDate validFrom,
			LocalDate validUntil,
			OffsetDateTime createdAt,
			OffsetDateTime updatedAt
	) {
	}

	public record AccessDecisionResponse(
			UUID logId,
			String decision,
			String accessType,
			String reason,
			String commandStatus,
			OffsetDateTime occurredAt,
			String deviceName,
			String deviceCode,
			UUID clientId,
			String clientName,
			UUID membershipId,
			String planName,
			UUID temporaryPassId
	) {
	}

	public record AccessLogRecord(
			UUID id,
			String decision,
			String accessType,
			String source,
			String reason,
			String commandStatus,
			OffsetDateTime occurredAt,
			String deviceName,
			String deviceCode,
			UUID clientId,
			String clientName,
			OffsetDateTime reversedAt
	) {
	}
}
