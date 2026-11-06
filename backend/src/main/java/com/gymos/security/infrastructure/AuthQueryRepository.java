package com.gymos.security.infrastructure;

import com.gymos.common.security.AccountScope;
import com.gymos.common.security.GymOsPrincipal;
import java.time.Instant;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class AuthQueryRepository {
	private static final String SCOPED_ROLES = """
			FROM user_role ur
			JOIN user_account u ON u.id = ur.user_id
			JOIN role r ON r.id = ur.role_id AND r.account_scope = u.account_scope AND r.deleted_at IS NULL
			 AND (r.organization_id = u.organization_id OR (r.organization_id IS NULL AND r.system_role))
			""";
	private final JdbcClient jdbcClient;

	public AuthQueryRepository(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	public Optional<AuthenticatedUser> findActiveUserByLoginIdentifier(String loginIdentifier) {
		String normalizedIdentifier = loginIdentifier == null ? "" : loginIdentifier.trim();
		Optional<AuthenticatedUser> usernameMatch = findUser("""
				SELECT ua.id, ua.account_scope, ua.organization_id, ua.username, ua.password_hash, ua.credential_version,
				       TRIM(p.first_name || ' ' || p.last_name || ' ' || COALESCE(p.maternal_last_name, '')) AS full_name
				FROM user_account ua
				JOIN person p ON p.id = ua.person_id
				WHERE LOWER(ua.username) = LOWER(:loginIdentifier)
				  AND ua.status = 'ACTIVE'
				  AND ua.deleted_at IS NULL
				  AND p.deleted_at IS NULL
				""", "loginIdentifier", normalizedIdentifier);
		if (usernameMatch.isPresent()) {
			return usernameMatch;
		}

		List<AuthenticatedUser> emailMatches = findUsers("""
				SELECT ua.id, ua.account_scope, ua.organization_id, ua.username, ua.password_hash, ua.credential_version,
				       TRIM(p.first_name || ' ' || p.last_name || ' ' || COALESCE(p.maternal_last_name, '')) AS full_name
				FROM user_account ua
				JOIN person p ON p.id = ua.person_id
				WHERE LOWER(p.email) = LOWER(:loginIdentifier)
				  AND ua.status = 'ACTIVE'
				  AND ua.deleted_at IS NULL
				  AND p.deleted_at IS NULL
				ORDER BY ua.created_at, ua.id
				LIMIT 2
				""", "loginIdentifier", normalizedIdentifier);
		// Person emails are unique only inside a tenant. Never guess a tenant when
		// the same email identifies more than one active account.
		return emailMatches.size() == 1 ? Optional.of(emailMatches.getFirst()) : Optional.empty();
	}

	public Optional<AuthenticatedUser> findActiveUserById(UUID userId) {
		return findUser("""
				SELECT ua.id, ua.account_scope, ua.organization_id, ua.username, ua.password_hash, ua.credential_version,
				       TRIM(p.first_name || ' ' || p.last_name || ' ' || COALESCE(p.maternal_last_name, '')) AS full_name
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
				FROM user_branch_access uba
				JOIN branch b ON b.id = uba.branch_id
				WHERE uba.user_id = :userId
				  AND uba.branch_id = :branchId
				  AND b.status = 'ACTIVE'
				  AND b.deleted_at IS NULL
				""")
				.param("userId", userId)
				.param("branchId", branchId)
				.query(Integer.class)
				.single();
		return count > 0;
	}

	public boolean userHasOrganizationAccess(UUID userId, UUID organizationId) {
		Integer count = jdbcClient.sql("""
				SELECT COUNT(*)
				FROM user_branch_access uba
				JOIN branch b ON b.id = uba.branch_id
				WHERE uba.user_id = :userId
				  AND b.organization_id = :organizationId
				  AND b.status = 'ACTIVE'
				  AND b.deleted_at IS NULL
				""")
				.param("userId", userId)
				.param("organizationId", organizationId)
				.query(Integer.class)
				.single();
		return count > 0;
	}

	public boolean branchBelongsToOrganization(UUID branchId, UUID organizationId) {
		Integer count = jdbcClient.sql("""
				SELECT COUNT(*)
				FROM branch
				WHERE id = :branchId
				  AND organization_id = :organizationId
				  AND status = 'ACTIVE'
				  AND deleted_at IS NULL
				""")
				.param("branchId", branchId)
				.param("organizationId", organizationId)
				.query(Integer.class)
				.single();
		return count > 0;
	}

	public boolean organizationIsActive(UUID organizationId) {
		Integer count = jdbcClient.sql("""
				SELECT COUNT(*)
				FROM organization
				WHERE id = :organizationId
				  AND status = 'ACTIVE'
				  AND deleted_at IS NULL
				""")
				.param("organizationId", organizationId)
				.query(Integer.class)
				.single();
		return count > 0;
	}

	public Optional<UUID> firstActiveBranch(UUID organizationId) {
		return jdbcClient.sql("""
				SELECT id
				FROM branch
				WHERE organization_id = :organizationId
				  AND status = 'ACTIVE'
				  AND deleted_at IS NULL
				ORDER BY name
				LIMIT 1
				""")
				.param("organizationId", organizationId)
				.query(UUID.class)
				.optional();
	}

	public Optional<UUID> firstAccessibleBranch(UUID userId, UUID organizationId) {
		return jdbcClient.sql("""
				SELECT b.id
				FROM user_branch_access uba
				JOIN branch b ON b.id = uba.branch_id
				WHERE uba.user_id = :userId
				  AND b.organization_id = :organizationId
				  AND b.status = 'ACTIVE'
				  AND b.deleted_at IS NULL
				ORDER BY b.name
				LIMIT 1
				""")
				.param("userId", userId)
				.param("organizationId", organizationId)
				.query(UUID.class)
				.optional();
	}

	public Optional<OrganizationTheme> organizationTheme(UUID organizationId) {
		if (organizationId == null) {
			return Optional.empty();
		}
		return jdbcClient.sql("""
				SELECT trade_name, brand_name, logo_url, primary_color, accent_color, surface_color
				FROM organization
				WHERE id = :organizationId
				  AND deleted_at IS NULL
				""")
				.param("organizationId", organizationId)
				.query((resultSet, rowNumber) -> new OrganizationTheme(
						resultSet.getString("trade_name"),
						resultSet.getString("brand_name"),
						resultSet.getString("logo_url"),
						resultSet.getString("primary_color"),
						resultSet.getString("accent_color"),
						resultSet.getString("surface_color")
				))
				.optional();
	}

	private Optional<AuthenticatedUser> findUser(String sql, String parameterName, Object parameterValue) {
		return findUsers(sql, parameterName, parameterValue).stream().findFirst();
	}

	private List<AuthenticatedUser> findUsers(String sql, String parameterName, Object parameterValue) {
		return jdbcClient.sql(sql)
				.param(parameterName, parameterValue)
				.query((resultSet, rowNumber) -> new AuthenticatedUser(
						resultSet.getObject("id", UUID.class),
						AccountScope.valueOf(resultSet.getString("account_scope")),
						resultSet.getObject("organization_id", UUID.class),
						resultSet.getString("username"),
						resultSet.getString("password_hash"),
						resultSet.getString("full_name"),
						findPermissions(resultSet.getObject("id", UUID.class)),
						findRoles(resultSet.getObject("id", UUID.class)),
						resultSet.getLong("credential_version")
				))
				.list();
	}

	private Set<String> findPermissions(UUID userId) {
		return new LinkedHashSet<>(jdbcClient.sql("""
				SELECT DISTINCT p.code
				""" + SCOPED_ROLES + """
				JOIN role_permission rp ON rp.role_id = r.id
				JOIN permission p ON p.id = rp.permission_id AND p.deleted_at IS NULL
				WHERE ur.user_id = :userId
				  AND (u.account_scope = 'PLATFORM' OR p.code NOT LIKE 'platform.%')
				ORDER BY p.code
				""")
				.param("userId", userId)
				.query(String.class)
				.list());
	}

	private Set<String> findRoles(UUID userId) {
		return new LinkedHashSet<>(jdbcClient.sql("""
				SELECT DISTINCT r.name
				""" + SCOPED_ROLES + """
				WHERE ur.user_id = :userId
				ORDER BY r.name
				""")
				.param("userId", userId)
				.query(String.class)
				.list());
	}

	public record AuthenticatedUser(
			UUID userId,
			AccountScope accountScope,
			UUID organizationId,
			String username,
			String passwordHash,
			String fullName,
			Set<String> permissions,
			Set<String> roles,
			long credentialVersion
	) {
		public AuthenticatedUser(UUID userId, AccountScope accountScope, UUID organizationId, String username,
				String passwordHash, String fullName, Set<String> permissions, Set<String> roles) {
			this(userId, accountScope, organizationId, username, passwordHash, fullName, permissions, roles, 0);
		}
		public AuthenticatedUser(
				UUID userId,
				UUID organizationId,
				String username,
				String passwordHash,
				String fullName,
				Set<String> permissions,
				Set<String> roles
		) {
			this(userId, AccountScope.TENANT, organizationId, username, passwordHash, fullName, permissions, roles);
		}

		public GymOsPrincipal toPrincipal(UUID branchId) {
			if (accountScope == AccountScope.PLATFORM) {
				return new GymOsPrincipal(
						userId, accountScope, null, null, username, fullName,
						platformPermissions(), roles, false, null, credentialVersion);
			}
			return new GymOsPrincipal(
					userId, accountScope, organizationId, branchId, username, fullName,
					permissions, roles, false, null, credentialVersion);
		}

		public GymOsPrincipal toSupportPrincipal(UUID activeOrganizationId, UUID branchId, Instant expiresAt) {
			if (accountScope != AccountScope.PLATFORM) {
				throw new IllegalStateException("Only platform identities can enter support mode");
			}
			return new GymOsPrincipal(
					userId, accountScope, activeOrganizationId, branchId, username, fullName,
					tenantSupportPermissions(), roles, true, expiresAt, credentialVersion);
		}

		public boolean platformAdmin() {
			return accountScope == AccountScope.PLATFORM && permissions.contains("platform.admin");
		}

		private Set<String> platformPermissions() {
			return permissions.stream()
					.filter(permission -> permission.startsWith("platform."))
					.collect(java.util.stream.Collectors.toUnmodifiableSet());
		}

		private Set<String> tenantSupportPermissions() {
			return permissions.stream()
					.filter(permission -> !permission.startsWith("platform."))
					.collect(java.util.stream.Collectors.toUnmodifiableSet());
		}
	}

	public record OrganizationTheme(
			String organizationName,
			String brandName,
			String logoUrl,
			String primaryColor,
			String accentColor,
			String surfaceColor
	) {
	}
}
