package com.gymos.memberships.infrastructure;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class MembershipCommandRepository {
	private final JdbcClient jdbcClient;

	MembershipCommandRepository(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	Optional<LocalDate> latestMembershipEnd(UUID organizationId, UUID clientId, UUID planId) {
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

	int cancelActiveForPlanChange(UUID organizationId, UUID branchId, UUID membershipId) {
		return jdbcClient.sql("""
				UPDATE membership
				SET status = 'CANCELLED',
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
