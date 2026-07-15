package com.gymos.audit.api;

import com.gymos.common.security.GymOsPrincipal;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

@Service
public class AuditService {
	private final JdbcClient jdbcClient;

	public AuditService(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	public void record(
			GymOsPrincipal actor,
			String action,
			String entityType,
			UUID entityId,
			String origin,
			String reason,
			String correlationId
	) {
		UUID organizationId = actor == null ? null : actor.organizationId();
		UUID branchId = actor == null ? null : actor.branchId();
		UUID actorUserId = actor == null ? null : actor.userId();
		String actorRole = actor == null ? "SYSTEM" : "USER";
		insert(organizationId, branchId, actorUserId, actorRole, action, entityType, entityId, origin, reason, correlationId);
	}

	public void recordSystem(
			UUID organizationId,
			UUID branchId,
			UUID actorUserId,
			String action,
			String entityType,
			UUID entityId,
			String origin,
			String reason,
			String correlationId
	) {
		insert(organizationId, branchId, actorUserId, "USER", action, entityType, entityId, origin, reason, correlationId);
	}

	private void insert(
			UUID organizationId,
			UUID branchId,
			UUID actorUserId,
			String actorRole,
			String action,
			String entityType,
			UUID entityId,
			String origin,
			String reason,
			String correlationId
	) {
		jdbcClient.sql("""
				INSERT INTO audit_log (
				    id, organization_id, branch_id, actor_user_id, actor_role, action,
				    entity_type, entity_id, reason, origin, correlation_id, occurred_at
				)
				VALUES (
				    :id, :organizationId, :branchId, :actorUserId, :actorRole, :action,
				    :entityType, :entityId, :reason, :origin, :correlationId, :occurredAt
				)
				""")
				.param("id", UUID.randomUUID())
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("actorUserId", actorUserId)
				.param("actorRole", actorRole)
				.param("action", action)
				.param("entityType", entityType)
				.param("entityId", entityId)
				.param("reason", reason)
				.param("origin", origin)
				.param("correlationId", safeCorrelationId(correlationId))
				.param("occurredAt", OffsetDateTime.now())
				.update();
	}

	private UUID safeCorrelationId(String correlationId) {
		try {
			return UUID.fromString(correlationId);
		} catch (RuntimeException exception) {
			return UUID.nameUUIDFromBytes(("gymos:" + correlationId).getBytes());
		}
	}
}
