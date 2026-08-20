package com.gymos.memberships.infrastructure;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class MembershipCommandRepository {
	private final JdbcClient jdbcClient;

	MembershipCommandRepository(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	UUID insertActiveMembership(
			UUID organizationId,
			UUID branchId,
			UUID clientId,
			UUID planId,
			UUID saleId,
			LocalDate startsOn,
			LocalDate endsOn,
			LocalDate graceEndsOn,
			UUID sourceMembershipId,
			String sourcePaymentEventKey
	) {
		OffsetDateTime now = OffsetDateTime.now();
		UUID membershipId = UUID.randomUUID();
		jdbcClient.sql("""
				INSERT INTO membership (
				    id, organization_id, branch_id, client_id, plan_id, sale_id, status,
				    activated_at, starts_on, ends_on, grace_ends_on,
				    created_at, updated_at, created_by, updated_by, version,
				    source_membership_id, source_payment_event_key
				)
				VALUES (
				    :id, :organizationId, :branchId, :clientId, :planId, :saleId, 'ACTIVE',
				    :activatedAt, :startsOn, :endsOn, :graceEndsOn,
				    :createdAt, :updatedAt, NULL, NULL, 0,
				    :sourceMembershipId, :sourcePaymentEventKey
				)
				""")
				.param("id", membershipId)
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("clientId", clientId)
				.param("planId", planId)
				.param("saleId", saleId)
				.param("activatedAt", now)
				.param("startsOn", startsOn)
				.param("endsOn", endsOn)
				.param("graceEndsOn", graceEndsOn)
				.param("createdAt", now)
				.param("updatedAt", now)
				.param("sourceMembershipId", sourceMembershipId)
				.param("sourcePaymentEventKey", sourcePaymentEventKey)
				.update();
		return membershipId;
	}

	int updateMembershipForRenewal(
			UUID organizationId,
			UUID membershipId,
			UUID saleId,
			LocalDate startsOn,
			LocalDate endsOn,
			LocalDate graceEndsOn,
			String eventKey
	) {
		return jdbcClient.sql("""
				UPDATE membership
				SET sale_id = :saleId,
				    status = 'ACTIVE',
				    starts_on = :startsOn,
				    ends_on = :endsOn,
				    grace_ends_on = :graceEndsOn,
				    source_payment_event_key = :eventKey,
				    updated_at = NOW(),
				    version = version + 1
				WHERE organization_id = :organizationId
				  AND id = :membershipId
				""")
				.param("organizationId", organizationId)
				.param("membershipId", membershipId)
				.param("saleId", saleId)
				.param("startsOn", startsOn)
				.param("endsOn", endsOn)
				.param("graceEndsOn", graceEndsOn)
				.param("eventKey", eventKey)
				.update();
	}

	void insertPeriod(
			UUID organizationId,
			UUID branchId,
			UUID membershipId,
			UUID clientId,
			UUID planId,
			UUID saleId,
			String operationType,
			LocalDate startsOn,
			LocalDate endsOn,
			String sourceEventKey
	) {
		jdbcClient.sql("""
				INSERT INTO membership_period (
				    id, organization_id, branch_id, membership_id, client_id, plan_id,
				    sale_id, operation_type, starts_on, ends_on, source_event_key, created_at
				)
				VALUES (
				    :id, :organizationId, :branchId, :membershipId, :clientId, :planId,
				    :saleId, :operationType, :startsOn, :endsOn, :sourceEventKey, :createdAt
				)
				ON CONFLICT (organization_id, source_event_key)
				    WHERE source_event_key IS NOT NULL
				DO NOTHING
				""")
				.param("id", UUID.randomUUID())
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("membershipId", membershipId)
				.param("clientId", clientId)
				.param("planId", planId)
				.param("saleId", saleId)
				.param("operationType", operationType)
				.param("startsOn", startsOn)
				.param("endsOn", endsOn)
				.param("sourceEventKey", sourceEventKey)
				.param("createdAt", OffsetDateTime.now())
				.update();
	}

	boolean hasOtherActiveMembership(UUID organizationId, UUID clientId, UUID excludedMembershipId) {
		if (excludedMembershipId == null) {
			Integer count = jdbcClient.sql("""
					SELECT COUNT(*)
					FROM membership
					WHERE organization_id = :organizationId
					  AND client_id = :clientId
					  AND status = 'ACTIVE'
					  AND deleted_at IS NULL
					""")
					.param("organizationId", organizationId)
					.param("clientId", clientId)
					.query(Integer.class)
					.single();
			return count > 0;
		}
		Integer count = jdbcClient.sql("""
				SELECT COUNT(*)
				FROM membership
				WHERE organization_id = :organizationId
				  AND client_id = :clientId
				  AND status = 'ACTIVE'
				  AND deleted_at IS NULL
				  AND id <> :excludedMembershipId
				""")
				.param("organizationId", organizationId)
				.param("clientId", clientId)
				.param("excludedMembershipId", excludedMembershipId)
				.query(Integer.class)
				.single();
		return count > 0;
	}

	int expireDueMemberships(UUID organizationId, UUID clientId, LocalDate businessDate) {
		if (clientId == null) {
			return jdbcClient.sql("""
					UPDATE membership
					SET status = 'EXPIRED',
					    updated_at = NOW(),
					    version = version + 1
					WHERE organization_id = :organizationId
					  AND status = 'ACTIVE'
					  AND grace_ends_on < :businessDate
					  AND deleted_at IS NULL
					""")
					.param("organizationId", organizationId)
					.param("businessDate", businessDate)
					.update();
		}
		return jdbcClient.sql("""
				UPDATE membership
				SET status = 'EXPIRED',
				    updated_at = NOW(),
				    version = version + 1
				WHERE organization_id = :organizationId
				  AND client_id = :clientId
				  AND status = 'ACTIVE'
				  AND grace_ends_on < :businessDate
				  AND deleted_at IS NULL
				""")
				.param("organizationId", organizationId)
				.param("clientId", clientId)
				.param("businessDate", businessDate)
				.update();
	}

	int expireAllDueMemberships(LocalDate businessDate) {
		return jdbcClient.sql("""
				UPDATE membership
				SET status = 'EXPIRED',
				    updated_at = NOW(),
				    version = version + 1
				WHERE status = 'ACTIVE'
				  AND grace_ends_on < :businessDate
				  AND deleted_at IS NULL
				""")
				.param("businessDate", businessDate)
				.update();
	}

	List<MembershipRepository.MembershipLifecycleRecord> unpublishedExpiringMemberships(
			LocalDate businessDate,
			int horizonDays,
			int limit
	) {
		return jdbcClient.sql("""
				SELECT m.organization_id, m.branch_id, m.id AS membership_id,
				       m.client_id, m.plan_id, m.ends_on, m.grace_ends_on
				FROM membership m
				WHERE m.status = 'ACTIVE'
				  AND m.deleted_at IS NULL
				  AND m.ends_on BETWEEN :businessDate AND :horizonDate
				  AND NOT EXISTS (
				      SELECT 1
				      FROM membership_lifecycle_occurrence occurrence
				      WHERE occurrence.organization_id = m.organization_id
				        AND occurrence.membership_id = m.id
				        AND occurrence.event_type = 'EXPIRING'
				        AND occurrence.effective_on = :businessDate
				  )
				ORDER BY m.ends_on, m.id
				LIMIT :limit
				""")
				.param("businessDate", businessDate)
				.param("horizonDate", businessDate.plusDays(horizonDays))
				.param("limit", limit)
				.query(this::mapLifecycleRecord)
				.list();
	}

	boolean recordExpiringOccurrence(MembershipRepository.MembershipLifecycleRecord membership, LocalDate businessDate) {
		return jdbcClient.sql("""
				INSERT INTO membership_lifecycle_occurrence (
				    id, organization_id, branch_id, membership_id,
				    event_type, effective_on, created_at
				)
				VALUES (
				    :id, :organizationId, :branchId, :membershipId,
				    'EXPIRING', :effectiveOn, :createdAt
				)
				ON CONFLICT (organization_id, membership_id, event_type, effective_on)
				DO NOTHING
				""")
				.param("id", UUID.randomUUID())
				.param("organizationId", membership.organizationId())
				.param("branchId", membership.branchId())
				.param("membershipId", membership.membershipId())
				.param("effectiveOn", businessDate)
				.param("createdAt", OffsetDateTime.now())
				.update() == 1;
	}

	List<MembershipRepository.MembershipLifecycleRecord> claimUnpublishedExpiredMemberships(int limit) {
		OffsetDateTime now = OffsetDateTime.now();
		return jdbcClient.sql("""
				WITH pending AS (
				    SELECT id
				    FROM membership
				    WHERE status = 'EXPIRED'
				      AND expired_event_published_at IS NULL
				      AND deleted_at IS NULL
				    ORDER BY grace_ends_on, id
				    FOR UPDATE SKIP LOCKED
				    LIMIT :limit
				)
				UPDATE membership membership
				SET expired_event_published_at = :publishedAt,
				    updated_at = :publishedAt,
				    version = version + 1
				FROM pending
				WHERE membership.id = pending.id
				RETURNING membership.organization_id, membership.branch_id,
				          membership.id AS membership_id, membership.client_id,
				          membership.plan_id, membership.ends_on, membership.grace_ends_on
				""")
				.param("limit", limit)
				.param("publishedAt", now)
				.query(this::mapLifecycleRecord)
				.list();
	}

	private MembershipRepository.MembershipLifecycleRecord mapLifecycleRecord(
			java.sql.ResultSet resultSet,
			int rowNumber
	) throws java.sql.SQLException {
		return new MembershipRepository.MembershipLifecycleRecord(
				resultSet.getObject("organization_id", UUID.class),
				resultSet.getObject("branch_id", UUID.class),
				resultSet.getObject("membership_id", UUID.class),
				resultSet.getObject("client_id", UUID.class),
				resultSet.getObject("plan_id", UUID.class),
				resultSet.getObject("ends_on", LocalDate.class),
				resultSet.getObject("grace_ends_on", LocalDate.class)
		);
	}

	int expireMembershipIfDue(UUID organizationId, UUID membershipId, LocalDate businessDate) {
		return jdbcClient.sql("""
				UPDATE membership
				SET status = 'EXPIRED',
				    updated_at = NOW(),
				    version = version + 1
				WHERE organization_id = :organizationId
				  AND id = :membershipId
				  AND status = 'ACTIVE'
				  AND grace_ends_on < :businessDate
				  AND deleted_at IS NULL
				""")
				.param("organizationId", organizationId)
				.param("membershipId", membershipId)
				.param("businessDate", businessDate)
				.update();
	}

	int cancelActiveForPlanChange(UUID organizationId, UUID branchId, UUID membershipId) {
		return jdbcClient.sql("""
				UPDATE membership
				SET status = 'CANCELLED',
				    cancelled_at = :updatedAt,
				    cancellation_reason = 'Cambio de plan completado',
				    updated_at = :updatedAt,
				    version = version + 1
				WHERE organization_id = :organizationId
				  AND (
				      branch_id = :branchId
				      OR EXISTS (
				          SELECT 1
				          FROM membership_plan mp
				          WHERE mp.id = membership.plan_id
				            AND mp.multi_branch_access = true
				      )
				  )
				  AND id = :membershipId
				  AND status = 'ACTIVE'
				  AND deleted_at IS NULL
				""")
				.param("updatedAt", OffsetDateTime.now())
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("membershipId", membershipId)
				.update();
	}
}
