package com.gymos.coaching.infrastructure;

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
				SELECT id, display_name, email, phone, specialties_json::text AS specialties_json,
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

	public Optional<CoachRecord> coach(UUID organizationId, UUID branchId, UUID coachId) {
		return jdbcClient.sql("""
				SELECT id, display_name, email, phone, specialties_json::text AS specialties_json,
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

	public List<WorkoutSummaryRecord> workouts(UUID organizationId, UUID branchId) {
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
				    ORDER BY version_number DESC
				    LIMIT 1
				) v ON true
				WHERE w.organization_id = :organizationId
				  AND w.branch_id = :branchId
				  AND w.deleted_at IS NULL
				ORDER BY w.updated_at DESC, w.title
				""")
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.query(this::mapWorkoutSummary)
				.list();
	}

	public Optional<WorkoutRecord> workout(UUID organizationId, UUID branchId, UUID workoutId) {
		return jdbcClient.sql("""
				SELECT w.id, w.coach_id, c.display_name AS coach_name, w.title, w.goal, w.level,
				       w.status, w.created_at, w.updated_at
				FROM workout w
				LEFT JOIN coach c ON c.id = w.coach_id
				WHERE w.organization_id = :organizationId
				  AND w.branch_id = :branchId
				  AND w.id = :workoutId
				  AND w.deleted_at IS NULL
				""")
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("workoutId", workoutId)
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
			UUID organizationId, UUID branchId, UUID assignmentId, String status, UUID actorUserId
	) {
		int updated = jdbcClient.sql("""
				UPDATE workout_assignment
				SET status = :status, updated_at = now(), updated_by = :actorUserId,
				    completed_at = CASE WHEN :status = 'COMPLETED' THEN now() ELSE completed_at END,
				    cancelled_at = CASE WHEN :status = 'CANCELLED' THEN now() ELSE cancelled_at END,
				    version = version + 1
				WHERE id = :assignmentId AND organization_id = :organizationId
				  AND branch_id = :branchId AND deleted_at IS NULL
				""")
				.param("status", status)
				.param("actorUserId", actorUserId)
				.param("assignmentId", assignmentId)
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.update();
		if (updated == 0) throw new IllegalStateException("Assignment not found");
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

	public List<WorkoutAssignmentRecord> clientAssignments(UUID organizationId, UUID branchId, UUID clientId) {
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
				  AND a.deleted_at IS NULL
				ORDER BY a.assigned_on DESC, a.created_at DESC
				""")
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("clientId", clientId)
				.query(this::mapAssignment)
				.list();
	}

	public Optional<WorkoutAssignmentRecord> assignment(UUID organizationId, UUID branchId, UUID assignmentId) {
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
				  AND a.deleted_at IS NULL
				""")
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("assignmentId", assignmentId)
				.query(this::mapAssignment)
				.optional();
	}

	public CoachReportRecord coachReport(UUID organizationId, UUID branchId) {
		return jdbcClient.sql("""
				SELECT
				    COUNT(DISTINCT c.id) AS coach_count,
				    COUNT(DISTINCT w.id) AS workout_count,
				    COUNT(a.id) AS active_assignments,
				    COALESCE(SUM(CASE WHEN a.assigned_on >= CURRENT_DATE - INTERVAL '30 days' THEN 1 ELSE 0 END), 0) AS assignments_last_30_days
				FROM coach c
				LEFT JOIN workout w ON w.coach_id = c.id AND w.deleted_at IS NULL
				LEFT JOIN workout_assignment a ON a.coach_id = c.id AND a.status = 'ACTIVE' AND a.deleted_at IS NULL
				WHERE c.organization_id = :organizationId
				  AND c.branch_id = :branchId
				  AND c.deleted_at IS NULL
				""")
				.param("organizationId", organizationId)
				.param("branchId", branchId)
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

	public record CoachRecord(UUID id, String displayName, String email, String phone, String specialtiesJson, String bio, String status, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
	}

	public record WorkoutSummaryRecord(UUID id, UUID coachId, String coachName, String title, String goal, String level, String status, UUID latestVersionId, Integer latestVersionNumber, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
	}

	public record WorkoutRecord(UUID id, UUID coachId, String coachName, String title, String goal, String level, String status, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
	}

	public record WorkoutVersionRecord(UUID id, UUID workoutId, int versionNumber, String summary, String contentJson, String status, OffsetDateTime createdAt) {
	}

	public record WorkoutAssignmentRecord(UUID id, UUID workoutId, UUID workoutVersionId, UUID clientId, String clientName, UUID coachId, String coachName, String workoutTitle, String level, int versionNumber, String status, LocalDate assignedOn, LocalDate startsOn, LocalDate endsOn, String notes) {
	}

	public record WorkoutDetailRecord(WorkoutRecord workout, List<WorkoutVersionRecord> versions) {
	}

	public record CoachReportRecord(long coachCount, long workoutCount, long activeAssignments, long assignmentsLast30Days) {
	}
}
