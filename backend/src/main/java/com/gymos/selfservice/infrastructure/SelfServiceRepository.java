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

	public void ensureDefaultTerminal(UUID organizationId, UUID branchId, UUID userId) {
		OffsetDateTime now = OffsetDateTime.now();
		jdbcClient.sql("""
				INSERT INTO self_service_terminal (
				    id, organization_id, branch_id, name, device_code, status,
				    allowed_actions_json, created_at, updated_at, created_by, updated_by, version
				)
				SELECT gen_random_uuid(), :organizationId, :branchId, 'Kiosco de sucursal',
				       'KIOSK-MAIN', 'ACTIVE',
			       '{"membershipEnrollment":true,"membershipRenewal":true,"membershipPlanChange":true,"resourceBooking":true,"wellnessBooking":false,"profileUpdate":true,"autopay":true}'::jsonb,
				       :now, :now, :userId, :userId, 0
				WHERE EXISTS (
				    SELECT 1 FROM branch
				    WHERE id = :branchId
				      AND organization_id = :organizationId
				      AND status = 'ACTIVE'
				      AND deleted_at IS NULL
				)
				AND NOT EXISTS (
				    SELECT 1 FROM self_service_terminal
				    WHERE organization_id = :organizationId
				      AND branch_id = :branchId
				      AND deleted_at IS NULL
				)
				ON CONFLICT (organization_id, branch_id, device_code)
				    WHERE deleted_at IS NULL
				DO NOTHING
				""")
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("userId", userId)
				.param("now", now)
				.update();
	}

	public Optional<TerminalRecord> defaultTerminal(UUID organizationId, UUID branchId) {
		return jdbcClient.sql("""
				SELECT sst.id, sst.organization_id, sst.branch_id, sst.name, sst.device_code, sst.status,
				       sst.allowed_actions_json::text AS allowed_actions_json,
				       o.trade_name, o.brand_name, o.logo_url, o.primary_color, o.accent_color, o.surface_color
				FROM self_service_terminal sst
				JOIN organization o ON o.id = sst.organization_id
				JOIN branch b ON b.id = sst.branch_id
				WHERE sst.organization_id = :organizationId
				  AND sst.branch_id = :branchId
				  AND sst.status = 'ACTIVE'
				  AND sst.deleted_at IS NULL
				  AND b.status = 'ACTIVE'
				  AND b.deleted_at IS NULL
				ORDER BY CASE WHEN sst.device_code = 'KIOSK-MAIN' THEN 0 ELSE 1 END, sst.created_at
				LIMIT 1
				""")
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.query(this::mapTerminal)
				.optional();
	}

	public List<BranchKioskConfigurationRecord> branchConfigurations(UUID organizationId) {
		return jdbcClient.sql("""
				SELECT branch.id AS branch_id, branch.name AS branch_name,
				       COALESCE(terminal.status, 'NOT_CONFIGURED') AS status,
				       COALESCE(terminal.allowed_actions_json,
				           '{"membershipEnrollment":true,"membershipRenewal":true,"membershipPlanChange":true,"resourceBooking":true,"wellnessBooking":false,"profileUpdate":true,"autopay":true}'::jsonb
				       )::text AS allowed_actions_json,
				       terminal.updated_at
				FROM branch
				LEFT JOIN LATERAL (
				    SELECT status, allowed_actions_json, updated_at
				    FROM self_service_terminal terminal
				    WHERE terminal.organization_id = branch.organization_id
				      AND terminal.branch_id = branch.id
				      AND terminal.deleted_at IS NULL
				    ORDER BY CASE WHEN terminal.device_code = 'KIOSK-MAIN' THEN 0 ELSE 1 END, terminal.created_at
				    LIMIT 1
				) terminal ON TRUE
				WHERE branch.organization_id = :organizationId
				  AND branch.status = 'ACTIVE'
				  AND branch.deleted_at IS NULL
				ORDER BY branch.name, branch.id
				""")
				.param("organizationId", organizationId)
				.query((rs, row) -> new BranchKioskConfigurationRecord(
						rs.getObject("branch_id", UUID.class), rs.getString("branch_name"),
						rs.getString("status"), rs.getString("allowed_actions_json"),
						rs.getObject("updated_at", OffsetDateTime.class)
				))
				.list();
	}

	public boolean configureBranch(
			UUID organizationId, UUID branchId, UUID actorUserId, BranchKioskConfigurationData data
	) {
		OffsetDateTime now = OffsetDateTime.now();
		return jdbcClient.sql("""
				INSERT INTO self_service_terminal (
				    id, organization_id, branch_id, name, device_code, status,
				    allowed_actions_json, created_at, updated_at, created_by, updated_by, version
				)
				SELECT gen_random_uuid(), :organizationId, :branchId, 'Kiosco de sucursal',
				       'KIOSK-MAIN', 'ACTIVE',
				       jsonb_build_object(
				           'membershipEnrollment', :membershipEnrollment,
				           'membershipRenewal', :membershipRenewal,
				           'membershipPlanChange', :membershipPlanChange,
				           'resourceBooking', :resourceBooking,
				           'wellnessBooking', false,
				           'profileUpdate', :profileUpdate,
				           'autopay', :autopay
				       ), :now, :now, :actorUserId, :actorUserId, 0
				FROM branch
				WHERE id = :branchId AND organization_id = :organizationId
				  AND status = 'ACTIVE' AND deleted_at IS NULL
				ON CONFLICT (organization_id, branch_id, device_code)
				    WHERE deleted_at IS NULL
				DO UPDATE SET
				    status = 'ACTIVE',
				    allowed_actions_json = EXCLUDED.allowed_actions_json,
				    updated_at = EXCLUDED.updated_at,
				    updated_by = EXCLUDED.updated_by,
				    version = self_service_terminal.version + 1
				""")
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("actorUserId", actorUserId)
				.param("membershipEnrollment", data.membershipEnrollment())
				.param("membershipRenewal", data.membershipRenewal())
				.param("membershipPlanChange", data.membershipPlanChange())
				.param("resourceBooking", data.resourceBooking())
				.param("profileUpdate", data.profileUpdate())
				.param("autopay", data.autopay())
				.param("now", now)
				.update() == 1;
	}

	public Optional<TerminalRecord> terminal(String terminalCode, UUID organizationId, UUID branchId) {
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
				  AND b.status = 'ACTIVE'
				  AND b.deleted_at IS NULL
				""")
				.param("terminalCode", terminalCode)
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.query(this::mapTerminal)
				.optional();
	}

	public Optional<SessionRecord> session(UUID sessionId) {
		return jdbcClient.sql("""
				SELECT s.id, s.organization_id, s.branch_id, s.terminal_id, s.client_id,
				       s.status, s.started_at, s.expires_at, s.ended_at, s.verification_method,
				       s.session_token_hash, s.verification_attempts, s.blocked_until, s.last_request_at,
				       s.launched_by_user_id,
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
						resultSet.getObject("last_request_at", OffsetDateTime.class),
						resultSet.getObject("launched_by_user_id", UUID.class),
						resultSet.getString("allowed_actions_json")
				))
				.optional();
	}

	public void createSession(
			UUID sessionId,
			UUID organizationId,
			UUID branchId,
			UUID terminalId,
			UUID launchedByUserId,
			OffsetDateTime startedAt,
			OffsetDateTime expiresAt,
			String originIp,
			String sessionTokenHash
	) {
		jdbcClient.sql("""
				INSERT INTO self_service_session (
				    id, organization_id, branch_id, terminal_id, status, started_at,
				    expires_at, origin_ip, session_token_hash, last_request_at, launched_by_user_id,
				    created_at, updated_at, version
				)
				VALUES (
				    :id, :organizationId, :branchId, :terminalId, 'ACTIVE', :startedAt,
				    :expiresAt, :originIp, :sessionTokenHash, :startedAt, :launchedByUserId,
				    :createdAt, :updatedAt, 0
				)
				""")
				.param("id", sessionId)
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("terminalId", terminalId)
				.param("launchedByUserId", launchedByUserId)
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
				    last_request_at = :updatedAt,
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

	public int recordFailedVerification(UUID sessionId, int maxAttempts, OffsetDateTime blockedUntil, OffsetDateTime now) {
		return jdbcClient.sql("""
				UPDATE self_service_session
				SET verification_attempts = verification_attempts + 1,
				    blocked_until = CASE
				        WHEN verification_attempts + 1 >= :maxAttempts THEN :blockedUntil
				        ELSE blocked_until
				    END,
				    updated_at = :updatedAt,
				    version = version + 1
				WHERE id = :sessionId
				RETURNING verification_attempts
				""")
				.param("maxAttempts", maxAttempts)
				.param("blockedUntil", blockedUntil)
				.param("updatedAt", now)
				.param("sessionId", sessionId)
				.query(Integer.class)
				.single();
	}

	public void touchSession(UUID sessionId, OffsetDateTime now) {
		jdbcClient.sql("""
				UPDATE self_service_session
				SET last_request_at = :now,
				    updated_at = :now,
				    version = version + 1
				WHERE id = :sessionId
				  AND status = 'ACTIVE'
				  AND expires_at >= :now
				""")
				.param("sessionId", sessionId)
				.param("now", now)
				.update();
	}

	public void expireSession(UUID sessionId, OffsetDateTime now) {
		jdbcClient.sql("""
				UPDATE self_service_session
				SET status = 'EXPIRED', ended_at = :now, updated_at = :now, version = version + 1
				WHERE id = :sessionId AND status = 'ACTIVE'
				""")
				.param("sessionId", sessionId)
				.param("now", now)
				.update();
	}

	public void resetSession(UUID sessionId, OffsetDateTime now, OffsetDateTime expiresAt) {
		jdbcClient.sql("""
				UPDATE self_service_session
				SET client_id = NULL,
				    status = 'ACTIVE',
				    verification_method = NULL,
				    verification_attempts = 0,
				    blocked_until = NULL,
				    expires_at = :expiresAt,
				    ended_at = NULL,
				    last_request_at = :now,
				    updated_at = :now,
				    version = version + 1
				WHERE id = :sessionId
				  AND deleted_at IS NULL
				""")
				.param("sessionId", sessionId)
				.param("now", now)
				.param("expiresAt", expiresAt)
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

	public Optional<AutopayMembershipRecord> autopayMembership(
			UUID organizationId,
			UUID branchId,
			UUID clientId,
			UUID membershipId
	) {
		return jdbcClient.sql("""
				SELECT membership.id, membership.plan_id, product.name AS plan_name,
				       membership.status, membership.ends_on, product.price_cents, product.currency
				FROM membership
				JOIN membership_plan plan ON plan.id = membership.plan_id
				JOIN catalog_product product ON product.id = plan.product_id
				WHERE membership.organization_id = :organizationId
				  AND membership.branch_id = :branchId
				  AND membership.client_id = :clientId
				  AND membership.id = :membershipId
				  AND membership.status = 'ACTIVE'
				  AND membership.deleted_at IS NULL
				  AND plan.status = 'ACTIVE'
				  AND plan.deleted_at IS NULL
				  AND product.status = 'ACTIVE'
				  AND product.deleted_at IS NULL
				""")
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("clientId", clientId)
				.param("membershipId", membershipId)
				.query((rs, row) -> new AutopayMembershipRecord(
						rs.getObject("id", UUID.class), rs.getObject("plan_id", UUID.class),
						rs.getString("plan_name"), rs.getString("status"),
						rs.getObject("ends_on", LocalDate.class), rs.getLong("price_cents"),
						rs.getString("currency")
				))
				.optional();
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

	public List<SelfServicePaymentRecord> paymentActivity(UUID organizationId, UUID branchId, UUID clientId) {
		return jdbcClient.sql("""
				SELECT payment.id, payment.sale_id, sale.sale_number, payment.method,
				       CASE
				           WHEN payment.status IN ('COMPLETED', 'REJECTED') THEN payment.status
				           WHEN operation.status IS NOT NULL THEN operation.status
				           ELSE payment.status
				       END AS tracking_status,
				       payment.amount_cents, payment.currency, payment.received_at,
				       operation.provider, operation.receipt_url, operation.checkout_url,
				       operation.updated_at AS provider_updated_at
				FROM payment
				JOIN sale ON sale.id = payment.sale_id
				LEFT JOIN LATERAL (
				    SELECT provider, status, receipt_url, checkout_url, updated_at
				    FROM payment_provider_operation operation
				    WHERE operation.organization_id = payment.organization_id
				      AND operation.payment_id = payment.id
				    ORDER BY operation.created_at DESC, operation.id DESC
				    LIMIT 1
				) operation ON TRUE
				WHERE payment.organization_id = :organizationId
				  AND payment.branch_id = :branchId
				  AND payment.client_id = :clientId
				  AND payment.method = 'CARD_ONLINE'
				  AND payment.deleted_at IS NULL
				ORDER BY payment.received_at DESC, payment.id DESC
				LIMIT 20
				""")
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("clientId", clientId)
				.query((rs, row) -> new SelfServicePaymentRecord(
						rs.getObject("id", UUID.class), rs.getObject("sale_id", UUID.class),
						rs.getString("sale_number"), rs.getString("method"), rs.getString("tracking_status"),
						rs.getLong("amount_cents"), rs.getString("currency"),
						rs.getObject("received_at", OffsetDateTime.class), rs.getString("provider"),
						rs.getString("receipt_url"), rs.getString("checkout_url"),
						rs.getObject("provider_updated_at", OffsetDateTime.class)
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
			OffsetDateTime lastRequestAt,
			UUID launchedByUserId,
			String allowedActionsJson
	) {
	}

	private TerminalRecord mapTerminal(java.sql.ResultSet resultSet, int rowNumber) throws java.sql.SQLException {
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

	public record AutopayMembershipRecord(
			UUID id, UUID planId, String planName, String status, LocalDate endsOn,
			long currentPriceCents, String currency
	) {}

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

	public record BranchKioskConfigurationRecord(
			UUID branchId, String branchName, String status, String allowedActionsJson,
			OffsetDateTime updatedAt
	) {}

	public record BranchKioskConfigurationData(
			boolean membershipEnrollment, boolean membershipRenewal,
			boolean membershipPlanChange, boolean resourceBooking,
			boolean profileUpdate, boolean autopay
	) {}

	public record SelfServicePaymentRecord(
			UUID id, UUID saleId, String saleNumber, String method, String status,
			long amountCents, String currency, OffsetDateTime receivedAt, String provider,
			String receiptUrl, String checkoutUrl, OffsetDateTime providerUpdatedAt
	) {}
}
