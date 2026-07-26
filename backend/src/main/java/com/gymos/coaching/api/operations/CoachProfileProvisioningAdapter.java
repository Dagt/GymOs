package com.gymos.coaching.api.operations;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymos.common.api.BusinessException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
public class CoachProfileProvisioningAdapter implements CoachProfileProvisioning {
	private final JdbcClient jdbcClient;
	private final ObjectMapper objectMapper;

	public CoachProfileProvisioningAdapter(JdbcClient jdbcClient, ObjectMapper objectMapper) {
		this.jdbcClient = jdbcClient;
		this.objectMapper = objectMapper;
	}

	@Override
	public void synchronize(
			UUID organizationId,
			UUID userId,
			String displayName,
			String email,
			String phone,
			List<UUID> branchIds,
			List<String> specialties,
			String bio,
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
		boolean updateDetails = specialties != null || bio != null;
		String specialtiesJson = specialtiesJson(specialties);
		String normalizedBio = bio == null || bio.isBlank() ? null : bio.trim();
		for (UUID branchId : resolvedBranches) {
			linkExistingProfile(
					organizationId, branchId, userId, displayName, email, phone,
					specialtiesJson, normalizedBio, updateDetails, actorUserId, now
			);
			upsertProfile(
					organizationId, branchId, userId, displayName, email, phone,
					specialtiesJson, normalizedBio, updateDetails, actorUserId, now
			);
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
			String specialtiesJson,
			String bio,
			boolean updateDetails,
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
				    specialties_json = CASE
				        WHEN :updateDetails THEN CAST(:specialtiesJson AS jsonb)
				        ELSE specialties_json
				    END,
				    bio = CASE WHEN :updateDetails THEN :bio ELSE bio END,
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
				.param("specialtiesJson", specialtiesJson)
				.param("bio", bio)
				.param("updateDetails", updateDetails)
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
			String specialtiesJson,
			String bio,
			boolean updateDetails,
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
				    CAST(:specialtiesJson AS jsonb), :bio, 'ACTIVE',
				    :createdAt, :updatedAt, :createdBy, :updatedBy, 0
				)
				ON CONFLICT (organization_id, branch_id, user_account_id) WHERE user_account_id IS NOT NULL
				DO UPDATE SET
				    display_name = EXCLUDED.display_name,
				    email = EXCLUDED.email,
				    phone = EXCLUDED.phone,
				    specialties_json = CASE
				        WHEN :updateDetails THEN EXCLUDED.specialties_json
				        ELSE coach.specialties_json
				    END,
				    bio = CASE WHEN :updateDetails THEN EXCLUDED.bio ELSE coach.bio END,
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
				.param("specialtiesJson", specialtiesJson)
				.param("bio", bio)
				.param("updateDetails", updateDetails)
				.param("createdAt", now)
				.param("updatedAt", now)
				.param("createdBy", actorUserId)
				.param("updatedBy", actorUserId)
				.update();
	}

	private String specialtiesJson(List<String> specialties) {
		List<String> normalized = specialties == null
				? List.of()
				: specialties.stream()
						.map(String::trim)
						.filter(value -> !value.isBlank())
						.distinct()
						.toList();
		try {
			return objectMapper.writeValueAsString(normalized);
		} catch (JsonProcessingException exception) {
			throw new BusinessException(
					"COACH_SPECIALTIES_INVALID",
					"No fue posible guardar las especialidades del coach."
			);
		}
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
