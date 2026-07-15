package com.gymos.memberships.infrastructure;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class MembershipRepository {
	private final JdbcClient jdbcClient;

	public MembershipRepository(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	public boolean clientExists(UUID organizationId, UUID clientId) {
		Integer count = jdbcClient.sql("""
				SELECT COUNT(*)
				FROM client
				WHERE organization_id = :organizationId
				  AND id = :clientId
				  AND deleted_at IS NULL
				""")
				.param("organizationId", organizationId)
				.param("clientId", clientId)
				.query(Integer.class)
				.single();
		return count > 0;
	}

	public List<MembershipSummary> forClient(UUID organizationId, UUID branchId, UUID clientId) {
		return jdbcClient.sql("""
				SELECT m.id, m.client_id, m.plan_id, cp.name AS plan_name,
				       m.sale_id, s.sale_number, m.status, m.activated_at,
				       m.starts_on, m.ends_on, m.grace_ends_on
				FROM membership m
				JOIN membership_plan mp ON mp.id = m.plan_id
				JOIN catalog_product cp ON cp.id = mp.product_id
				JOIN sale s ON s.id = m.sale_id
				WHERE m.organization_id = :organizationId
				  AND m.branch_id = :branchId
				  AND m.client_id = :clientId
				  AND m.deleted_at IS NULL
				ORDER BY m.starts_on DESC, m.activated_at DESC
				""")
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("clientId", clientId)
				.query((resultSet, rowNumber) -> new MembershipSummary(
						resultSet.getObject("id", UUID.class),
						resultSet.getObject("client_id", UUID.class),
						resultSet.getObject("plan_id", UUID.class),
						resultSet.getString("plan_name"),
						resultSet.getObject("sale_id", UUID.class),
						resultSet.getString("sale_number"),
						resultSet.getString("status"),
						resultSet.getObject("activated_at", OffsetDateTime.class),
						resultSet.getObject("starts_on", LocalDate.class),
						resultSet.getObject("ends_on", LocalDate.class),
						resultSet.getObject("grace_ends_on", LocalDate.class)
				))
				.list();
	}

	public Optional<MembershipSummary> byId(UUID organizationId, UUID branchId, UUID membershipId) {
		return jdbcClient.sql("""
				SELECT m.id, m.client_id, m.plan_id, cp.name AS plan_name,
				       m.sale_id, s.sale_number, m.status, m.activated_at,
				       m.starts_on, m.ends_on, m.grace_ends_on
				FROM membership m
				JOIN membership_plan mp ON mp.id = m.plan_id
				JOIN catalog_product cp ON cp.id = mp.product_id
				JOIN sale s ON s.id = m.sale_id
				WHERE m.organization_id = :organizationId
				  AND m.branch_id = :branchId
				  AND m.id = :membershipId
				  AND m.deleted_at IS NULL
				""")
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("membershipId", membershipId)
				.query((resultSet, rowNumber) -> new MembershipSummary(
						resultSet.getObject("id", UUID.class),
						resultSet.getObject("client_id", UUID.class),
						resultSet.getObject("plan_id", UUID.class),
						resultSet.getString("plan_name"),
						resultSet.getObject("sale_id", UUID.class),
						resultSet.getString("sale_number"),
						resultSet.getString("status"),
						resultSet.getObject("activated_at", OffsetDateTime.class),
						resultSet.getObject("starts_on", LocalDate.class),
						resultSet.getObject("ends_on", LocalDate.class),
						resultSet.getObject("grace_ends_on", LocalDate.class)
				))
				.optional();
	}

	public Optional<MembershipSummary> bySaleAndPlan(UUID organizationId, UUID branchId, UUID saleId, UUID planId) {
		return jdbcClient.sql("""
				SELECT m.id, m.client_id, m.plan_id, cp.name AS plan_name,
				       m.sale_id, s.sale_number, m.status, m.activated_at,
				       m.starts_on, m.ends_on, m.grace_ends_on
				FROM membership m
				JOIN membership_plan mp ON mp.id = m.plan_id
				JOIN catalog_product cp ON cp.id = mp.product_id
				JOIN sale s ON s.id = m.sale_id
				WHERE m.organization_id = :organizationId
				  AND m.branch_id = :branchId
				  AND m.sale_id = :saleId
				  AND m.plan_id = :planId
				  AND m.deleted_at IS NULL
				""")
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("saleId", saleId)
				.param("planId", planId)
				.query((resultSet, rowNumber) -> new MembershipSummary(
						resultSet.getObject("id", UUID.class),
						resultSet.getObject("client_id", UUID.class),
						resultSet.getObject("plan_id", UUID.class),
						resultSet.getString("plan_name"),
						resultSet.getObject("sale_id", UUID.class),
						resultSet.getString("sale_number"),
						resultSet.getString("status"),
						resultSet.getObject("activated_at", OffsetDateTime.class),
						resultSet.getObject("starts_on", LocalDate.class),
						resultSet.getObject("ends_on", LocalDate.class),
						resultSet.getObject("grace_ends_on", LocalDate.class)
				))
				.optional();
	}

	public Optional<PlanForRenewal> planForRenewal(UUID organizationId, UUID planId) {
		return jdbcClient.sql("""
				SELECT mp.id AS plan_id, mp.product_id, cp.name AS plan_name,
				       mp.duration_days, mp.grace_days, cp.price_cents, cp.currency
				FROM membership_plan mp
				JOIN catalog_product cp ON cp.id = mp.product_id
				WHERE mp.organization_id = :organizationId
				  AND mp.id = :planId
				  AND mp.status = 'ACTIVE'
				  AND mp.deleted_at IS NULL
				  AND cp.status = 'ACTIVE'
				  AND cp.deleted_at IS NULL
				""")
				.param("organizationId", organizationId)
				.param("planId", planId)
				.query((resultSet, rowNumber) -> new PlanForRenewal(
						resultSet.getObject("plan_id", UUID.class),
						resultSet.getObject("product_id", UUID.class),
						resultSet.getString("plan_name"),
						resultSet.getInt("duration_days"),
						resultSet.getInt("grace_days"),
						resultSet.getLong("price_cents"),
						resultSet.getString("currency")
				))
				.optional();
	}

	public Optional<PlanForRenewal> planForProduct(UUID organizationId, UUID productId) {
		return jdbcClient.sql("""
				SELECT mp.id AS plan_id, mp.product_id, cp.name AS plan_name,
				       mp.duration_days, mp.grace_days, cp.price_cents, cp.currency
				FROM membership_plan mp
				JOIN catalog_product cp ON cp.id = mp.product_id
				WHERE mp.organization_id = :organizationId
				  AND mp.product_id = :productId
				  AND mp.status = 'ACTIVE'
				  AND mp.deleted_at IS NULL
				  AND cp.status = 'ACTIVE'
				  AND cp.deleted_at IS NULL
				""")
				.param("organizationId", organizationId)
				.param("productId", productId)
				.query((resultSet, rowNumber) -> new PlanForRenewal(
						resultSet.getObject("plan_id", UUID.class),
						resultSet.getObject("product_id", UUID.class),
						resultSet.getString("plan_name"),
						resultSet.getInt("duration_days"),
						resultSet.getInt("grace_days"),
						resultSet.getLong("price_cents"),
						resultSet.getString("currency")
				))
				.optional();
	}

	public boolean markEventProcessed(UUID organizationId, String eventKey, String eventType) {
		try {
			jdbcClient.sql("""
					INSERT INTO processed_domain_event (id, organization_id, event_key, event_type, processed_at, created_at)
					VALUES (:id, :organizationId, :eventKey, :eventType, :processedAt, :createdAt)
					""")
					.param("id", UUID.randomUUID())
					.param("organizationId", organizationId)
					.param("eventKey", eventKey)
					.param("eventType", eventType)
					.param("processedAt", OffsetDateTime.now())
					.param("createdAt", OffsetDateTime.now())
					.update();
			return true;
		} catch (org.springframework.dao.DuplicateKeyException exception) {
			return false;
		}
	}

	public Optional<LocalDate> latestMembershipEnd(UUID organizationId, UUID clientId, UUID planId) {
		return jdbcClient.sql("""
				SELECT MAX(ends_on)
				FROM membership
				WHERE organization_id = :organizationId
				  AND client_id = :clientId
				  AND plan_id = :planId
				  AND status = 'ACTIVE'
				  AND deleted_at IS NULL
				""")
				.param("organizationId", organizationId)
				.param("clientId", clientId)
				.param("planId", planId)
				.query(LocalDate.class)
				.optional();
	}

	public record MembershipSummary(
			UUID id,
			UUID clientId,
			UUID planId,
			String planName,
			UUID saleId,
			String saleNumber,
			String status,
			OffsetDateTime activatedAt,
			LocalDate startsOn,
			LocalDate endsOn,
			LocalDate graceEndsOn
	) {
	}

	public record PlanForRenewal(
			UUID planId,
			UUID productId,
			String planName,
			int durationDays,
			int graceDays,
			long priceCents,
			String currency
	) {
	}
}
