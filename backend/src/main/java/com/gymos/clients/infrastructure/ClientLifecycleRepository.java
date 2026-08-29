package com.gymos.clients.infrastructure;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class ClientLifecycleRepository {
	private final JdbcClient jdbcClient;

	public ClientLifecycleRepository(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	public Optional<ClientLifecycleConfiguration> configuration(UUID organizationId, UUID branchId) {
		return jdbcClient.sql("""
				SELECT organization.id AS organization_id, branch.id AS branch_id, organization.default_currency,
				       branch.registration_fee_enabled, branch.registration_fee_cents,
				       branch.registration_fee_label, branch.auto_inactivation_enabled,
				       branch.inactivity_months, branch.updated_at, branch.version
				FROM organization
				JOIN branch ON branch.organization_id = organization.id
				 AND branch.id = :branchId AND branch.deleted_at IS NULL
				WHERE organization.id = :organizationId AND organization.deleted_at IS NULL
				""")
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.query((rs, row) -> new ClientLifecycleConfiguration(
						rs.getObject("organization_id", UUID.class),
						rs.getObject("branch_id", UUID.class),
						rs.getBoolean("registration_fee_enabled"),
						rs.getLong("registration_fee_cents"),
						rs.getString("registration_fee_label"),
						rs.getString("default_currency"),
						rs.getBoolean("auto_inactivation_enabled"),
						rs.getInt("inactivity_months"),
						rs.getObject("updated_at", OffsetDateTime.class),
						rs.getLong("version")
				))
				.optional();
	}

	public ClientLifecycleConfiguration save(
			UUID organizationId,
			UUID branchId,
			UUID actorUserId,
			boolean registrationFeeEnabled,
			long registrationFeeCents,
			String registrationFeeLabel,
			boolean autoInactivationEnabled,
			int inactivityMonths
	) {
		jdbcClient.sql("""
				UPDATE branch SET registration_fee_enabled = :registrationFeeEnabled,
				    registration_fee_cents = :registrationFeeCents,
				    registration_fee_label = :registrationFeeLabel,
				    auto_inactivation_enabled = :autoInactivationEnabled,
				    inactivity_months = :inactivityMonths, updated_at = now(),
				    updated_by = :actorUserId, version = version + 1
				WHERE organization_id = :organizationId AND id = :branchId AND deleted_at IS NULL
				""")
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("registrationFeeEnabled", registrationFeeEnabled)
				.param("registrationFeeCents", registrationFeeCents)
				.param("registrationFeeLabel", registrationFeeLabel)
				.param("autoInactivationEnabled", autoInactivationEnabled)
				.param("inactivityMonths", inactivityMonths)
				.param("actorUserId", actorUserId)
				.update();
		return configuration(organizationId, branchId).orElseThrow();
	}

	public Optional<LocalDate> latestMembershipEnd(UUID organizationId, UUID clientId) {
		return jdbcClient.sql("""
				SELECT MAX(grace_ends_on)
				FROM membership
				WHERE organization_id = :organizationId
				  AND client_id = :clientId
				  AND deleted_at IS NULL
				""")
				.param("organizationId", organizationId)
				.param("clientId", clientId)
				.query(LocalDate.class)
				.optional();
	}

	public Optional<LocalDate> latestVisitPackageEnd(UUID organizationId, UUID clientId) {
		return jdbcClient.sql("""
				SELECT MAX(
				    CASE
				        WHEN status = 'ACTIVE' THEN valid_until
				        ELSE LEAST(valid_until, updated_at::date)
				    END
				)
				FROM visit_package_entitlement
				WHERE organization_id = :organizationId
				  AND client_id = :clientId
				  AND activated_at IS NOT NULL
				  AND deleted_at IS NULL
				""")
				.param("organizationId", organizationId)
				.param("clientId", clientId)
				.query(LocalDate.class)
				.optional();
	}

	public List<ClientInactivityCandidate> automaticInactivityCandidates(LocalDate effectiveOn, int limit) {
		return jdbcClient.sql("""
				SELECT client.id, client.organization_id, client.home_branch_id,
				       GREATEST(
				           COALESCE(history.latest_membership_end, client.joined_at::date),
				           COALESCE(package_history.latest_package_end, client.joined_at::date)
				       ) AS last_activity_on
				FROM client
				JOIN branch configuration
				  ON configuration.organization_id = client.organization_id
				 AND configuration.id = client.home_branch_id
				 AND configuration.auto_inactivation_enabled = true
				 AND configuration.deleted_at IS NULL
				LEFT JOIN LATERAL (
				    SELECT MAX(membership.grace_ends_on) AS latest_membership_end
				    FROM membership
				    WHERE membership.organization_id = client.organization_id
				      AND membership.client_id = client.id
				      AND membership.deleted_at IS NULL
				) history ON true
				LEFT JOIN LATERAL (
				    SELECT MAX(
				        CASE WHEN entitlement.status = 'ACTIVE' THEN entitlement.valid_until
				             ELSE LEAST(entitlement.valid_until, entitlement.updated_at::date) END
				    ) AS latest_package_end
				    FROM visit_package_entitlement entitlement
				    WHERE entitlement.organization_id = client.organization_id
				      AND entitlement.client_id = client.id
				      AND entitlement.activated_at IS NOT NULL
				      AND entitlement.deleted_at IS NULL
				) package_history ON true
				WHERE client.status = 'ACTIVE'
				  AND client.deleted_at IS NULL
				  AND GREATEST(
				      COALESCE(history.latest_membership_end, client.joined_at::date),
				      COALESCE(package_history.latest_package_end, client.joined_at::date)
				  )
				      <= :effectiveOn - (configuration.inactivity_months * INTERVAL '1 month')
				  AND NOT EXISTS (
				      SELECT 1
				      FROM membership active_membership
				      WHERE active_membership.organization_id = client.organization_id
				        AND active_membership.client_id = client.id
				        AND active_membership.status = 'ACTIVE'
				        AND active_membership.grace_ends_on >= :effectiveOn
				        AND active_membership.deleted_at IS NULL
				  )
				  AND NOT EXISTS (
				      SELECT 1
				      FROM visit_package_entitlement active_package
				      WHERE active_package.organization_id = client.organization_id
				        AND active_package.client_id = client.id
				        AND active_package.status = 'ACTIVE'
				        AND active_package.remaining_visits > 0
				        AND active_package.valid_from <= :effectiveOn
				        AND active_package.valid_until >= :effectiveOn
				        AND active_package.deleted_at IS NULL
				  )
				ORDER BY last_activity_on, client.id
				LIMIT :limit
				FOR UPDATE OF client SKIP LOCKED
				""")
				.param("effectiveOn", effectiveOn)
				.param("limit", Math.max(1, Math.min(limit, 1000)))
				.query((rs, row) -> new ClientInactivityCandidate(
						rs.getObject("id", UUID.class),
						rs.getObject("organization_id", UUID.class),
						rs.getObject("home_branch_id", UUID.class),
						rs.getObject("last_activity_on", LocalDate.class)
				))
				.list();
	}

	public boolean markAutomaticallyInactive(UUID organizationId, UUID clientId, OffsetDateTime now) {
		return jdbcClient.sql("""
				UPDATE client
				SET status = 'INACTIVE',
				    inactivated_at = :now,
				    inactivation_reason = 'MEMBERSHIP_INACTIVITY',
				    updated_at = :now,
				    version = version + 1
				WHERE organization_id = :organizationId
				  AND id = :clientId
				  AND status = 'ACTIVE'
				  AND deleted_at IS NULL
				""")
				.param("organizationId", organizationId)
				.param("clientId", clientId)
				.param("now", now)
				.update() == 1;
	}

	public record ClientLifecycleConfiguration(
			UUID organizationId,
			UUID branchId,
			boolean registrationFeeEnabled,
			long registrationFeeCents,
			String registrationFeeLabel,
			String currency,
			boolean autoInactivationEnabled,
			int inactivityMonths,
			OffsetDateTime updatedAt,
			long version
	) {
	}

	public record ClientInactivityCandidate(
			UUID clientId,
			UUID organizationId,
			UUID homeBranchId,
			LocalDate lastActivityOn
	) {
	}
}
