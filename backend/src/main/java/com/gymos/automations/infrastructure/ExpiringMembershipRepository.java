package com.gymos.automations.infrastructure;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class ExpiringMembershipRepository {
	private final JdbcClient jdbcClient;

	public ExpiringMembershipRepository(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	public List<ExpiringMembership> find(
			UUID organizationId,
			UUID branchId,
			LocalDate targetDate,
			int limit
	) {
		return jdbcClient.sql("""
				SELECT id, client_id, plan_id, ends_on
				FROM membership
				WHERE organization_id = :organizationId
				  AND branch_id = :branchId
				  AND deleted_at IS NULL
				  AND status IN ('ACTIVE', 'GRACE')
				  AND ends_on = :targetDate
				ORDER BY id
				LIMIT :limit
				""")
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("targetDate", targetDate)
				.param("limit", Math.max(1, Math.min(limit, 1000)))
				.query((rs, rowNum) -> new ExpiringMembership(
						rs.getObject("id", UUID.class),
						rs.getObject("client_id", UUID.class),
						rs.getObject("plan_id", UUID.class),
						rs.getObject("ends_on", LocalDate.class)
				))
				.list();
	}

	public record ExpiringMembership(UUID id, UUID clientId, UUID planId, LocalDate endsOn) {}
}
