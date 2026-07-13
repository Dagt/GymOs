package com.gymos.organizations.infrastructure;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class OrganizationQueryRepository {
	private final JdbcClient jdbcClient;

	public OrganizationQueryRepository(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	public Optional<OrganizationSummary> findCurrentOrganization(UUID organizationId) {
		return jdbcClient.sql("""
				SELECT id, legal_name, trade_name, status, timezone, default_currency
				FROM organization
				WHERE id = :organizationId
				  AND deleted_at IS NULL
				""")
				.param("organizationId", organizationId)
				.query((resultSet, rowNumber) -> new OrganizationSummary(
						resultSet.getObject("id", UUID.class),
						resultSet.getString("legal_name"),
						resultSet.getString("trade_name"),
						resultSet.getString("status"),
						resultSet.getString("timezone"),
						resultSet.getString("default_currency")
				))
				.optional();
	}

	public List<BranchSummary> findBranchesForUser(UUID userId, UUID organizationId) {
		return jdbcClient.sql("""
				SELECT b.id, b.name, b.code, b.status, b.timezone, uba.access_scope
				FROM user_branch_access uba
				JOIN branch b ON b.id = uba.branch_id
				WHERE uba.user_id = :userId
				  AND b.organization_id = :organizationId
				  AND b.deleted_at IS NULL
				ORDER BY b.name
				""")
				.param("userId", userId)
				.param("organizationId", organizationId)
				.query((resultSet, rowNumber) -> new BranchSummary(
						resultSet.getObject("id", UUID.class),
						resultSet.getString("name"),
						resultSet.getString("code"),
						resultSet.getString("status"),
						resultSet.getString("timezone"),
						resultSet.getString("access_scope")
				))
				.list();
	}

	public record OrganizationSummary(
			UUID id,
			String legalName,
			String tradeName,
			String status,
			String timezone,
			String defaultCurrency
	) {
	}

	public record BranchSummary(
			UUID id,
			String name,
			String code,
			String status,
			String timezone,
			String accessScope
	) {
	}
}
