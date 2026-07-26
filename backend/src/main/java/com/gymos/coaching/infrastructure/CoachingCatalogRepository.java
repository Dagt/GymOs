package com.gymos.coaching.infrastructure;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class CoachingCatalogRepository {
	private final JdbcClient jdbc;

	public CoachingCatalogRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	public ExercisePage exercises(
			UUID organizationId, String query, String muscle, String equipment, String type, int page, int size
	) {
		String term = query == null ? "" : query.trim().toLowerCase();
		String pattern = "%" + term + "%";
		String filters = """
				FROM exercise e
				WHERE (e.organization_id IS NULL OR e.organization_id = :organizationId)
				  AND e.status = 'ACTIVE'
				  AND (:term = '' OR e.normalized_name LIKE :pattern OR EXISTS (
				      SELECT 1 FROM exercise_alias a
				      WHERE a.exercise_id = e.id AND a.normalized_alias LIKE :pattern
				  ))
				  AND (:muscle = '' OR lower(COALESCE(e.primary_muscle_group, '')) = lower(:muscle))
				  AND (:equipment = '' OR lower(COALESCE(e.equipment, '')) = lower(:equipment))
				  AND (:type = '' OR e.exercise_type = upper(:type))
				""";
		var countQuery = jdbc.sql("SELECT COUNT(*) " + filters);
		bindFilters(countQuery, organizationId, term, pattern, muscle, equipment, type);
		long total = countQuery.query(Long.class).single();
		var dataQuery = jdbc.sql("""
				SELECT e.id, e.organization_id, e.name, e.description, e.instructions,
				       e.primary_muscle_group, e.secondary_muscle_groups, e.movement_pattern,
				       e.exercise_type, e.equipment, e.suggested_level, e.laterality,
				       e.media_url, e.warnings, e.origin, e.status, e.created_by, e.created_at, e.updated_at
				""" + filters + " ORDER BY e.name LIMIT :size OFFSET :offset");
		bindFilters(dataQuery, organizationId, term, pattern, muscle, equipment, type);
		List<ExerciseRecord> items = dataQuery
				.param("size", size)
				.param("offset", page * size)
				.query(this::mapExercise)
				.list();
		return new ExercisePage(items, page, size, total, (int) Math.ceil(total / (double) size));
	}

	private void bindFilters(
			JdbcClient.StatementSpec spec, UUID organizationId, String term, String pattern,
			String muscle, String equipment, String type
	) {
		spec.param("organizationId", organizationId)
				.param("term", term)
				.param("pattern", pattern)
				.param("muscle", clean(muscle))
				.param("equipment", clean(equipment))
				.param("type", clean(type));
	}

	public Optional<ExerciseRecord> exercise(UUID organizationId, UUID exerciseId) {
		return jdbc.sql("""
				SELECT id, organization_id, name, description, instructions, primary_muscle_group,
				       secondary_muscle_groups, movement_pattern, exercise_type, equipment,
				       suggested_level, laterality, media_url, warnings, origin, status,
				       created_by, created_at, updated_at
				FROM exercise
				WHERE id = :id AND (organization_id IS NULL OR organization_id = :organizationId)
				""")
				.param("id", exerciseId)
				.param("organizationId", organizationId)
				.query(this::mapExercise)
				.optional();
	}

	public Optional<ExerciseRecord> activeByNormalizedName(UUID organizationId, String normalizedName) {
		return jdbc.sql("""
				SELECT id, organization_id, name, description, instructions, primary_muscle_group,
				       secondary_muscle_groups, movement_pattern, exercise_type, equipment,
				       suggested_level, laterality, media_url, warnings, origin, status,
				       created_by, created_at, updated_at
				FROM exercise
				WHERE normalized_name = :name
				  AND status = 'ACTIVE'
				  AND (organization_id IS NULL OR organization_id = :organizationId)
				ORDER BY organization_id NULLS LAST LIMIT 1
				""")
				.param("name", normalizedName)
				.param("organizationId", organizationId)
				.query(this::mapExercise)
				.optional();
	}

	public Optional<ExerciseRecord> activeByNormalizedAlias(UUID organizationId, String normalizedAlias) {
		return jdbc.sql("""
				SELECT e.id, e.organization_id, e.name, e.description, e.instructions,
				       e.primary_muscle_group, e.secondary_muscle_groups, e.movement_pattern,
				       e.exercise_type, e.equipment, e.suggested_level, e.laterality,
				       e.media_url, e.warnings, e.origin, e.status, e.created_by, e.created_at, e.updated_at
				FROM exercise_alias a
				JOIN exercise e ON e.id = a.exercise_id
				WHERE a.normalized_alias = :alias
				  AND e.status = 'ACTIVE'
				  AND (e.organization_id IS NULL OR e.organization_id = :organizationId)
				ORDER BY e.organization_id NULLS LAST LIMIT 1
				""")
				.param("alias", normalizedAlias)
				.param("organizationId", organizationId)
				.query(this::mapExercise)
				.optional();
	}

	public UUID createExercise(UUID organizationId, UUID userId, ExerciseData data) {
		UUID id = UUID.randomUUID();
		jdbc.sql("""
				INSERT INTO exercise (
				    id, organization_id, name, normalized_name, description, instructions,
				    primary_muscle_group, secondary_muscle_groups, movement_pattern,
				    exercise_type, equipment, suggested_level, laterality, media_url,
				    warnings, origin, created_by, status, created_at, updated_at
				) VALUES (
				    :id, :organizationId, :name, :normalizedName, :description, :instructions,
				    :primaryMuscle, :secondaryMuscles, :movementPattern, :type, :equipment,
				    :level, :laterality, :mediaUrl, :warnings, :origin, :userId,
				    'ACTIVE', now(), now()
				)
				""")
				.param("id", id)
				.param("organizationId", organizationId)
				.param("name", data.name())
				.param("normalizedName", data.normalizedName())
				.param("description", data.description())
				.param("instructions", data.instructions())
				.param("primaryMuscle", data.primaryMuscleGroup())
				.param("secondaryMuscles", data.secondaryMuscleGroups())
				.param("movementPattern", data.movementPattern())
				.param("type", data.type())
				.param("equipment", data.equipment())
				.param("level", data.suggestedLevel())
				.param("laterality", data.laterality())
				.param("mediaUrl", data.mediaUrl())
				.param("warnings", data.warnings())
				.param("origin", data.origin())
				.param("userId", userId)
				.update();
		return id;
	}

	public void updateExercise(UUID organizationId, UUID id, ExerciseData data) {
		jdbc.sql("""
				UPDATE exercise SET
				    name = :name, normalized_name = :normalizedName, description = :description,
				    instructions = :instructions, primary_muscle_group = :primaryMuscle,
				    secondary_muscle_groups = :secondaryMuscles, movement_pattern = :movementPattern,
				    exercise_type = :type, equipment = :equipment, suggested_level = :level,
				    laterality = :laterality, media_url = :mediaUrl, warnings = :warnings,
				    updated_at = now()
				WHERE id = :id AND organization_id = :organizationId
				""")
				.param("name", data.name())
				.param("normalizedName", data.normalizedName())
				.param("description", data.description())
				.param("instructions", data.instructions())
				.param("primaryMuscle", data.primaryMuscleGroup())
				.param("secondaryMuscles", data.secondaryMuscleGroups())
				.param("movementPattern", data.movementPattern())
				.param("type", data.type())
				.param("equipment", data.equipment())
				.param("level", data.suggestedLevel())
				.param("laterality", data.laterality())
				.param("mediaUrl", data.mediaUrl())
				.param("warnings", data.warnings())
				.param("id", id)
				.param("organizationId", organizationId)
				.update();
	}

	public void addAlias(UUID exerciseId, String alias, String normalizedAlias) {
		jdbc.sql("""
				INSERT INTO exercise_alias (id, exercise_id, alias, normalized_alias, created_at)
				VALUES (:id, :exerciseId, :alias, :normalizedAlias, now())
				ON CONFLICT (exercise_id, normalized_alias) DO NOTHING
				""")
				.param("id", UUID.randomUUID())
				.param("exerciseId", exerciseId)
				.param("alias", alias)
				.param("normalizedAlias", normalizedAlias)
				.update();
	}

	public boolean exerciseCreatedBy(UUID organizationId, UUID exerciseId, UUID userId) {
		Integer count = jdbc.sql("""
				SELECT COUNT(*)
				FROM exercise
				WHERE id = :exerciseId AND organization_id = :organizationId
				  AND created_by = :userId
				""")
				.param("exerciseId", exerciseId)
				.param("organizationId", organizationId)
				.param("userId", userId)
				.query(Integer.class)
				.single();
		return count > 0;
	}

	public List<String> aliases(UUID exerciseId) {
		return jdbc.sql("SELECT alias FROM exercise_alias WHERE exercise_id = :exerciseId ORDER BY alias")
				.param("exerciseId", exerciseId)
				.query(String.class)
				.list();
	}

	public void deleteAlias(UUID exerciseId, String normalizedAlias) {
		jdbc.sql("""
				DELETE FROM exercise_alias
				WHERE exercise_id = :exerciseId AND normalized_alias = :normalizedAlias
				""")
				.param("exerciseId", exerciseId)
				.param("normalizedAlias", normalizedAlias)
				.update();
	}

	public void updateExerciseStatus(UUID organizationId, UUID id, String status) {
		jdbc.sql("""
				UPDATE exercise SET status = :status, archived_at =
				    CASE WHEN :status = 'ARCHIVED' THEN now() ELSE NULL END, updated_at = now()
				WHERE id = :id AND organization_id = :organizationId
				""")
				.param("status", status)
				.param("id", id)
				.param("organizationId", organizationId)
				.update();
	}

	public void updateExerciseMedia(UUID organizationId, UUID exerciseId, String mediaReference) {
		jdbc.sql("""
				UPDATE exercise
				SET media_url = :mediaReference, updated_at = now()
				WHERE id = :exerciseId AND organization_id = :organizationId
				""")
				.param("mediaReference", mediaReference)
				.param("exerciseId", exerciseId)
				.param("organizationId", organizationId)
				.update();
	}

	public List<AssignmentDetail> assignments(
			UUID organizationId, UUID branchId, LocalDate from, LocalDate to,
			UUID coachId, UUID clientId, UUID ownerUserId, int size, int offset
	) {
		return jdbc.sql("""
				SELECT a.id, a.assigned_on, a.status, a.status_reason, a.replaced_assignment_id,
				       a.starts_on, a.ends_on,
				       a.workout_id, w.title AS workout_title, v.version_number,
				       a.client_id, trim(concat(p.first_name, ' ', p.last_name, ' ', coalesce(p.maternal_last_name, ''))) AS client_name,
				       a.coach_id, c.display_name AS coach_name, a.printable_notes, a.notes
				FROM workout_assignment a
				JOIN workout w ON w.id = a.workout_id
				JOIN workout_version v ON v.id = a.workout_version_id
				JOIN client cl ON cl.id = a.client_id
				JOIN person p ON p.id = cl.person_id
				LEFT JOIN coach c ON c.id = a.coach_id
				WHERE a.organization_id = :organizationId
				  AND a.branch_id = :branchId
				  AND a.deleted_at IS NULL
				  AND a.assigned_on BETWEEN :fromDate AND :toDate
				  AND (:coachId IS NULL OR a.coach_id = :coachId)
				  AND (:clientId IS NULL OR a.client_id = :clientId)
				  AND (:ownerUserId IS NULL OR c.user_account_id = :ownerUserId)
				ORDER BY a.assigned_on DESC, a.created_at DESC
				LIMIT :size OFFSET :offset
				""")
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("fromDate", from)
				.param("toDate", to)
				.param("coachId", coachId)
				.param("clientId", clientId)
				.param("ownerUserId", ownerUserId)
				.param("size", size)
				.param("offset", offset)
				.query((rs, row) -> new AssignmentDetail(
						rs.getObject("id", UUID.class), rs.getObject("assigned_on", LocalDate.class),
						rs.getString("status"), rs.getString("status_reason"),
						rs.getObject("replaced_assignment_id", UUID.class),
						rs.getObject("starts_on", LocalDate.class),
						rs.getObject("ends_on", LocalDate.class), rs.getObject("workout_id", UUID.class),
						rs.getString("workout_title"), rs.getInt("version_number"),
						rs.getObject("client_id", UUID.class), rs.getString("client_name"),
						rs.getObject("coach_id", UUID.class), rs.getString("coach_name"),
						rs.getString("printable_notes"), rs.getString("notes")
				)).list();
	}

	public AssignmentStats assignmentStats(
			UUID organizationId, UUID branchId, LocalDate from, LocalDate to,
			UUID coachId, UUID clientId, UUID ownerUserId
	) {
		return jdbc.sql("""
				SELECT COUNT(a.id) AS total_rows,
				       COUNT(a.id) FILTER (WHERE a.status <> 'CANCELLED') AS assignments,
				       COUNT(DISTINCT a.client_id) FILTER (WHERE a.status <> 'CANCELLED') AS unique_clients
				FROM workout_assignment a
				LEFT JOIN coach c ON c.id = a.coach_id
				WHERE a.organization_id = :organizationId
				  AND a.branch_id = :branchId
				  AND a.deleted_at IS NULL
				  AND a.assigned_on BETWEEN :fromDate AND :toDate
				  AND (:coachId IS NULL OR a.coach_id = :coachId)
				  AND (:clientId IS NULL OR a.client_id = :clientId)
				  AND (:ownerUserId IS NULL OR c.user_account_id = :ownerUserId)
				""")
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("fromDate", from)
				.param("toDate", to)
				.param("coachId", coachId)
				.param("clientId", clientId)
				.param("ownerUserId", ownerUserId)
				.query((rs, row) -> new AssignmentStats(
						rs.getLong("total_rows"),
						rs.getLong("assignments"),
						rs.getLong("unique_clients")
				)).single();
	}

	public List<CoachMetric> metrics(
			UUID organizationId, UUID branchId, LocalDate from, LocalDate to,
			UUID coachId, UUID clientId, UUID ownerUserId
	) {
		return jdbc.sql("""
				SELECT c.id AS coach_id, c.display_name AS coach_name,
				       COUNT(a.id) FILTER (WHERE a.status <> 'CANCELLED') AS assignments,
				       COUNT(DISTINCT a.client_id) FILTER (WHERE a.status <> 'CANCELLED') AS unique_clients,
				       COUNT(a.id) FILTER (WHERE a.status <> 'CANCELLED' AND NOT EXISTS (
				           SELECT 1 FROM workout_assignment previous
				           WHERE previous.organization_id = a.organization_id
				             AND previous.client_id = a.client_id
				             AND previous.assigned_on < a.assigned_on
				             AND previous.status <> 'CANCELLED'
				             AND previous.deleted_at IS NULL
				       )) AS first_workouts
				FROM coach c
				LEFT JOIN workout_assignment a ON a.coach_id = c.id
				  AND a.assigned_on BETWEEN :fromDate AND :toDate AND a.deleted_at IS NULL
				  AND (:clientId IS NULL OR a.client_id = :clientId)
				WHERE c.organization_id = :organizationId AND c.branch_id = :branchId
				  AND c.deleted_at IS NULL AND c.status = 'ACTIVE'
				  AND (:coachId IS NULL OR c.id = :coachId)
				  AND (:ownerUserId IS NULL OR c.user_account_id = :ownerUserId)
				GROUP BY c.id, c.display_name
				ORDER BY assignments DESC, c.display_name
				""")
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("fromDate", from)
				.param("toDate", to)
				.param("coachId", coachId)
				.param("clientId", clientId)
				.param("ownerUserId", ownerUserId)
				.query((rs, row) -> new CoachMetric(
						rs.getObject("coach_id", UUID.class), rs.getString("coach_name"),
						rs.getLong("assignments"), rs.getLong("unique_clients"),
						rs.getLong("first_workouts")
				)).list();
	}

	public Optional<AssignmentPdfData> assignmentPdf(
			UUID organizationId, UUID branchId, UUID assignmentId, UUID ownerUserId
	) {
		return jdbc.sql("""
				SELECT a.id, a.assigned_on, a.starts_on, a.ends_on, a.printable_notes,
				       w.title, w.goal, w.level, v.version_number, v.content_json::text AS content_json,
				       trim(concat(p.first_name, ' ', p.last_name, ' ', coalesce(p.maternal_last_name, ''))) AS client_name,
				       coalesce(c.display_name, 'Coach') AS coach_name,
				       coalesce(o.brand_name, o.trade_name) AS gym_name,
				       o.logo_url, o.primary_color, b.name AS branch_name,
				       coalesce(b.phone, o.contact_phone) AS contact_phone
				FROM workout_assignment a
				JOIN workout w ON w.id = a.workout_id
				JOIN workout_version v ON v.id = a.workout_version_id
				JOIN client cl ON cl.id = a.client_id
				JOIN person p ON p.id = cl.person_id
				JOIN organization o ON o.id = a.organization_id
				JOIN branch b ON b.id = a.branch_id
				LEFT JOIN coach c ON c.id = a.coach_id
				WHERE a.id = :id AND a.organization_id = :organizationId
				  AND a.branch_id = :branchId AND a.deleted_at IS NULL
				  AND (:ownerUserId IS NULL OR c.user_account_id = :ownerUserId)
				""")
				.param("id", assignmentId)
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("ownerUserId", ownerUserId)
				.query((rs, row) -> new AssignmentPdfData(
						rs.getObject("id", UUID.class), rs.getObject("assigned_on", LocalDate.class),
						rs.getObject("starts_on", LocalDate.class), rs.getObject("ends_on", LocalDate.class),
						rs.getString("printable_notes"), rs.getString("title"), rs.getString("goal"),
						rs.getString("level"), rs.getInt("version_number"), rs.getString("content_json"),
						rs.getString("client_name"), rs.getString("coach_name"), rs.getString("gym_name"),
						rs.getString("logo_url"), rs.getString("primary_color"),
						rs.getString("branch_name"), rs.getString("contact_phone")
				)).optional();
	}

	private ExerciseRecord mapExercise(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
		return new ExerciseRecord(
				rs.getObject("id", UUID.class), rs.getObject("organization_id", UUID.class),
				rs.getString("name"), rs.getString("description"), rs.getString("instructions"),
				rs.getString("primary_muscle_group"), rs.getString("secondary_muscle_groups"),
				rs.getString("movement_pattern"), rs.getString("exercise_type"), rs.getString("equipment"),
				rs.getString("suggested_level"), rs.getString("laterality"), rs.getString("media_url"),
				rs.getString("warnings"), rs.getString("origin"), rs.getString("status"),
				rs.getObject("created_by", UUID.class),
				rs.getObject("created_at", OffsetDateTime.class), rs.getObject("updated_at", OffsetDateTime.class)
		);
	}

	private String clean(String value) {
		return value == null ? "" : value.trim();
	}

	public record ExerciseData(
			String name, String normalizedName, String description, String instructions,
			String primaryMuscleGroup, String secondaryMuscleGroups, String movementPattern,
			String type, String equipment, String suggestedLevel, String laterality,
			String mediaUrl, String warnings, String origin
	) {}
	public record ExerciseRecord(
			UUID id, UUID organizationId, String name, String description, String instructions,
			String primaryMuscleGroup, String secondaryMuscleGroups, String movementPattern,
			String type, String equipment, String suggestedLevel, String laterality,
			String mediaUrl, String warnings, String origin, String status,
			UUID createdBy, OffsetDateTime createdAt, OffsetDateTime updatedAt
	) {}
	public record ExercisePage(List<ExerciseRecord> items, int page, int size, long totalElements, int totalPages) {}
	public record AssignmentDetail(
			UUID id, LocalDate assignedOn, String status, String statusReason, UUID replacedAssignmentId,
			LocalDate startsOn, LocalDate endsOn,
			UUID workoutId, String workoutTitle, int versionNumber, UUID clientId,
			String clientName, UUID coachId, String coachName, String printableNotes, String privateNotes
	) {}
	public record CoachMetric(UUID coachId, String coachName, long assignments, long uniqueClients, long firstWorkouts) {}
	public record AssignmentStats(long totalRows, long assignments, long uniqueClients) {}
	public record AssignmentPdfData(
			UUID id, LocalDate assignedOn, LocalDate startsOn, LocalDate endsOn, String printableNotes,
			String title, String goal, String level, int versionNumber, String contentJson,
			String clientName, String coachName, String gymName, String logoUrl,
			String primaryColor, String branchName, String contactPhone
	) {}
}
