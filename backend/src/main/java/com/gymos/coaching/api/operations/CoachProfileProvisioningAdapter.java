package com.gymos.coaching.api.operations;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
public class CoachProfileProvisioningAdapter implements CoachProfileProvisioning {
	private final JdbcClient jdbcClient;

	public CoachProfileProvisioningAdapter(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	@Override
	public void synchronize(
			UUID organizationId,
			UUID userId,
			String displayName,
			String email,
			String phone,
			List<UUID> branchIds,
			boolean active,
			UUID actorUserId
	) {
		OffsetDateTime now = OffsetDateTime.now();
		if (!active) {
			jdbcClient.sql("""
					UPDATE coach
					SET status = 'INACTIVE',
					    updated_at = :updatedAt,
					    updated_by = :updatedBy,
					    version = version + 1
					WHERE organization_id = :organizationId
					  AND user_account_id = :userId
					  AND deleted_at IS NULL
					""")
					.param("updatedAt", now)
					.param("updatedBy", actorUserId)
					.param("organizationId", organizationId)
					.param("userId", userId)
					.update();
			return;
		}

		List<UUID> resolvedBranches = branchIds == null ? List.of() : branchIds.stream().distinct().toList();
		for (UUID branchId : resolvedBranches) {
			linkExistingProfile(organizationId, branchId, userId, displayName, email, phone, actorUserId, now);
			upsertProfile(organizationId, branchId, userId, displayName, email, phone, actorUserId, now);
		}

		if (resolvedBranches.isEmpty()) {
			deactivateProfilesOutsideBranches(organizationId, userId, List.of(), actorUserId, now);
		} else {
			deactivateProfilesOutsideBranches(organizationId, userId, resolvedBranches, actorUserId, now);
		}
	}

	private void linkExistingProfile(
			UUID organizationId,
			UUID branchId,
			UUID userId,
			String displayName,
			String email,
			String phone,
			UUID actorUserId,
			OffsetDateTime now
	) {
		if (email == null || email.isBlank()) {
			return;
		}
		jdbcClient.sql("""
				UPDATE coach
				SET user_account_id = :userId,
				    display_name = :displayName,
				    phone = :phone,
				    status = 'ACTIVE',
				    updated_at = :updatedAt,
				    updated_by = :updatedBy,
				    version = version + 1
				WHERE organization_id = :organizationId
				  AND branch_id = :branchId
				  AND user_account_id IS NULL
				  AND lower(email) = lower(:email)
				  AND deleted_at IS NULL
				""")
				.param("userId", userId)
				.param("displayName", displayName)
				.param("phone", phone)
				.param("updatedAt", now)
				.param("updatedBy", actorUserId)
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("email", email)
				.update();
	}

	private void upsertProfile(
			UUID organizationId,
			UUID branchId,
			UUID userId,
			String displayName,
			String email,
			String phone,
			UUID actorUserId,
			OffsetDateTime now
	) {
		jdbcClient.sql("""
				INSERT INTO coach (
				    id, organization_id, branch_id, user_account_id, display_name, email, phone,
				    specialties_json, bio, status, created_at, updated_at, created_by, updated_by, version
				)
				VALUES (
				    :id, :organizationId, :branchId, :userId, :displayName, :email, :phone,
				    '[]'::jsonb, NULL, 'ACTIVE', :createdAt, :updatedAt, :createdBy, :updatedBy, 0
				)
				ON CONFLICT (organization_id, branch_id, user_account_id) WHERE user_account_id IS NOT NULL
				DO UPDATE SET
				    display_name = EXCLUDED.display_name,
				    email = EXCLUDED.email,
				    phone = EXCLUDED.phone,
				    status = 'ACTIVE',
				    updated_at = EXCLUDED.updated_at,
				    updated_by = EXCLUDED.updated_by,
				    version = coach.version + 1
				""")
				.param("id", UUID.randomUUID())
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("userId", userId)
				.param("displayName", displayName)
				.param("email", email)
				.param("phone", phone)
				.param("createdAt", now)
				.param("updatedAt", now)
				.param("createdBy", actorUserId)
				.param("updatedBy", actorUserId)
				.update();
	}

	private void deactivateProfilesOutsideBranches(
			UUID organizationId,
			UUID userId,
			List<UUID> branchIds,
			UUID actorUserId,
			OffsetDateTime now
	) {
		if (branchIds.isEmpty()) {
			jdbcClient.sql("""
					UPDATE coach
					SET status = 'INACTIVE', updated_at = :updatedAt, updated_by = :updatedBy, version = version + 1
					WHERE organization_id = :organizationId
					  AND user_account_id = :userId
					  AND deleted_at IS NULL
					""")
					.param("updatedAt", now)
					.param("updatedBy", actorUserId)
					.param("organizationId", organizationId)
					.param("userId", userId)
					.update();
			return;
		}
		jdbcClient.sql("""
				UPDATE coach
				SET status = 'INACTIVE', updated_at = :updatedAt, updated_by = :updatedBy, version = version + 1
				WHERE organization_id = :organizationId
				  AND user_account_id = :userId
				  AND branch_id NOT IN (:branchIds)
				  AND deleted_at IS NULL
				""")
				.param("updatedAt", now)
				.param("updatedBy", actorUserId)
				.param("organizationId", organizationId)
				.param("userId", userId)
				.param("branchIds", branchIds)
				.update();
	}
}
