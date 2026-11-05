
package com.gymos.access.infrastructure;

import com.gymos.common.security.GymOsPrincipal;
import com.gymos.visitpackages.api.operations.VisitPackageAccessOperations.VisitRestoration;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class ManualCheckInRepository {
	// Use the same CRM visibility as Access, extended to a currently covered visit package.
	private static final String CLIENTS = """
			SELECT c.id, concat_ws(' ', p.first_name, p.last_name, p.maternal_last_name) AS name,
			       p.phone, c.status
			FROM client c
			JOIN person p ON p.id=c.person_id AND p.organization_id=c.organization_id
			WHERE c.organization_id=:organizationId AND c.deleted_at IS NULL AND p.deleted_at IS NULL
			  AND (c.home_branch_id IS NULL OR c.home_branch_id=:branchId
			       OR EXISTS (
			           SELECT 1 FROM membership m JOIN membership_plan mp
			             ON mp.id=m.plan_id AND mp.organization_id=m.organization_id
			           WHERE m.organization_id=c.organization_id AND m.client_id=c.id
			             AND m.status='ACTIVE' AND m.deleted_at IS NULL AND mp.deleted_at IS NULL
			             AND m.starts_on<=:businessDate AND m.grace_ends_on>=:businessDate
			             AND (m.branch_id=:branchId OR mp.multi_branch_access)
			       )
			       OR EXISTS (
			           SELECT 1 FROM visit_package_entitlement e
			           JOIN visit_package_entitlement_benefit_branch eb
			             ON eb.organization_id=e.organization_id AND eb.entitlement_id=e.id
			           WHERE e.organization_id=c.organization_id AND e.client_id=c.id
			             AND e.status IN ('ACTIVE', 'CONSUMED') AND e.deleted_at IS NULL
			             AND e.valid_from<=:businessDate AND e.valid_until>=:businessDate
			             AND eb.branch_id=:branchId AND eb.usage_mode='CONSUMES_VISIT'
			       ))
			""";

	private static final String HISTORY = """
			SELECT l.id, l.organization_id, l.branch_id, l.client_id,
			       concat_ws(' ', p.first_name, p.last_name, p.maternal_last_name) AS client_name,
			       l.created_by, l.occurred_at, l.manual_business_date, l.source, l.decision, l.reason,
			       l.membership_id, c.entitlement_id, c.remaining_after,
			       l.manual_benefit_id, b.name AS benefit_name, l.manual_duplicate_of,
			       l.manual_override_of, l.manual_override_reason,
			       r.occurred_at AS reversed_at, r.actor_user_id AS reversed_by, r.reason AS reversal_reason,
			       r.remaining_before AS reversal_before, r.remaining_after AS reversal_after
			FROM access_log l
			JOIN client cl ON cl.id=l.client_id AND cl.organization_id=l.organization_id
			JOIN person p ON p.id=cl.person_id AND p.organization_id=l.organization_id
			LEFT JOIN benefit b ON b.id=l.manual_benefit_id AND b.organization_id=l.organization_id
			LEFT JOIN visit_package_consumption c
			  ON c.access_log_id=l.id AND c.organization_id=l.organization_id AND c.branch_id=l.branch_id
			LEFT JOIN access_manual_reversal r ON r.access_log_id=l.id
			WHERE l.organization_id=:organizationId AND l.branch_id=:branchId AND l.source='MANUAL'
			""";

	private final JdbcClient jdbc;

	public ManualCheckInRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	public Optional<BranchContext> branch(GymOsPrincipal actor) {
		return scope("""
				SELECT name, timezone FROM branch
				WHERE organization_id=:organizationId AND id=:branchId AND deleted_at IS NULL AND status='ACTIVE'
				""", actor)
				.query((rs, row) -> new BranchContext(rs.getString("name"), ZoneId.of(rs.getString("timezone"))))
				.optional();
	}

	public List<Client> search(GymOsPrincipal actor, LocalDate date, String query) {
		return scope(CLIENTS + """
				AND (strpos(lower(concat_ws(' ', p.first_name, p.last_name, p.maternal_last_name)), lower(:query)) > 0
				     OR strpos(coalesce(p.phone, ''), :query) > 0
				     OR c.id::text = lower(:query))
				ORDER BY name, c.id LIMIT 20
				""", actor).param("businessDate", date).param("query", query)
				.query(this::clientRow).list();
	}

	public Optional<Client> client(GymOsPrincipal actor, LocalDate date, UUID clientId) {
		return scope(CLIENTS + " AND c.id=:clientId", actor)
				.param("businessDate", date).param("clientId", clientId).query(this::clientRow).optional();
	}

	public void lockRequest(GymOsPrincipal actor, UUID requestId) {
		jdbc.sql("SELECT pg_advisory_xact_lock(hashtextextended(:key, 0))")
				.param("key", actor.organizationId() + ":manual-check-in:" + requestId)
				.query((rs, row) -> Boolean.TRUE).single();
	}

	public void lockClient(GymOsPrincipal actor, UUID clientId) {
		jdbc.sql("SELECT id FROM client WHERE organization_id=:organizationId AND id=:clientId FOR UPDATE")
				.param("organizationId", actor.organizationId()).param("clientId", clientId)
				.query(UUID.class).optional();
	}

	public Optional<Request> request(GymOsPrincipal actor, UUID requestId) {
		return jdbc.sql("""
				SELECT id, branch_id, client_id, manual_benefit_id, manual_override_of, manual_override_reason
				FROM access_log
				WHERE organization_id=:organizationId AND manual_request_id=:requestId AND source='MANUAL'
				""").param("organizationId", actor.organizationId()).param("requestId", requestId)
				.query((rs, row) -> new Request(uuid(rs, "id"), uuid(rs, "branch_id"), uuid(rs, "client_id"),
						uuid(rs, "manual_benefit_id"), uuid(rs, "manual_override_of"),
						rs.getString("manual_override_reason"))).optional();
	}

	public Optional<UUID> recentGrant(GymOsPrincipal actor, UUID clientId, OffsetDateTime since) {
		return scope("""
				SELECT l.id FROM access_log l
				WHERE l.organization_id=:organizationId AND l.branch_id=:branchId
				  AND l.client_id=:clientId AND l.source='MANUAL' AND l.decision='GRANTED'
				  AND l.occurred_at>=:since
				  AND NOT EXISTS (SELECT 1 FROM access_manual_reversal r WHERE r.access_log_id=l.id)
				ORDER BY l.occurred_at DESC, l.id DESC LIMIT 1
				""", actor).param("clientId", clientId).param("since", since).query(UUID.class).optional();
	}

	public UUID insert(GymOsPrincipal actor, UUID requestId, UUID clientId, UUID benefitId, UUID membershipId,
			OffsetDateTime now, LocalDate date, String decision, String reason, UUID duplicateOf,
			UUID overrideOf, String overrideReason, UUID correlationId) {
		UUID id = UUID.randomUUID();
		scope("""
				INSERT INTO access_log (
				    id, organization_id, branch_id, client_id, membership_id, access_type, decision,
				    source, reason, occurred_at, created_at, created_by, correlation_id, manual_request_id,
				    manual_business_date, manual_benefit_id, manual_duplicate_of, manual_override_of, manual_override_reason
				) VALUES (
				    :id, :organizationId, :branchId, :clientId, :membershipId, 'MANUAL_CHECK_IN', :decision,
				    'MANUAL', :reason, :now, :now, :actorId, :correlationId, :requestId,
				    :businessDate, :benefitId, :duplicateOf, :overrideOf, :overrideReason
				)
				""", actor).param("id", id).param("requestId", requestId).param("businessDate", date)
				.param("clientId", clientId).param("membershipId", membershipId).param("now", now)
				.param("decision", decision).param("reason", reason).param("actorId", actor.userId())
				.param("correlationId", correlationId).param("benefitId", benefitId)
				.param("duplicateOf", duplicateOf).param("overrideOf", overrideOf)
				.param("overrideReason", overrideReason).update();
		return id;
	}

	public Optional<Entry> entry(GymOsPrincipal actor, UUID id) {
		return scope(HISTORY + " AND l.id=:id", actor).param("id", id).query(this::entryRow).optional();
	}

	public List<Entry> history(GymOsPrincipal actor) {
		return scope(HISTORY + " ORDER BY l.occurred_at DESC, l.id DESC LIMIT 30", actor)
				.query(this::entryRow).list();
	}

	public void reverse(GymOsPrincipal actor, UUID id, OffsetDateTime now, String reason, VisitRestoration restoration) {
		scope("""
				INSERT INTO access_manual_reversal (
				    access_log_id, organization_id, branch_id, actor_user_id, occurred_at, reason,
				    remaining_before, remaining_after
				) VALUES (:id, :organizationId, :branchId, :actorId, :now, :reason, :before, :after)
				""", actor).param("id", id).param("actorId", actor.userId()).param("now", now)
				.param("reason", reason).param("before", restoration == null ? null : restoration.remainingBefore())
				.param("after", restoration == null ? null : restoration.remainingAfter()).update();
	}

	private JdbcClient.StatementSpec scope(String sql, GymOsPrincipal actor) {
		return jdbc.sql(sql).param("organizationId", actor.organizationId()).param("branchId", actor.branchId());
	}

	private Client clientRow(ResultSet rs, int row) throws SQLException {
		return new Client(uuid(rs, "id"), rs.getString("name"), rs.getString("phone"), rs.getString("status"));
	}

	private Entry entryRow(ResultSet rs, int row) throws SQLException {
		Integer after = rs.getObject("remaining_after", Integer.class);
		OffsetDateTime reversedAt = rs.getObject("reversed_at", OffsetDateTime.class);
		return new Entry(uuid(rs, "id"), uuid(rs, "organization_id"), uuid(rs, "branch_id"), uuid(rs, "client_id"),
				rs.getString("client_name"), uuid(rs, "created_by"), rs.getObject("occurred_at", OffsetDateTime.class),
				rs.getObject("manual_business_date", LocalDate.class), rs.getString("source"), rs.getString("decision"),
				rs.getString("reason"), uuid(rs, "membership_id"), uuid(rs, "entitlement_id"),
				after == null ? null : after + 1, after, uuid(rs, "manual_benefit_id"), rs.getString("benefit_name"),
				uuid(rs, "manual_duplicate_of"), uuid(rs, "manual_override_of"), rs.getString("manual_override_reason"),
				reversedAt == null ? null : new Reversal(reversedAt, uuid(rs, "reversed_by"),
						rs.getString("reversal_reason"), rs.getObject("reversal_before", Integer.class),
						rs.getObject("reversal_after", Integer.class)));
	}

	private UUID uuid(ResultSet rs, String field) throws SQLException {
		return rs.getObject(field, UUID.class);
	}

	public record BranchContext(String name, ZoneId zone) {}
	public record Client(UUID id, String name, String phone, String status) {}
	public record Request(UUID id, UUID branchId, UUID clientId, UUID benefitId, UUID overrideOf, String overrideReason) {}
	public record Entry(UUID id, UUID organizationId, UUID branchId, UUID clientId, String clientName,
			UUID actorUserId, OffsetDateTime occurredAt, LocalDate businessDate, String method, String decision,
			String reason, UUID membershipId, UUID entitlementId, Integer remainingBefore, Integer remainingAfter,
			UUID benefitId, String benefitName, UUID duplicateOf, UUID overrideOf, String overrideReason,
			Reversal reversal) {}
	public record Reversal(OffsetDateTime occurredAt, UUID actorUserId, String reason,
			Integer remainingBefore, Integer remainingAfter) {}
}
