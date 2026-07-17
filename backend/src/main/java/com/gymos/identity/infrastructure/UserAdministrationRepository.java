package com.gymos.identity.infrastructure;

import com.gymos.identity.application.result.InternalUserSummary;
import com.gymos.identity.application.result.RoleOption;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class UserAdministrationRepository {
	private final JdbcClient jdbcClient;

	public UserAdministrationRepository(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	public List<RoleOption> findRoles(UUID organizationId) {
		return jdbcClient.sql("""
				SELECT r.id, r.name, r.description, r.system_role, COUNT(rp.permission_id) AS permission_count
				FROM role r
				LEFT JOIN role_permission rp ON rp.role_id = r.id
				WHERE r.deleted_at IS NULL
				  AND (r.organization_id = :organizationId OR r.system_role = TRUE)
				GROUP BY r.id, r.name, r.description, r.system_role
				ORDER BY CASE r.name
				    WHEN 'Administrador GymOS' THEN 1
				    WHEN 'Administrador' THEN 2
				    WHEN 'Dueno / Gerencia' THEN 3
				    WHEN 'Recepcion / Caja' THEN 4
				    WHEN 'Coach' THEN 5
				    WHEN 'Operacion' THEN 6
				    ELSE 99
				END, r.name
				""")
				.param("organizationId", organizationId)
				.query((resultSet, rowNumber) -> new RoleOption(
						resultSet.getObject("id", UUID.class),
						resultSet.getString("name"),
						resultSet.getString("description"),
						resultSet.getBoolean("system_role"),
						resultSet.getInt("permission_count")
				))
				.list();
	}

	public boolean roleBelongsToOrganization(UUID organizationId, UUID roleId) {
		Integer count = jdbcClient.sql("""
				SELECT COUNT(*)
				FROM role
				WHERE id = :roleId
				  AND deleted_at IS NULL
				  AND (organization_id = :organizationId OR system_role = TRUE)
				""")
				.param("roleId", roleId)
				.param("organizationId", organizationId)
				.query(Integer.class)
				.single();
		return count > 0;
	}

	public boolean branchBelongsToOrganization(UUID organizationId, UUID branchId) {
		Integer count = jdbcClient.sql("""
				SELECT COUNT(*)
				FROM branch
				WHERE id = :branchId
				  AND organization_id = :organizationId
				  AND deleted_at IS NULL
				""")
				.param("branchId", branchId)
				.param("organizationId", organizationId)
				.query(Integer.class)
				.single();
		return count > 0;
	}

	public UUID createPerson(
			UUID organizationId,
			String firstName,
			String lastName,
			String email,
			String phone,
			UUID actorUserId,
			OffsetDateTime now
	) throws DuplicateKeyException {
		UUID personId = UUID.randomUUID();
		jdbcClient.sql("""
				INSERT INTO person (
				    id, organization_id, first_name, last_name, email, phone, status,
				    created_at, updated_at, created_by, updated_by, version
				)
				VALUES (
				    :id, :organizationId, :firstName, :lastName, :email, :phone, 'ACTIVE',
				    :createdAt, :updatedAt, :createdBy, :updatedBy, 0
				)
				""")
				.param("id", personId)
				.param("organizationId", organizationId)
				.param("firstName", firstName)
				.param("lastName", lastName)
				.param("email", email)
				.param("phone", phone)
				.param("createdAt", now)
				.param("updatedAt", now)
				.param("createdBy", actorUserId)
				.param("updatedBy", actorUserId)
				.update();
		return personId;
	}

	public UUID createUser(
			UUID organizationId,
			UUID personId,
			String username,
			String passwordHash,
			UUID actorUserId,
			OffsetDateTime now
	) throws DuplicateKeyException {
		UUID userId = UUID.randomUUID();
		jdbcClient.sql("""
				INSERT INTO user_account (
				    id, organization_id, person_id, username, password_hash, status, failed_attempts,
				    created_at, updated_at, created_by, updated_by, version
				)
				VALUES (
				    :id, :organizationId, :personId, :username, :passwordHash, 'ACTIVE', 0,
				    :createdAt, :updatedAt, :createdBy, :updatedBy, 0
				)
				""")
				.param("id", userId)
				.param("organizationId", organizationId)
				.param("personId", personId)
				.param("username", username)
				.param("passwordHash", passwordHash)
				.param("createdAt", now)
				.param("updatedAt", now)
				.param("createdBy", actorUserId)
				.param("updatedBy", actorUserId)
				.update();
		return userId;
	}

	public void assignRole(UUID userId, UUID roleId) {
		jdbcClient.sql("INSERT INTO user_role (user_id, role_id) VALUES (:userId, :roleId) ON CONFLICT DO NOTHING")
				.param("userId", userId)
				.param("roleId", roleId)
				.update();
	}

	public void assignBranch(UUID userId, UUID branchId, String accessScope) {
		jdbcClient.sql("""
				INSERT INTO user_branch_access (user_id, branch_id, access_scope)
				VALUES (:userId, :branchId, :accessScope)
				ON CONFLICT (user_id, branch_id) DO UPDATE SET access_scope = EXCLUDED.access_scope
				""")
				.param("userId", userId)
				.param("branchId", branchId)
				.param("accessScope", accessScope)
				.update();
	}

	public List<InternalUserSummary> findUsers(UUID organizationId) {
		return jdbcClient.sql("""
				SELECT
				    u.id,
				    CONCAT(p.first_name, ' ', p.last_name) AS full_name,
				    u.username,
				    p.email,
				    p.phone,
				    u.status,
				    COALESCE(array_remove(array_agg(DISTINCT r.name ORDER BY r.name), NULL), ARRAY[]::text[]) AS roles,
				    COALESCE(array_remove(array_agg(DISTINCT b.name ORDER BY b.name), NULL), ARRAY[]::text[]) AS branches
				FROM user_account u
				JOIN person p ON p.id = u.person_id
				LEFT JOIN user_role ur ON ur.user_id = u.id
				LEFT JOIN role r ON r.id = ur.role_id
				LEFT JOIN user_branch_access uba ON uba.user_id = u.id
				LEFT JOIN branch b ON b.id = uba.branch_id AND b.deleted_at IS NULL
				WHERE u.organization_id = :organizationId
				  AND u.deleted_at IS NULL
				GROUP BY u.id, p.first_name, p.last_name, u.username, p.email, p.phone, u.status
				ORDER BY p.first_name, p.last_name, u.username
				""")
				.param("organizationId", organizationId)
				.query((resultSet, rowNumber) -> new InternalUserSummary(
						resultSet.getObject("id", UUID.class),
						resultSet.getString("full_name"),
						resultSet.getString("username"),
						resultSet.getString("email"),
						resultSet.getString("phone"),
						resultSet.getString("status"),
						List.of((String[]) resultSet.getArray("roles").getArray()),
						List.of((String[]) resultSet.getArray("branches").getArray())
				))
				.list();
	}
}
