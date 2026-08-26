package com.gymos.coaching.infrastructure;

import com.gymos.coaching.domain.AssignmentLifecycle;
import com.gymos.common.time.BusinessTime;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class CoachingRepository {
	private final JdbcClient jdbcClient;

	public CoachingRepository(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	public List<CoachRecord> coaches(UUID organizationId, UUID branchId) {
		return jdbcClient.sql("""
				SELECT id, user_account_id, display_name, email, phone, specialties_json::text AS specialties_json,
				       bio, status, created_at, updated_at
				FROM coach
				WHERE organization_id = :organizationId
				  AND branch_id = :branchId
				  AND deleted_at IS NULL
				ORDER BY display_name
				""")
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.query(this::mapCoach)
				.list();
	}

	public List<UUID> accessibleBranchIds(UUID organizationId, UUID actorUserId) {
		return jdbcClient.sql("""
				SELECT b.id
				FROM branch b
				JOIN user_branch_access uba ON uba.branch_id = b.id
				WHERE b.organization_id = :organizationId
				  AND uba.user_id = :actorUserId
				  AND b.status = 'ACTIVE'
				  AND b.deleted_at IS NULL
				ORDER BY b.name
				""")
				.param("organizationId", organizationId)
				.param("actorUserId", actorUserId)
				.query(UUID.class)
				.list();
	}

	public List<CoachProfileRow> coachProfileRows(UUID organizationId, List<UUID> branchIds) {
		if (branchIds == null || branchIds.isEmpty()) return List.of();
		return jdbcClient.sql("""
				SELECT u.id AS user_account_id,
				       trim(concat(p.first_name, ' ', p.last_name, ' ', coalesce(p.maternal_last_name, ''))) AS display_name,
				       p.email,
				       p.phone,
				       COALESCE(c.specialties_json, details.specialties_json, '[]'::jsonb)::text AS specialties_json,
				       COALESCE(c.bio, details.bio) AS bio,
				       CASE
				           WHEN u.status = 'ACTIVE' AND COALESCE(c.status, 'ACTIVE') = 'ACTIVE'
				           THEN 'ACTIVE'
				           ELSE 'INACTIVE'
				       END AS status,
				       b.id AS branch_id,
				       b.name AS branch_name
				FROM user_account u
				JOIN person p ON p.id = u.person_id
				JOIN user_role ur ON ur.user_id = u.id
				JOIN role r ON r.id = ur.role_id
				JOIN user_branch_access uba ON uba.user_id = u.id
				JOIN branch b ON b.id = uba.branch_id
				LEFT JOIN coach c
				  ON c.organization_id = u.organization_id
				 AND c.branch_id = b.id
				 AND c.user_account_id = u.id
				 AND c.deleted_at IS NULL
				LEFT JOIN LATERAL (
				    SELECT profile.specialties_json, profile.bio
				    FROM coach profile
				    WHERE profile.organization_id = u.organization_id
				      AND profile.user_account_id = u.id
				      AND profile.deleted_at IS NULL
				    ORDER BY profile.updated_at DESC
				    LIMIT 1
				) details ON true
				WHERE u.organization_id = :organizationId
				  AND u.deleted_at IS NULL
				  AND r.deleted_at IS NULL
				  AND lower(r.name) = 'coach'
				  AND b.id IN (:branchIds)
				  AND b.deleted_at IS NULL
				ORDER BY display_name, b.name
				""")
				.param("organizationId", organizationId)
				.param("branchIds", branchIds)
				.query((resultSet, rowNumber) -> new CoachProfileRow(
						resultSet.getObject("user_account_id", UUID.class),
						resultSet.getString("display_name"),
						resultSet.getString("email"),
						resultSet.getString("phone"),
						resultSet.getString("specialties_json"),
						resultSet.getString("bio"),
						resultSet.getString("status"),
						resultSet.getObject("branch_id", UUID.class),
						resultSet.getString("branch_name")
				))
				.list();
	}

	public boolean userHasCoachRole(UUID organizationId, UUID userId) {
		Integer count = jdbcClient.sql("""
				SELECT COUNT(*)
				FROM user_account u
				JOIN user_role ur ON ur.user_id = u.id
				JOIN role r ON r.id = ur.role_id
				WHERE u.organization_id = :organizationId
				  AND u.id = :userId
				  AND u.deleted_at IS NULL
				  AND r.deleted_at IS NULL
				  AND lower(r.name) = 'coach'
				""")
				.param("organizationId", organizationId)
				.param("userId", userId)
				.query(Integer.class)
				.single();
		return count > 0;
	}

	public Optional<CoachIdentity> coachIdentity(UUID organizationId, UUID userId) {
		return jdbcClient.sql("""
				SELECT u.id,
				       trim(concat(p.first_name, ' ', p.last_name, ' ', coalesce(p.maternal_last_name, ''))) AS display_name,
				       p.email, p.phone
				FROM user_account u
				JOIN person p ON p.id = u.person_id
				WHERE u.organization_id = :organizationId
				  AND u.id = :userId
				  AND u.deleted_at IS NULL
				""")
				.param("organizationId", organizationId)
				.param("userId", userId)
				.query((resultSet, rowNumber) -> new CoachIdentity(
						resultSet.getObject("id", UUID.class),
						resultSet.getString("display_name"),
						resultSet.getString("email"),
						resultSet.getString("phone")
				))
				.optional();
	}

	public void updateCoachProfileDetails(
			UUID organizationId, UUID userId, String specialtiesJson, String bio,
			UUID actorUserId, OffsetDateTime now
	) {
		jdbcClient.sql("""
				UPDATE coach
				SET specialties_json = CAST(:specialtiesJson AS jsonb),
				    bio = :bio,
				    updated_at = :updatedAt,
				    updated_by = :actorUserId,
				    version = version + 1
				WHERE organization_id = :organizationId
				  AND user_account_id = :userId
				  AND deleted_at IS NULL
				""")
				.param("specialtiesJson", specialtiesJson)
				.param("bio", bio)
				.param("updatedAt", now)
				.param("actorUserId", actorUserId)
				.param("organizationId", organizationId)
				.param("userId", userId)
				.update();
	}

	public void upsertCoachBranchProfile(
			UUID organizationId, UUID branchId, CoachIdentity identity,
			String specialtiesJson, String bio, UUID actorUserId, OffsetDateTime now
	) {
		jdbcClient.sql("""
				INSERT INTO coach (
				    id, organization_id, branch_id, user_account_id, display_name,
				    email, phone, specialties_json, bio, status,
				    created_at, updated_at, created_by, updated_by, version
				)
				VALUES (
				    :id, :organizationId, :branchId, :userId, :displayName,
				    :email, :phone, CAST(:specialtiesJson AS jsonb), :bio, 'ACTIVE',
				    :createdAt, :updatedAt, :actorUserId, :actorUserId, 0
				)
				ON CONFLICT (organization_id, branch_id, user_account_id)
				    WHERE user_account_id IS NOT NULL
				DO UPDATE SET
				    display_name = EXCLUDED.display_name,
				    email = EXCLUDED.email,
				    phone = EXCLUDED.phone,
				    specialties_json = EXCLUDED.specialties_json,
				    bio = EXCLUDED.bio,
				    status = 'ACTIVE',
				    updated_at = EXCLUDED.updated_at,
				    updated_by = EXCLUDED.updated_by,
				    version = coach.version + 1
				""")
				.param("id", UUID.randomUUID())
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("userId", identity.userId())
				.param("displayName", identity.displayName())
				.param("email", identity.email())
				.param("phone", identity.phone())
				.param("specialtiesJson", specialtiesJson)
				.param("bio", bio)
				.param("createdAt", now)
				.param("updatedAt", now)
				.param("actorUserId", actorUserId)
				.update();
	}

	public void deactivateCoachBranches(
			UUID organizationId, UUID userId, List<UUID> managedBranchIds,
			List<UUID> activeBranchIds, UUID actorUserId, OffsetDateTime now
	) {
		if (managedBranchIds == null || managedBranchIds.isEmpty()) return;
		if (activeBranchIds == null || activeBranchIds.isEmpty()) {
			jdbcClient.sql("""
					UPDATE coach
					SET status = 'INACTIVE', updated_at = :updatedAt,
					    updated_by = :actorUserId, version = version + 1
					WHERE organization_id = :organizationId
					  AND user_account_id = :userId
					  AND branch_id IN (:managedBranchIds)
					  AND deleted_at IS NULL
					""")
					.param("updatedAt", now)
					.param("actorUserId", actorUserId)
					.param("organizationId", organizationId)
					.param("userId", userId)
					.param("managedBranchIds", managedBranchIds)
					.update();
			return;
		}
		jdbcClient.sql("""
				UPDATE coach
				SET status = 'INACTIVE', updated_at = :updatedAt,
				    updated_by = :actorUserId, version = version + 1
				WHERE organization_id = :organizationId
				  AND user_account_id = :userId
				  AND branch_id IN (:managedBranchIds)
				  AND branch_id NOT IN (:activeBranchIds)
				  AND deleted_at IS NULL
				""")
				.param("updatedAt", now)
				.param("actorUserId", actorUserId)
				.param("organizationId", organizationId)
				.param("userId", userId)
				.param("managedBranchIds", managedBranchIds)
				.param("activeBranchIds", activeBranchIds)
				.update();
	}

	public void replaceCoachBranchAccess(
			UUID userId, List<UUID> managedBranchIds, List<UUID> selectedBranchIds
	) {
		if (managedBranchIds == null || managedBranchIds.isEmpty()) return;
		jdbcClient.sql("""
				DELETE FROM user_branch_access
				WHERE user_id = :userId AND branch_id IN (:managedBranchIds)
				""")
				.param("userId", userId)
				.param("managedBranchIds", managedBranchIds)
				.update();
		for (UUID branchId : selectedBranchIds == null ? List.<UUID>of() : selectedBranchIds) {
			jdbcClient.sql("""
					INSERT INTO user_branch_access (user_id, branch_id, access_scope)
					VALUES (:userId, :branchId, 'BRANCH')
					ON CONFLICT (user_id, branch_id)
					DO UPDATE SET access_scope = EXCLUDED.access_scope
					""")
					.param("userId", userId)
					.param("branchId", branchId)
					.update();
		}
	}

	public Optional<CoachRecord> coach(UUID organizationId, UUID branchId, UUID coachId) {
		return jdbcClient.sql("""
				SELECT id, user_account_id, display_name, email, phone, specialties_json::text AS specialties_json,
				       bio, status, created_at, updated_at
				FROM coach
				WHERE organization_id = :organizationId
				  AND branch_id = :branchId
				  AND id = :coachId
				  AND deleted_at IS NULL
				""")
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("coachId", coachId)
				.query(this::mapCoach)
				.optional();
	}

	public Optional<CoachRecord> coachByUser(UUID organizationId, UUID branchId, UUID userId) {
		return jdbcClient.sql("""
				SELECT id, user_account_id, display_name, email, phone, specialties_json::text AS specialties_json,
				       bio, status, created_at, updated_at
				FROM coach
				WHERE organization_id = :organizationId
				  AND branch_id = :branchId
				  AND user_account_id = :userId
				  AND deleted_at IS NULL
				""")
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("userId", userId)
				.query(this::mapCoach)
				.optional();
	}

	public boolean coachEmailExists(UUID organizationId, UUID branchId, String email) {
		Integer count = jdbcClient.sql("""
				SELECT COUNT(*)
				FROM coach
				WHERE organization_id = :organizationId
				  AND branch_id = :branchId
				  AND lower(email) = lower(:email)
				  AND deleted_at IS NULL
				""")
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("email", email)
				.query(Integer.class)
				.single();
		return count > 0;
	}

	public UUID createCoach(UUID organizationId, UUID branchId, UUID actorUserId, CreateCoachData data) {
		OffsetDateTime now = OffsetDateTime.now();
		UUID coachId = UUID.randomUUID();
		jdbcClient.sql("""
				INSERT INTO coach (
				    id, organization_id, branch_id, display_name, email, phone, specialties_json,
				    bio, status, created_at, updated_at, created_by, updated_by, version
				)
				VALUES (
				    :id, :organizationId, :branchId, :displayName, :email, :phone,
				    COALESCE(CAST(:specialties AS jsonb), '[]'::jsonb),
				    :bio, 'ACTIVE', :createdAt, :updatedAt, :createdBy, :updatedBy, 0
				)
				""")
				.param("id", coachId)
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("displayName", data.displayName())
				.param("email", data.email())
				.param("phone", data.phone())
				.param("specialties", data.specialties())
				.param("bio", data.bio())
				.param("createdAt", now)
				.param("updatedAt", now)
				.param("createdBy", actorUserId)
				.param("updatedBy", actorUserId)
				.update();
		return coachId;
	}

	public List<WorkoutSummaryRecord> workouts(UUID organizationId, UUID branchId, UUID ownerUserId) {
		return jdbcClient.sql("""
				SELECT w.id, w.coach_id, c.display_name AS coach_name, w.title, w.goal, w.level,
				       w.status, v.id AS latest_version_id, v.version_number AS latest_version_number,
				       w.created_at, w.updated_at
				FROM workout w
				LEFT JOIN coach c ON c.id = w.coach_id
				LEFT JOIN LATERAL (
				    SELECT id, version_number
				    FROM workout_version
				    WHERE workout_id = w.id
				      AND deleted_at IS NULL
				      AND (
				        w.status NOT IN ('PUBLISHED', 'ACTIVE')
				        OR status = 'PUBLISHED'
				      )
				    ORDER BY version_number DESC
				    LIMIT 1
				) v ON true
				WHERE w.organization_id = :organizationId
				  AND w.branch_id = :branchId
				  AND (CAST(:ownerUserId AS UUID) IS NULL OR c.user_account_id = CAST(:ownerUserId AS UUID))
				  AND w.deleted_at IS NULL
				ORDER BY w.updated_at DESC, w.title
				""")
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("ownerUserId", ownerUserId)
				.query(this::mapWorkoutSummary)
				.list();
	}

	public Optional<WorkoutRecord> workout(UUID organizationId, UUID branchId, UUID workoutId, UUID ownerUserId) {
		return jdbcClient.sql("""
				SELECT w.id, w.coach_id, c.display_name AS coach_name, w.title, w.goal, w.level,
				       w.status, w.created_at, w.updated_at
				FROM workout w
				LEFT JOIN coach c ON c.id = w.coach_id
				WHERE w.organization_id = :organizationId
				  AND w.branch_id = :branchId
				  AND w.id = :workoutId
				  AND (CAST(:ownerUserId AS UUID) IS NULL OR c.user_account_id = CAST(:ownerUserId AS UUID))
				  AND w.deleted_at IS NULL
				""")
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("workoutId", workoutId)
				.param("ownerUserId", ownerUserId)
				.query(this::mapWorkout)
				.optional();
	}

	public UUID createWorkout(UUID organizationId, UUID branchId, UUID actorUserId, CreateWorkoutData data) {
		OffsetDateTime now = OffsetDateTime.now();
		UUID workoutId = UUID.randomUUID();
		jdbcClient.sql("""
				INSERT INTO workout (
				    id, organization_id, branch_id, coach_id, title, goal, level,
				    status, created_at, updated_at, created_by, updated_by, version
				)
				VALUES (
				    :id, :organizationId, :branchId, :coachId, :title, :goal, :level,
				    'DRAFT', :createdAt, :updatedAt, :createdBy, :updatedBy, 0
				)
				""")
				.param("id", workoutId)
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("coachId", data.coachId())
				.param("title", data.title())
				.param("goal", data.goal())
				.param("level", data.level())
				.param("createdAt", now)
				.param("updatedAt", now)
				.param("createdBy", actorUserId)
				.param("updatedBy", actorUserId)
				.update();
		return workoutId;
	}

	public void updateWorkout(UUID organizationId, UUID branchId, UUID workoutId, UUID actorUserId, CreateWorkoutData data) {
		OffsetDateTime now = OffsetDateTime.now();
		jdbcClient.sql("""
				UPDATE workout
				SET title = :title, goal = :goal, level = :level, coach_id = :coachId,
				    updated_at = :now, updated_by = :actorUserId, version = version + 1
				WHERE organization_id = :organizationId
				  AND branch_id = :branchId
				  AND id = :workoutId
				  AND deleted_at IS NULL
				""")
				.param("title", data.title())
				.param("goal", data.goal())
				.param("level", data.level())
				.param("coachId", data.coachId())
				.param("now", now)
				.param("actorUserId", actorUserId)
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("workoutId", workoutId)
				.update();
	}

	public UUID createWorkoutVersion(UUID organizationId, UUID workoutId, UUID actorUserId, CreateWorkoutVersionData data) {
		OffsetDateTime now = OffsetDateTime.now();
		UUID versionId = UUID.randomUUID();
		Integer nextVersion = jdbcClient.sql("""
				SELECT COALESCE(MAX(version_number), 0) + 1
				FROM workout_version
				WHERE organization_id = :organizationId
				  AND workout_id = :workoutId
				  AND deleted_at IS NULL
				""")
				.param("organizationId", organizationId)
				.param("workoutId", workoutId)
				.query(Integer.class)
				.single();
		jdbcClient.sql("""
				INSERT INTO workout_version (
				    id, organization_id, workout_id, version_number, summary, content_json,
				    status, created_at, created_by
				)
				VALUES (
				    :id, :organizationId, :workoutId, :versionNumber, :summary,
				    CAST(:contentJson AS jsonb), 'DRAFT', :createdAt, :createdBy
				)
				""")
				.param("id", versionId)
				.param("organizationId", organizationId)
				.param("workoutId", workoutId)
				.param("versionNumber", nextVersion)
				.param("summary", data.summary())
				.param("contentJson", data.contentJson())
				.param("createdAt", now)
				.param("createdBy", actorUserId)
				.update();
		jdbcClient.sql("""
				UPDATE workout
				SET updated_at = :updatedAt,
				    updated_by = :updatedBy,
				    version = version + 1
				WHERE organization_id = :organizationId
				  AND id = :workoutId
				""")
				.param("updatedAt", now)
				.param("updatedBy", actorUserId)
				.param("organizationId", organizationId)
				.param("workoutId", workoutId)
				.update();
		return versionId;
	}

	public void updateDraftVersion(
			UUID organizationId, UUID workoutId, UUID versionId, CreateWorkoutVersionData data
	) {
		int updated = jdbcClient.sql("""
				UPDATE workout_version
				SET summary = :summary, content_json = CAST(:contentJson AS jsonb)
				WHERE id = :versionId AND workout_id = :workoutId
				  AND organization_id = :organizationId AND status = 'DRAFT'
				  AND deleted_at IS NULL
				""")
				.param("summary", data.summary())
				.param("contentJson", data.contentJson())
				.param("versionId", versionId)
				.param("workoutId", workoutId)
				.param("organizationId", organizationId)
				.update();
		if (updated == 0) throw new IllegalStateException("Draft version not found");
	}

	public void deleteWorkoutStructure(UUID organizationId, UUID versionId) {
		jdbcClient.sql("""
				DELETE FROM workout_session
				WHERE organization_id = :organizationId AND workout_version_id = :versionId
				""")
				.param("organizationId", organizationId)
				.param("versionId", versionId)
				.update();
	}

	public void publishWorkoutVersion(UUID organizationId, UUID workoutId, UUID versionId, UUID actorUserId) {
		int published = jdbcClient.sql("""
				UPDATE workout_version SET status = 'PUBLISHED'
				WHERE id = :versionId AND workout_id = :workoutId
				  AND organization_id = :organizationId AND status = 'DRAFT'
				  AND deleted_at IS NULL
				""")
				.param("versionId", versionId)
				.param("workoutId", workoutId)
				.param("organizationId", organizationId)
				.update();
		if (published == 0) throw new IllegalStateException("Draft version not found");
		jdbcClient.sql("""
				UPDATE workout SET status = 'PUBLISHED', updated_at = now(),
				    updated_by = :actorUserId, version = version + 1
				WHERE id = :workoutId AND organization_id = :organizationId AND deleted_at IS NULL
				""")
				.param("actorUserId", actorUserId)
				.param("workoutId", workoutId)
				.param("organizationId", organizationId)
				.update();
	}

	public UUID createWorkoutSession(
			UUID organizationId, UUID versionId, String name, String focus, String notes, int position
	) {
		UUID id = UUID.randomUUID();
		jdbcClient.sql("""
				INSERT INTO workout_session (id, organization_id, workout_version_id, name, focus, notes, position)
				VALUES (:id, :organizationId, :versionId, :name, :focus, :notes, :position)
				""")
				.param("id", id).param("organizationId", organizationId).param("versionId", versionId)
				.param("name", name).param("focus", focus).param("notes", notes).param("position", position)
				.update();
		return id;
	}

	public UUID createWorkoutBlock(
			UUID organizationId, UUID sessionId, String name, String type, String notes, int position
	) {
		UUID id = UUID.randomUUID();
		jdbcClient.sql("""
				INSERT INTO workout_session_block (
				    id, organization_id, workout_session_id, name, block_type, notes, position
				) VALUES (:id, :organizationId, :sessionId, :name, :type, :notes, :position)
				""")
				.param("id", id).param("organizationId", organizationId).param("sessionId", sessionId)
				.param("name", name).param("type", type).param("notes", notes).param("position", position)
				.update();
		return id;
	}

	public void createWorkoutExercise(
			UUID organizationId, UUID blockId, UUID exerciseId, String name, String prescription,
			Integer sets, String reps, String load, Integer restSeconds, String tempo,
			String duration, String distance, String notes, String groupKey, boolean optional, int position
	) {
		jdbcClient.sql("""
				INSERT INTO workout_exercise (
				    id, organization_id, workout_session_block_id, exercise_id,
				    exercise_name_snapshot, prescription_text, sets, reps, load_text,
				    rest_seconds, tempo, duration_text, distance_text, notes, group_key,
				    optional, position
				) VALUES (
				    :id, :organizationId, :blockId, :exerciseId, :name, :prescription,
				    :sets, :reps, :load, :restSeconds, :tempo, :duration, :distance,
				    :notes, :groupKey, :optional, :position
				)
				""")
				.param("id", UUID.randomUUID()).param("organizationId", organizationId)
				.param("blockId", blockId).param("exerciseId", exerciseId).param("name", name)
				.param("prescription", prescription).param("sets", sets).param("reps", reps)
				.param("load", load).param("restSeconds", restSeconds).param("tempo", tempo)
				.param("duration", duration).param("distance", distance).param("notes", notes)
				.param("groupKey", groupKey).param("optional", optional).param("position", position)
				.update();
	}

	public Optional<WorkoutVersionRecord> workoutVersion(UUID organizationId, UUID workoutId, UUID versionId) {
		return jdbcClient.sql("""
				SELECT id, workout_id, version_number, summary, content_json::text AS content_json,
				       status, created_at
				FROM workout_version
				WHERE organization_id = :organizationId
				  AND workout_id = :workoutId
				  AND id = :versionId
				  AND deleted_at IS NULL
				""")
				.param("organizationId", organizationId)
				.param("workoutId", workoutId)
				.param("versionId", versionId)
				.query(this::mapVersion)
				.optional();
	}

	public Optional<WorkoutVersionRecord> latestWorkoutVersion(UUID organizationId, UUID workoutId) {
		return jdbcClient.sql("""
				SELECT id, workout_id, version_number, summary, content_json::text AS content_json,
				       status, created_at
				FROM workout_version
				WHERE organization_id = :organizationId
				  AND workout_id = :workoutId
				  AND deleted_at IS NULL
				ORDER BY version_number DESC
				LIMIT 1
				""")
				.param("organizationId", organizationId)
				.param("workoutId", workoutId)
				.query(this::mapVersion)
				.optional();
	}

	public void updateAssignmentStatus(
			UUID organizationId, UUID branchId, UUID assignmentId, String status, String reason, UUID actorUserId
	) {
		int updated = jdbcClient.sql("""
				UPDATE workout_assignment
				SET status = :status, updated_at = now(), updated_by = :actorUserId,
				    status_reason = :reason,
				    completed_at = CASE WHEN :status = 'COMPLETED' THEN now() ELSE completed_at END,
				    cancelled_at = CASE WHEN :status = 'CANCELLED' THEN now() ELSE cancelled_at END,
				    version = version + 1
				WHERE id = :assignmentId AND organization_id = :organizationId
				  AND branch_id = :branchId AND deleted_at IS NULL AND status = 'ACTIVE'
				""")
				.param("status", status)
				.param("reason", reason)
				.param("actorUserId", actorUserId)
				.param("assignmentId", assignmentId)
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.update();
		if (updated == 0) throw new com.gymos.common.api.BusinessException("WORKOUT_ASSIGNMENT_CONFLICT", "La asignación cambió. Actualiza el historial antes de continuar.");
	}

	public List<WorkoutVersionRecord> workoutVersions(UUID organizationId, UUID workoutId) {
		return jdbcClient.sql("""
				SELECT id, workout_id, version_number, summary, content_json::text AS content_json,
				       status, created_at
				FROM workout_version
				WHERE organization_id = :organizationId
				  AND workout_id = :workoutId
				  AND deleted_at IS NULL
				ORDER BY version_number DESC
				""")
				.param("organizationId", organizationId)
				.param("workoutId", workoutId)
				.query(this::mapVersion)
				.list();
	}

	public Optional<WorkoutVersionRecord> workoutVersionById(
			UUID organizationId, UUID branchId, UUID versionId, UUID ownerUserId
	) {
		return jdbcClient.sql("""
				SELECT v.id, v.workout_id, v.version_number, v.summary,
				       v.content_json::text AS content_json, v.status, v.created_at
				FROM workout_version v
				JOIN workout w ON w.id = v.workout_id
				LEFT JOIN coach c ON c.id = w.coach_id
				WHERE v.organization_id = :organizationId
				  AND w.branch_id = :branchId
				  AND (CAST(:ownerUserId AS UUID) IS NULL OR c.user_account_id = CAST(:ownerUserId AS UUID))
				  AND v.id = :versionId
				  AND v.deleted_at IS NULL
				  AND w.deleted_at IS NULL
				""")
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("ownerUserId", ownerUserId)
				.param("versionId", versionId)
				.query(this::mapVersion)
				.optional();
	}

	public Optional<WorkoutAssignmentRecord> workoutAssignmentById(
			UUID organizationId, UUID branchId, UUID assignmentId, UUID ownerUserId
	) {
		return jdbcClient.sql("""
				SELECT a.id, a.workout_id, a.workout_version_id, a.client_id,
				       p.first_name || ' ' || p.last_name AS client_name,
				       a.coach_id, c.display_name AS coach_name,
				       w.title AS workout_title, w.level,
				       v.version_number, a.status, a.assigned_on, a.starts_on, a.ends_on, a.notes
				FROM workout_assignment a
				JOIN workout w ON w.id = a.workout_id
				JOIN workout_version v ON v.id = a.workout_version_id
				JOIN client cl ON cl.id = a.client_id
				JOIN person p ON p.id = cl.person_id
				LEFT JOIN coach c ON c.id = a.coach_id
				WHERE a.organization_id = :organizationId
				  AND a.branch_id = :branchId
				  AND a.id = :assignmentId
				  AND (CAST(:ownerUserId AS UUID) IS NULL OR c.user_account_id = CAST(:ownerUserId AS UUID))
				  AND a.deleted_at IS NULL
				""")
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("assignmentId", assignmentId)
				.param("ownerUserId", ownerUserId)
				.query(this::mapAssignment)
				.optional();
	}

	public void replaceAssignment(UUID organizationId, UUID branchId, UUID oldAssignmentId, UUID newAssignmentId) {
		int updated = jdbcClient.sql("""
				UPDATE workout_assignment
				SET status = 'REPLACED', replaced_assignment_id = :newAssignmentId, updated_at = now(), version = version + 1
				WHERE id = :oldAssignmentId AND organization_id = :organizationId
				  AND branch_id = :branchId AND deleted_at IS NULL AND status = 'ACTIVE'
				""")
				.param("newAssignmentId", newAssignmentId)
				.param("oldAssignmentId", oldAssignmentId)
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.update();
		if (updated == 0) throw new com.gymos.common.api.BusinessException("WORKOUT_ASSIGNMENT_CONFLICT", "La asignación cambió. Actualiza el historial antes de continuar.");
	}

	public boolean clientExists(UUID organizationId, UUID clientId) {
		Integer count = jdbcClient.sql("""
				SELECT COUNT(*)
				FROM client
				WHERE organization_id = :organizationId
				  AND id = :clientId
				  AND status = 'ACTIVE'
				  AND deleted_at IS NULL
				""")
				.param("organizationId", organizationId)
				.param("clientId", clientId)
				.query(Integer.class)
				.single();
		return count > 0;
	}

	/** Serializes existing assignment mutations even when the member is inactive. */
	public boolean lockAssignmentClient(UUID organizationId, UUID clientId) {
		return jdbcClient.sql("SELECT id FROM client WHERE organization_id = :organizationId AND id = :clientId FOR UPDATE")
				.param("organizationId", organizationId).param("clientId", clientId).query(UUID.class).optional().isPresent();
	}

	/** New assignments additionally require an active member available in the branch. */
	public boolean lockAssignableClient(UUID organizationId, UUID branchId, UUID clientId) {
		return jdbcClient.sql("""
				SELECT c.id FROM client c JOIN person p ON p.id = c.person_id AND p.organization_id = c.organization_id
				WHERE c.organization_id = :organizationId AND c.id = :clientId
				  AND c.status = 'ACTIVE' AND c.deleted_at IS NULL AND p.deleted_at IS NULL
				  AND (c.home_branch_id IS NULL OR c.home_branch_id = :branchId OR EXISTS (
				      SELECT 1 FROM membership m JOIN membership_plan mp ON mp.id = m.plan_id
				      WHERE m.organization_id = c.organization_id AND m.client_id = c.id
				        AND m.status = 'ACTIVE' AND m.deleted_at IS NULL
				        AND (m.branch_id = :branchId OR mp.multi_branch_access = true)))
				FOR UPDATE OF c
				""").param("organizationId", organizationId).param("branchId", branchId).param("clientId", clientId)
				.query(UUID.class).optional().isPresent();
	}

	public boolean hasOverlappingAssignment(UUID organizationId, UUID branchId, UUID clientId,
			LocalDate startsOn, LocalDate endsOn, UUID excludedAssignmentId) {
		return jdbcClient.sql("""
				SELECT EXISTS (SELECT 1 FROM workout_assignment a
				WHERE a.organization_id = :organizationId AND a.branch_id = :branchId AND a.client_id = :clientId
				  AND a.deleted_at IS NULL AND a.status = 'ACTIVE'
				  AND (CAST(:excludedId AS UUID) IS NULL OR a.id <> CAST(:excludedId AS UUID))
				  AND COALESCE(a.starts_on, a.assigned_on) <= :endsOn
				  AND (a.ends_on IS NULL OR a.ends_on >= :startsOn))
				""").param("organizationId", organizationId).param("branchId", branchId).param("clientId", clientId)
				.param("startsOn", startsOn).param("endsOn", endsOn).param("excludedId", excludedAssignmentId)
				.query(Boolean.class).single();
	}

	public List<ExpiredAssignment> completeDueAssignments(LocalDate today, int batchSize) {
		return jdbcClient.sql("""
				WITH due AS (
				    SELECT id FROM workout_assignment
				    WHERE status = 'ACTIVE' AND ends_on < :today AND deleted_at IS NULL
				    ORDER BY ends_on, id LIMIT :batchSize FOR UPDATE SKIP LOCKED
				)
				UPDATE workout_assignment a
				SET status = 'COMPLETED', status_reason = 'PERIOD_ENDED',
				    completed_at = ((a.ends_on + 1)::timestamp AT TIME ZONE 'America/Mexico_City'),
				    updated_at = now(), updated_by = NULL, version = version + 1
				FROM due WHERE a.id = due.id AND a.status = 'ACTIVE'
				RETURNING a.id, a.organization_id, a.branch_id
				""").param("today", today).param("batchSize", batchSize)
				.query((rs, row) -> new ExpiredAssignment(rs.getObject("id", UUID.class),
						rs.getObject("organization_id", UUID.class), rs.getObject("branch_id", UUID.class))).list();
	}

	public record ExpiredAssignment(UUID id, UUID organizationId, UUID branchId) {}

	public UUID assignWorkout(UUID organizationId, UUID branchId, UUID actorUserId, AssignWorkoutData data) {
		OffsetDateTime now = OffsetDateTime.now();
		UUID assignmentId = UUID.randomUUID();
		jdbcClient.sql("""
				INSERT INTO workout_assignment (
				    id, organization_id, branch_id, workout_id, workout_version_id,
				    client_id, coach_id, status, assigned_on, starts_on, ends_on,
				    notes, printable_notes, idempotency_key, request_hash,
				    created_at, updated_at, created_by, updated_by, version
				)
				VALUES (
				    :id, :organizationId, :branchId, :workoutId, :workoutVersionId,
				    :clientId, :coachId, 'ACTIVE', :assignedOn, :startsOn, :endsOn,
				    :notes, :printableNotes, :idempotencyKey, :requestHash,
				    :createdAt, :updatedAt, :createdBy, :updatedBy, 0
				)
				""")
				.param("id", assignmentId)
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("workoutId", data.workoutId())
				.param("workoutVersionId", data.workoutVersionId())
				.param("clientId", data.clientId())
				.param("coachId", data.coachId())
				.param("assignedOn", data.assignedOn())
				.param("startsOn", data.startsOn())
				.param("endsOn", data.endsOn())
				.param("notes", data.notes())
				.param("printableNotes", data.printableNotes())
				.param("idempotencyKey", data.idempotencyKey())
				.param("requestHash", data.requestHash())
				.param("createdAt", now)
				.param("updatedAt", now)
				.param("createdBy", actorUserId)
				.param("updatedBy", actorUserId)
				.update();
		return assignmentId;
	}

	public Optional<IdempotentAssignment> assignmentByIdempotencyKey(UUID organizationId, String key) {
		return jdbcClient.sql("""
				SELECT id, request_hash FROM workout_assignment
				WHERE organization_id = :organizationId AND idempotency_key = :key
				  AND deleted_at IS NULL
				""")
				.param("organizationId", organizationId)
				.param("key", key)
				.query((resultSet, rowNumber) -> new IdempotentAssignment(
						resultSet.getObject("id", UUID.class), resultSet.getString("request_hash")
				)).optional();
	}

	public List<WorkoutAssignmentRecord> clientAssignments(
			UUID organizationId, UUID branchId, UUID clientId, UUID ownerUserId
	) {
		return jdbcClient.sql("""
				SELECT a.id, a.workout_id, a.workout_version_id, a.client_id,
				       p.first_name || ' ' || p.last_name AS client_name,
				       a.coach_id, c.display_name AS coach_name,
				       w.title AS workout_title, w.level,
				       v.version_number, a.status, a.assigned_on, a.starts_on, a.ends_on, a.notes
				FROM workout_assignment a
				JOIN workout w ON w.id = a.workout_id
				JOIN workout_version v ON v.id = a.workout_version_id
				JOIN client cl ON cl.id = a.client_id
				JOIN person p ON p.id = cl.person_id
				LEFT JOIN coach c ON c.id = a.coach_id
				WHERE a.organization_id = :organizationId
				  AND a.branch_id = :branchId
				  AND a.client_id = :clientId
				  AND (CAST(:ownerUserId AS UUID) IS NULL OR c.user_account_id = CAST(:ownerUserId AS UUID))
				  AND a.deleted_at IS NULL
				ORDER BY a.assigned_on DESC, a.created_at DESC
				""")
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("clientId", clientId)
				.param("ownerUserId", ownerUserId)
				.query(this::mapAssignment)
				.list();
	}

	public Optional<WorkoutAssignmentRecord> assignment(
			UUID organizationId, UUID branchId, UUID assignmentId, UUID ownerUserId
	) {
		return jdbcClient.sql("""
				SELECT a.id, a.workout_id, a.workout_version_id, a.client_id,
				       p.first_name || ' ' || p.last_name AS client_name,
				       a.coach_id, c.display_name AS coach_name,
				       w.title AS workout_title, w.level,
				       v.version_number, a.status, a.assigned_on, a.starts_on, a.ends_on, a.notes
				FROM workout_assignment a
				JOIN workout w ON w.id = a.workout_id
				JOIN workout_version v ON v.id = a.workout_version_id
				JOIN client cl ON cl.id = a.client_id
				JOIN person p ON p.id = cl.person_id
				LEFT JOIN coach c ON c.id = a.coach_id
				WHERE a.organization_id = :organizationId
				  AND a.branch_id = :branchId
				  AND a.id = :assignmentId
				  AND (CAST(:ownerUserId AS UUID) IS NULL OR c.user_account_id = CAST(:ownerUserId AS UUID))
				  AND a.deleted_at IS NULL
				""")
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("assignmentId", assignmentId)
				.param("ownerUserId", ownerUserId)
				.query(this::mapAssignment)
				.optional();
	}

	public CoachReportRecord coachReport(UUID organizationId, UUID branchId) {
		return jdbcClient.sql("""
				SELECT
				    (SELECT COUNT(*) FROM coach WHERE organization_id = :organizationId AND branch_id = :branchId
				      AND status = 'ACTIVE' AND deleted_at IS NULL) AS coach_count,
				    (SELECT COUNT(*) FROM workout WHERE organization_id = :organizationId AND branch_id = :branchId
				      AND status <> 'ARCHIVED' AND deleted_at IS NULL) AS workout_count,
				    COUNT(*) FILTER (WHERE a.status = 'ACTIVE' AND COALESCE(a.starts_on, a.assigned_on) <= :today
				      AND (a.ends_on IS NULL OR a.ends_on >= :today)) AS active_assignments,
				    COUNT(*) FILTER (WHERE a.status <> 'CANCELLED' AND a.assigned_on BETWEEN :fromDate AND :today) AS assignments_last_30_days
				FROM workout_assignment a
				WHERE a.organization_id = :organizationId AND a.branch_id = :branchId AND a.deleted_at IS NULL
				""")
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("today", BusinessTime.today()).param("fromDate", BusinessTime.today().minusDays(29))
				.query((resultSet, rowNumber) -> new CoachReportRecord(
						resultSet.getLong("coach_count"),
						resultSet.getLong("workout_count"),
						resultSet.getLong("active_assignments"),
						resultSet.getLong("assignments_last_30_days")
				))
				.single();
	}

	private CoachRecord mapCoach(java.sql.ResultSet resultSet, int rowNumber) throws java.sql.SQLException {
		return new CoachRecord(
				resultSet.getObject("id", UUID.class),
				resultSet.getObject("user_account_id", UUID.class),
				resultSet.getString("display_name"),
				resultSet.getString("email"),
				resultSet.getString("phone"),
				resultSet.getString("specialties_json"),
				resultSet.getString("bio"),
				resultSet.getString("status"),
				resultSet.getObject("created_at", OffsetDateTime.class),
				resultSet.getObject("updated_at", OffsetDateTime.class)
		);
	}

	private WorkoutSummaryRecord mapWorkoutSummary(java.sql.ResultSet resultSet, int rowNumber) throws java.sql.SQLException {
		return new WorkoutSummaryRecord(
				resultSet.getObject("id", UUID.class),
				resultSet.getObject("coach_id", UUID.class),
				resultSet.getString("coach_name"),
				resultSet.getString("title"),
				resultSet.getString("goal"),
				resultSet.getString("level"),
				resultSet.getString("status"),
				resultSet.getObject("latest_version_id", UUID.class),
				resultSet.getObject("latest_version_number", Integer.class),
				resultSet.getObject("created_at", OffsetDateTime.class),
				resultSet.getObject("updated_at", OffsetDateTime.class)
		);
	}

	private WorkoutRecord mapWorkout(java.sql.ResultSet resultSet, int rowNumber) throws java.sql.SQLException {
		return new WorkoutRecord(
				resultSet.getObject("id", UUID.class),
				resultSet.getObject("coach_id", UUID.class),
				resultSet.getString("coach_name"),
				resultSet.getString("title"),
				resultSet.getString("goal"),
				resultSet.getString("level"),
				resultSet.getString("status"),
				resultSet.getObject("created_at", OffsetDateTime.class),
				resultSet.getObject("updated_at", OffsetDateTime.class)
		);
	}

	private WorkoutVersionRecord mapVersion(java.sql.ResultSet resultSet, int rowNumber) throws java.sql.SQLException {
		return new WorkoutVersionRecord(
				resultSet.getObject("id", UUID.class),
				resultSet.getObject("workout_id", UUID.class),
				resultSet.getInt("version_number"),
				resultSet.getString("summary"),
				resultSet.getString("content_json"),
				resultSet.getString("status"),
				resultSet.getObject("created_at", OffsetDateTime.class)
		);
	}

	private WorkoutAssignmentRecord mapAssignment(java.sql.ResultSet resultSet, int rowNumber) throws java.sql.SQLException {
		return new WorkoutAssignmentRecord(
				resultSet.getObject("id", UUID.class),
				resultSet.getObject("workout_id", UUID.class),
				resultSet.getObject("workout_version_id", UUID.class),
				resultSet.getObject("client_id", UUID.class),
				resultSet.getString("client_name"),
				resultSet.getObject("coach_id", UUID.class),
				resultSet.getString("coach_name"),
				resultSet.getString("workout_title"),
				resultSet.getString("level"),
				resultSet.getInt("version_number"),
				resultSet.getString("status"),
				resultSet.getObject("assigned_on", LocalDate.class),
				resultSet.getObject("starts_on", LocalDate.class),
				resultSet.getObject("ends_on", LocalDate.class),
				resultSet.getString("notes")
		);
	}

	public record CreateCoachData(String displayName, String email, String phone, String specialties, String bio) {
	}

	public record CreateWorkoutData(UUID coachId, String title, String goal, String level) {
	}

	public record CreateWorkoutVersionData(String summary, String contentJson) {
	}

	public record AssignWorkoutData(
			UUID workoutId, UUID workoutVersionId, UUID clientId, UUID coachId,
			LocalDate assignedOn, LocalDate startsOn, LocalDate endsOn, String notes,
			String printableNotes, String idempotencyKey, String requestHash
	) {
	}

	public record IdempotentAssignment(UUID assignmentId, String requestHash) {}

	public record CoachRecord(
			UUID id, UUID userAccountId, String displayName, String email, String phone,
			String specialtiesJson, String bio, String status,
			OffsetDateTime createdAt, OffsetDateTime updatedAt
	) {
	}

	public record CoachProfileRow(
			UUID userId, String displayName, String email, String phone,
			String specialtiesJson, String bio, String status,
			UUID branchId, String branchName
	) {
	}

	public record CoachIdentity(UUID userId, String displayName, String email, String phone) {
	}

	public record WorkoutSummaryRecord(UUID id, UUID coachId, String coachName, String title, String goal, String level, String status, UUID latestVersionId, Integer latestVersionNumber, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
	}

	public record WorkoutRecord(UUID id, UUID coachId, String coachName, String title, String goal, String level, String status, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
	}

	public record WorkoutVersionRecord(UUID id, UUID workoutId, int versionNumber, String summary, String contentJson, String status, OffsetDateTime createdAt) {
	}

	public record WorkoutAssignmentRecord(UUID id, UUID workoutId, UUID workoutVersionId, UUID clientId, String clientName, UUID coachId, String coachName, String workoutTitle, String level, int versionNumber, String status, LocalDate assignedOn, LocalDate startsOn, LocalDate endsOn, String notes) {
		public WorkoutAssignmentRecord {
			status = AssignmentLifecycle.effectiveStatus(status, startsOn, endsOn, BusinessTime.today());
		}
	}

	public record WorkoutDetailRecord(WorkoutRecord workout, List<WorkoutVersionRecord> versions) {
	}

	public record CoachReportRecord(long coachCount, long workoutCount, long activeAssignments, long assignmentsLast30Days) {
	}
}
