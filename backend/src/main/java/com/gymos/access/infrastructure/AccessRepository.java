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

	public Optional<ActiveMembershipRecord> activeMembership(UUID organizationId, UUID branchId, UUID clientId, LocalDate today) {
		return jdbcClient.sql("""
				SELECT m.id, m.plan_id, cp.name AS plan_name, m.starts_on, m.ends_on, m.grace_ends_on
				FROM membership m
				JOIN membership_plan mp ON mp.id = m.plan_id
				JOIN catalog_product cp ON cp.id = mp.product_id
				WHERE m.organization_id = :organizationId
				  AND m.branch_id = :branchId
				  AND m.client_id = :clientId
				  AND m.status = 'ACTIVE'
				  AND m.starts_on <= :today
				  AND m.grace_ends_on >= :today
				  AND m.deleted_at IS NULL
				ORDER BY m.ends_on DESC
				LIMIT 1
				""")
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("clientId", clientId)
				.param("today", today)
				.query((resultSet, rowNumber) -> new ActiveMembershipRecord(
						resultSet.getObject("id", UUID.class),
						resultSet.getObject("plan_id", UUID.class),
						resultSet.getString("plan_name"),
						resultSet.getObject("starts_on", LocalDate.class),
						resultSet.getObject("ends_on", LocalDate.class),
						resultSet.getObject("grace_ends_on", LocalDate.class)
				))
				.optional();
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

	public void consumeTemporaryPass(UUID passId, OffsetDateTime now) {
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
				       c.id AS client_id, person.first_name || ' ' || person.last_name AS client_name
				FROM access_log l
				LEFT JOIN access_device d ON d.id = l.device_id
				LEFT JOIN client c ON c.id = l.client_id
				LEFT JOIN person person ON person.id = c.person_id
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
						resultSet.getString("client_name")
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

	public record ActiveMembershipRecord(
			UUID id,
			UUID planId,
			String planName,
			LocalDate startsOn,
			LocalDate endsOn,
			LocalDate graceEndsOn
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
			String clientName
	) {
	}
}
