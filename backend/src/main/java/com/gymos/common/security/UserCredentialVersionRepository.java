package com.gymos.common.security;

import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Revalidates the signed context against live account and branch state on every request. */
@Repository
public class UserCredentialVersionRepository {
	private final JdbcClient jdbc;
	public UserCredentialVersionRepository(JdbcClient jdbc) { this.jdbc = jdbc; }

	public boolean isCurrent(GymOsPrincipal principal) {
		String sql = """
				SELECT EXISTS (
				    SELECT 1 FROM user_account u
				    WHERE u.id = :userId AND u.account_scope = :scope
				      AND u.status = 'ACTIVE' AND u.deleted_at IS NULL
				      AND u.credential_version = :version
				      AND u.organization_id IS NOT DISTINCT FROM :accountOrganizationId
				""";
		if (principal.organizationId() != null) {
			sql += """
				      AND EXISTS (SELECT 1 FROM organization o WHERE o.id = :organizationId
				                  AND o.status = 'ACTIVE' AND o.deleted_at IS NULL)
					""";
		}
		if (principal.branchId() != null) {
			sql += """
				      AND EXISTS (SELECT 1 FROM branch b WHERE b.id = :branchId
				                  AND b.organization_id = :organizationId AND b.status = 'ACTIVE' AND b.deleted_at IS NULL)
					""";
			if (principal.tenantIdentity()) {
				sql += """
					      AND EXISTS (SELECT 1 FROM user_branch_access uba
					                  WHERE uba.user_id = u.id AND uba.branch_id = :branchId)
						""";
			}
		}
		var query = jdbc.sql(sql + ")")
				.param("userId", principal.userId())
				.param("scope", principal.accountScope().name())
				.param("version", principal.credentialVersion())
				.param("accountOrganizationId", principal.tenantIdentity() ? principal.organizationId() : null);
		if (principal.organizationId() != null) {
			query = query.param("organizationId", principal.organizationId());
		}
		if (principal.branchId() != null) {
			query = query.param("branchId", principal.branchId());
		}
		return query.query(Boolean.class).single();
	}

	public boolean isCurrent(UUID userId, long version) {
		return jdbc.sql("""
				SELECT credential_version FROM user_account
				WHERE id = :userId AND status = 'ACTIVE' AND deleted_at IS NULL
				""").param("userId", userId).query(Long.class).optional()
				.filter(current -> current == version).isPresent();
	}
}
