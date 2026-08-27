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

	public Optional<ClientLifecycleConfiguration> configuration(UUID organizationId) {
		return jdbcClient.sql("""
				SELECT organization.id AS organization_id, organization.default_currency,
				       COALESCE(configuration.registration_fee_enabled, false) AS registration_fee_enabled,
				       COALESCE(configuration.registration_fee_cents, 0) AS registration_fee_cents,
				       COALESCE(configuration.registration_fee_label, 'Inscripción') AS registration_fee_label,
				       COALESCE(configuration.auto_inactivation_enabled, false) AS auto_inactivation_enabled,
				       COALESCE(configuration.inactivity_months, 6) AS inactivity_months,
				       configuration.updated_at, COALESCE(configuration.version, 0) AS version
				FROM organization
				LEFT JOIN client_lifecycle_configuration configuration
				  ON configuration.organization_id = organization.id
				WHERE organization.id = :organizationId
				  AND organization.deleted_at IS NULL
				""")
				.param("organizationId", organizationId)
				.query((rs, row) -> new ClientLifecycleConfiguration(
						rs.getObject("organization_id", UUID.class),
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
			UUID actorUserId,
			boolean registrationFeeEnabled,
			long registrationFeeCents,
			String registrationFeeLabel,
			boolean autoInactivationEnabled,
			int inactivityMonths
	) {
		jdbcClient.sql("""
				INSERT INTO client_lifecycle_configuration (
				    organization_id, registration_fee_enabled, registration_fee_cents,
				    registration_fee_label, auto_inactivation_enabled, inactivity_months,
				    updated_at, updated_by, version
				)
				VALUES (
				    :organizationId, :registrationFeeEnabled, :registrationFeeCents,
				    :registrationFeeLabel, :autoInactivationEnabled, :inactivityMonths,
				    now(), :actorUserId, 0
				)
				ON CONFLICT (organization_id) DO UPDATE SET
				    registration_fee_enabled = EXCLUDED.registration_fee_enabled,
				    registration_fee_cents = EXCLUDED.registration_fee_cents,
				    registration_fee_label = EXCLUDED.registration_fee_label,
				    auto_inactivation_enabled = EXCLUDED.auto_inactivation_enabled,
				    inactivity_months = EXCLUDED.inactivity_months,
				    updated_at = now(),
				    updated_by = EXCLUDED.updated_by,
				    version = client_lifecycle_configuration.version + 1
				""")
				.param("organizationId", organizationId)
				.param("registrationFeeEnabled", registrationFeeEnabled)
				.param("registrationFeeCents", registrationFeeCents)
				.param("registrationFeeLabel", registrationFeeLabel)
				.param("autoInactivationEnabled", autoInactivationEnabled)
				.param("inactivityMonths", inactivityMonths)
				.param("actorUserId", actorUserId)
				.update();
		return configuration(organizationId).orElseThrow();
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

	public List<ClientInactivityCandidate> automaticInactivityCandidates(LocalDate effectiveOn, int limit) {
		return jdbcClient.sql("""
				SELECT client.id, client.organization_id, client.home_branch_id,
				       COALESCE(history.latest_membership_end, client.joined_at::date) AS last_activity_on
				FROM client
				JOIN client_lifecycle_configuration configuration
				  ON configuration.organization_id = client.organization_id
				 AND configuration.auto_inactivation_enabled = true
				LEFT JOIN LATERAL (
				    SELECT MAX(membership.grace_ends_on) AS latest_membership_end
				    FROM membership
				    WHERE membership.organization_id = client.organization_id
				      AND membership.client_id = client.id
				      AND membership.deleted_at IS NULL
				) history ON true
				WHERE client.status = 'ACTIVE'
				  AND client.deleted_at IS NULL
				  AND COALESCE(history.latest_membership_end, client.joined_at::date)
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
				ORDER BY COALESCE(history.latest_membership_end, client.joined_at::date), client.id
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
