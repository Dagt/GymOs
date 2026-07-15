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

	public Optional<TerminalRecord> terminal(String terminalCode) {
		return jdbcClient.sql("""
				SELECT id, organization_id, branch_id, name, device_code, status
				FROM self_service_terminal
				WHERE device_code = :terminalCode
				  AND status = 'ACTIVE'
				  AND deleted_at IS NULL
				""")
				.param("terminalCode", terminalCode)
				.query((resultSet, rowNumber) -> new TerminalRecord(
						resultSet.getObject("id", UUID.class),
						resultSet.getObject("organization_id", UUID.class),
						resultSet.getObject("branch_id", UUID.class),
						resultSet.getString("name"),
						resultSet.getString("device_code"),
						resultSet.getString("status")
				))
				.optional();
	}

	public Optional<SessionRecord> session(UUID sessionId) {
		return jdbcClient.sql("""
				SELECT id, organization_id, branch_id, terminal_id, client_id,
				       status, started_at, expires_at, ended_at, verification_method
				FROM self_service_session
				WHERE id = :sessionId
				  AND deleted_at IS NULL
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
						resultSet.getString("verification_method")
				))
				.optional();
	}

	public Optional<ClientRecord> clientByIdentifier(UUID organizationId, String identifier) {
		String like = identifier == null ? "" : identifier.trim().toLowerCase();
		return jdbcClient.sql("""
				SELECT c.id, p.first_name || ' ' || p.last_name AS full_name,
				       p.phone, p.email, c.status
				FROM client c
				JOIN person p ON p.id = c.person_id
				WHERE c.organization_id = :organizationId
				  AND c.status = 'ACTIVE'
				  AND c.deleted_at IS NULL
				  AND (
				      LOWER(COALESCE(p.email, '')) = :identifier
				      OR LOWER(COALESCE(p.phone, '')) = :identifier
				  )
				ORDER BY c.joined_at DESC
				LIMIT 1
				""")
				.param("organizationId", organizationId)
				.param("identifier", like)
				.query((resultSet, rowNumber) -> new ClientRecord(
						resultSet.getObject("id", UUID.class),
						resultSet.getString("full_name"),
						resultSet.getString("phone"),
						resultSet.getString("email"),
						resultSet.getString("status")
				))
				.optional();
	}

	public List<MembershipRecord> memberships(UUID organizationId, UUID branchId, UUID clientId) {
		return jdbcClient.sql("""
				SELECT m.id, m.plan_id, cp.name AS plan_name, m.status,
				       m.starts_on, m.ends_on, m.grace_ends_on
				FROM membership m
				JOIN membership_plan mp ON mp.id = m.plan_id
				JOIN catalog_product cp ON cp.id = mp.product_id
				WHERE m.organization_id = :organizationId
				  AND m.branch_id = :branchId
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

	public record TerminalRecord(UUID id, UUID organizationId, UUID branchId, String name, String deviceCode, String status) {
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
			String verificationMethod
	) {
	}

	public record ClientRecord(UUID id, String fullName, String phone, String email, String status) {
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
