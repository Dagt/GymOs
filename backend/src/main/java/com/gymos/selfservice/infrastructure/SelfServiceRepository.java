package com.gymos.selfservice.infrastructure;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class SelfServiceRepository {
	private final JdbcClient jdbcClient;

	public SelfServiceRepository(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	public Optional<TerminalRecord> terminal(String terminalCode, UUID organizationId, UUID branchId) {
		if (organizationId != null && branchId != null) {
			return terminalForBranchContext(terminalCode, organizationId, branchId);
		}
		if (organizationId != null) {
			return terminalForOrganizationContext(terminalCode, organizationId);
		}
		if (branchId != null) {
			return Optional.empty();
		}
		return terminalWithoutContext(terminalCode);
	}

	private Optional<TerminalRecord> terminalWithoutContext(String terminalCode) {
		return jdbcClient.sql("""
				SELECT sst.id, sst.organization_id, sst.branch_id, sst.name, sst.device_code, sst.status,
				       sst.allowed_actions_json::text AS allowed_actions_json,
				       o.trade_name, o.brand_name, o.logo_url, o.primary_color, o.accent_color, o.surface_color
				FROM self_service_terminal sst
				JOIN organization o ON o.id = sst.organization_id
				JOIN branch b ON b.id = sst.branch_id
				WHERE sst.device_code = :terminalCode
				  AND sst.status = 'ACTIVE'
				  AND sst.deleted_at IS NULL
				  AND b.deleted_at IS NULL
				ORDER BY sst.created_at, sst.id
				LIMIT 2
				""")
				.param("terminalCode", terminalCode)
				.query((resultSet, rowNumber) -> mapTerminal(resultSet))
				.list()
				.stream()
				.reduce((first, second) -> null);
	}

	private Optional<TerminalRecord> terminalForBranchContext(String terminalCode, UUID organizationId, UUID branchId) {
		return jdbcClient.sql("""
				SELECT sst.id, sst.organization_id, sst.branch_id, sst.name, sst.device_code, sst.status,
				       sst.allowed_actions_json::text AS allowed_actions_json,
				       o.trade_name, o.brand_name, o.logo_url, o.primary_color, o.accent_color, o.surface_color
				FROM self_service_terminal sst
				JOIN organization o ON o.id = sst.organization_id
				JOIN branch b ON b.id = sst.branch_id
				WHERE sst.device_code = :terminalCode
				  AND sst.organization_id = :organizationId
				  AND sst.branch_id = :branchId
				  AND sst.status = 'ACTIVE'
				  AND sst.deleted_at IS NULL
				  AND b.deleted_at IS NULL
				""")
				.param("terminalCode", terminalCode)
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.query((resultSet, rowNumber) -> mapTerminal(resultSet))
				.optional();
	}

	private Optional<TerminalRecord> terminalForOrganizationContext(String terminalCode, UUID organizationId) {
		return jdbcClient.sql("""
				SELECT sst.id, sst.organization_id, sst.branch_id, sst.name, sst.device_code, sst.status,
				       sst.allowed_actions_json::text AS allowed_actions_json,
				       o.trade_name, o.brand_name, o.logo_url, o.primary_color, o.accent_color, o.surface_color
				FROM self_service_terminal sst
				JOIN organization o ON o.id = sst.organization_id
				JOIN branch b ON b.id = sst.branch_id
				WHERE sst.device_code = :terminalCode
				  AND sst.organization_id = :organizationId
				  AND sst.status = 'ACTIVE'
				  AND sst.deleted_at IS NULL
				  AND b.deleted_at IS NULL
				ORDER BY CASE WHEN b.status = 'ACTIVE' THEN 0 ELSE 1 END, b.name
				LIMIT 1
				""")
				.param("terminalCode", terminalCode)
				.param("organizationId", organizationId)
				.query((resultSet, rowNumber) -> mapTerminal(resultSet))
				.optional();
	}

	private TerminalRecord mapTerminal(java.sql.ResultSet resultSet) throws java.sql.SQLException {
		return new TerminalRecord(
				resultSet.getObject("id", UUID.class),
				resultSet.getObject("organization_id", UUID.class),
				resultSet.getObject("branch_id", UUID.class),
				resultSet.getString("name"),
				resultSet.getString("device_code"),
				resultSet.getString("status"),
				resultSet.getString("allowed_actions_json"),
				resultSet.getString("trade_name"),
				resultSet.getString("brand_name"),
				resultSet.getString("logo_url"),
				resultSet.getString("primary_color"),
				resultSet.getString("accent_color"),
				resultSet.getString("surface_color")
		);
	}

	public Optional<SessionRecord> session(UUID sessionId) {
		return jdbcClient.sql("""
				SELECT s.id, s.organization_id, s.branch_id, s.terminal_id, s.client_id,
				       s.status, s.started_at, s.expires_at, s.ended_at, s.verification_method,
				       s.session_token_hash, s.verification_attempts, s.blocked_until,
				       t.allowed_actions_json::text AS allowed_actions_json
				FROM self_service_session s
				JOIN self_service_terminal t
				  ON t.id = s.terminal_id
				 AND t.organization_id = s.organization_id
				 AND t.status = 'ACTIVE'
				 AND t.deleted_at IS NULL
				WHERE s.id = :sessionId
				  AND s.deleted_at IS NULL
				""")
				.param("sessionId", sessionId)
				.query((resultSet, rowNumber) -> new SessionRecord(
						resultSet.getObject("id", UUID.class),
						resultSet.getObject("organization_id", UUID.class),
						resultSet.getObject("branch_id", UUID.class),
						resultSet.getObject("terminal_id", UUID.class),
						resultSet.getObject("client_id", UUID.class),
						resultSet.getString("status"),
						resultSet.getObject("started_at", OffsetDateTime.class),
						resultSet.getObject("expires_at", OffsetDateTime.class),
						resultSet.getObject("ended_at", OffsetDateTime.class),
						resultSet.getString("verification_method"),
						resultSet.getString("session_token_hash"),
						resultSet.getInt("verification_attempts"),
						resultSet.getObject("blocked_until", OffsetDateTime.class),
						resultSet.getString("allowed_actions_json")
				))
				.optional();
	}

	public void createSession(
			UUID sessionId,
			UUID organizationId,
			UUID branchId,
			UUID terminalId,
			OffsetDateTime startedAt,
			OffsetDateTime expiresAt,
			String originIp,
			String sessionTokenHash
	) {
		jdbcClient.sql("""
				INSERT INTO self_service_session (
				    id, organization_id, branch_id, terminal_id, status, started_at,
				    expires_at, origin_ip, session_token_hash, created_at, updated_at, version
				)
				VALUES (
				    :id, :organizationId, :branchId, :terminalId, 'ACTIVE', :startedAt,
				    :expiresAt, :originIp, :sessionTokenHash, :createdAt, :updatedAt, 0
				)
				""")
				.param("id", sessionId)
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("terminalId", terminalId)
				.param("startedAt", startedAt)
				.param("expiresAt", expiresAt)
				.param("originIp", originIp)
				.param("sessionTokenHash", sessionTokenHash)
				.param("createdAt", startedAt)
				.param("updatedAt", startedAt)
				.update();
	}

	public void markVerified(UUID sessionId, UUID clientId, OffsetDateTime expiresAt) {
		OffsetDateTime now = OffsetDateTime.now();
		jdbcClient.sql("""
				UPDATE self_service_session
				SET client_id = :clientId,
				    verification_method = 'CONTACT_BIRTH_DATE',
				    verification_attempts = 0,
				    blocked_until = NULL,
				    expires_at = :expiresAt,
				    updated_at = :updatedAt,
				    version = version + 1
				WHERE id = :sessionId
				""")
				.param("clientId", clientId)
				.param("expiresAt", expiresAt)
				.param("updatedAt", now)
				.param("sessionId", sessionId)
				.update();
	}

	public void recordFailedVerification(UUID sessionId, int attempts, OffsetDateTime blockedUntil) {
		jdbcClient.sql("""
				UPDATE self_service_session
				SET verification_attempts = :verificationAttempts,
				    blocked_until = :blockedUntil,
				    updated_at = :updatedAt,
				    version = version + 1
				WHERE id = :sessionId
				""")
				.param("verificationAttempts", attempts)
				.param("blockedUntil", blockedUntil)
				.param("updatedAt", OffsetDateTime.now())
				.param("sessionId", sessionId)
				.update();
	}

	public void endSession(UUID sessionId) {
		OffsetDateTime now = OffsetDateTime.now();
		jdbcClient.sql("""
				UPDATE self_service_session
				SET status = 'ENDED',
				    ended_at = :endedAt,
				    updated_at = :updatedAt,
				    version = version + 1
				WHERE id = :sessionId
				""")
				.param("endedAt", now)
				.param("updatedAt", now)
				.param("sessionId", sessionId)
				.update();
	}

	public List<MembershipRecord> memberships(UUID organizationId, UUID branchId, UUID clientId) {
		return jdbcClient.sql("""
				SELECT m.id, m.plan_id, cp.name AS plan_name, m.status,
				       m.starts_on, m.ends_on, m.grace_ends_on
				FROM membership m
				JOIN membership_plan mp ON mp.id = m.plan_id
				JOIN catalog_product cp ON cp.id = mp.product_id
				WHERE m.organization_id = :organizationId
				  AND (m.branch_id = :branchId OR mp.multi_branch_access = true)
				  AND m.client_id = :clientId
				  AND m.deleted_at IS NULL
				ORDER BY m.starts_on DESC, m.activated_at DESC
				LIMIT 5
				""")
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("clientId", clientId)
				.query((resultSet, rowNumber) -> new MembershipRecord(
						resultSet.getObject("id", UUID.class),
						resultSet.getObject("plan_id", UUID.class),
						resultSet.getString("plan_name"),
						resultSet.getString("status"),
						resultSet.getObject("starts_on", LocalDate.class),
						resultSet.getObject("ends_on", LocalDate.class),
						resultSet.getObject("grace_ends_on", LocalDate.class)
				))
				.list();
	}

	public List<PlanOptionRecord> planOptions(UUID organizationId) {
		return jdbcClient.sql("""
				SELECT mp.id, mp.product_id, cp.name, cp.price_cents, cp.currency,
				       mp.duration_days, mp.grace_days
				FROM membership_plan mp
				JOIN catalog_product cp ON cp.id = mp.product_id
				WHERE mp.organization_id = :organizationId
				  AND mp.status = 'ACTIVE'
				  AND mp.deleted_at IS NULL
				  AND cp.status = 'ACTIVE'
				  AND cp.deleted_at IS NULL
				ORDER BY cp.price_cents, cp.name
				""")
				.param("organizationId", organizationId)
				.query((resultSet, rowNumber) -> new PlanOptionRecord(
						resultSet.getObject("id", UUID.class),
						resultSet.getObject("product_id", UUID.class),
						resultSet.getString("name"),
						resultSet.getLong("price_cents"),
						resultSet.getString("currency"),
						resultSet.getInt("duration_days"),
						resultSet.getInt("grace_days")
				))
				.list();
	}

	public List<PendingPaymentRecord> pendingPayments(UUID organizationId, UUID branchId, UUID clientId) {
		return jdbcClient.sql("""
				SELECT p.id, p.sale_id, s.sale_number, p.method, p.status,
				       p.amount_cents, p.currency, p.received_at
				FROM payment p
				JOIN sale s ON s.id = p.sale_id
				WHERE p.organization_id = :organizationId
				  AND p.branch_id = :branchId
				  AND p.client_id = :clientId
				  AND p.status = 'PENDING_VALIDATION'
				  AND p.deleted_at IS NULL
				ORDER BY p.received_at DESC
				""")
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("clientId", clientId)
				.query((resultSet, rowNumber) -> new PendingPaymentRecord(
						resultSet.getObject("id", UUID.class),
						resultSet.getObject("sale_id", UUID.class),
						resultSet.getString("sale_number"),
						resultSet.getString("method"),
						resultSet.getString("status"),
						resultSet.getLong("amount_cents"),
						resultSet.getString("currency"),
						resultSet.getObject("received_at", OffsetDateTime.class)
				))
				.list();
	}

	public record TerminalRecord(
			UUID id,
			UUID organizationId,
			UUID branchId,
			String name,
			String deviceCode,
			String status,
			String allowedActionsJson,
			String tradeName,
			String brandName,
			String logoUrl,
			String primaryColor,
			String accentColor,
			String surfaceColor
	) {
	}

	public record SessionRecord(
			UUID id,
			UUID organizationId,
			UUID branchId,
			UUID terminalId,
			UUID clientId,
			String status,
			OffsetDateTime startedAt,
			OffsetDateTime expiresAt,
			OffsetDateTime endedAt,
			String verificationMethod,
			String sessionTokenHash,
			int verificationAttempts,
			OffsetDateTime blockedUntil,
			String allowedActionsJson
	) {
	}

	public record MembershipRecord(
			UUID id,
			UUID planId,
			String planName,
			String status,
			LocalDate startsOn,
			LocalDate endsOn,
			LocalDate graceEndsOn
	) {
	}

	public record PlanOptionRecord(
			UUID id,
			UUID productId,
			String name,
			long priceCents,
			String currency,
			int durationDays,
			int graceDays
	) {
	}

	public record PendingPaymentRecord(
			UUID id,
			UUID saleId,
			String saleNumber,
			String method,
			String status,
			long amountCents,
			String currency,
			OffsetDateTime receivedAt
	) {
	}
}
