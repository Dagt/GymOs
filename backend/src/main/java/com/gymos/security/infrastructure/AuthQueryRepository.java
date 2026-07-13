package com.gymos.security.infrastructure;

import com.gymos.common.security.GymOsPrincipal;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class AuthQueryRepository {
	private final JdbcClient jdbcClient;

	public AuthQueryRepository(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	public Optional<AuthenticatedUser> findActiveUserByUsername(String username) {
		return findUser("""
				SELECT ua.id, ua.organization_id, ua.username, ua.password_hash,
				       p.first_name || ' ' || p.last_name AS full_name
				FROM user_account ua
				JOIN person p ON p.id = ua.person_id
				WHERE LOWER(ua.username) = LOWER(:username)
				  AND ua.status = 'ACTIVE'
				  AND ua.deleted_at IS NULL
				""", "username", username);
	}

	public Optional<AuthenticatedUser> findActiveUserById(UUID userId) {
		return findUser("""
				SELECT ua.id, ua.organization_id, ua.username, ua.password_hash,
				       p.first_name || ' ' || p.last_name AS full_name
				FROM user_account ua
				JOIN person p ON p.id = ua.person_id
				WHERE ua.id = :userId
				  AND ua.status = 'ACTIVE'
				  AND ua.deleted_at IS NULL
				""", "userId", userId);
	}

	public boolean userHasBranchAccess(UUID userId, UUID branchId) {
		Integer count = jdbcClient.sql("""
				SELECT COUNT(*)
				FROM user_branch_access
				WHERE user_id = :userId
				  AND branch_id = :branchId
				""")
				.param("userId", userId)
				.param("branchId", branchId)
				.query(Integer.class)
				.single();
		return count > 0;
	}

	private Optional<AuthenticatedUser> findUser(String sql, String parameterName, Object parameterValue) {
		return jdbcClient.sql(sql)
				.param(parameterName, parameterValue)
				.query((resultSet, rowNumber) -> new AuthenticatedUser(
						resultSet.getObject("id", UUID.class),
						resultSet.getObject("organization_id", UUID.class),
						resultSet.getString("username"),
						resultSet.getString("password_hash"),
						resultSet.getString("full_name"),
						findPermissions(resultSet.getObject("id", UUID.class))
				))
				.optional();
	}

	private Set<String> findPermissions(UUID userId) {
		return new LinkedHashSet<>(jdbcClient.sql("""
				SELECT DISTINCT p.code
				FROM user_role ur
				JOIN role_permission rp ON rp.role_id = ur.role_id
				JOIN permission p ON p.id = rp.permission_id
				WHERE ur.user_id = :userId
				ORDER BY p.code
				""")
				.param("userId", userId)
				.query(String.class)
				.list());
	}

	public record AuthenticatedUser(
			UUID userId,
			UUID organizationId,
			String username,
			String passwordHash,
			String fullName,
			Set<String> permissions
	) {
		public GymOsPrincipal toPrincipal(UUID branchId) {
			return new GymOsPrincipal(userId, organizationId, branchId, username, fullName, permissions);
		}
	}
}
