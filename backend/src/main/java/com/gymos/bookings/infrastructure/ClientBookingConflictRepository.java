package com.gymos.bookings.infrastructure;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class ClientBookingConflictRepository {
	private final JdbcClient jdbcClient;

	public ClientBookingConflictRepository(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	public boolean hasOverlappingBooking(
			UUID organizationId,
			UUID branchId,
			UUID clientId,
			OffsetDateTime startsAt,
			OffsetDateTime endsAt
	) {
		Integer count = jdbcClient.sql("""
				SELECT COUNT(*)
				FROM booking
				WHERE organization_id = :organizationId
				  AND branch_id = :branchId
				  AND client_id = :clientId
				  AND status IN ('RESERVED', 'CONFIRMED', 'CHECKED_IN')
				  AND starts_at < :endsAt
				  AND ends_at > :startsAt
				  AND deleted_at IS NULL
				""")
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("clientId", clientId)
				.param("startsAt", startsAt)
				.param("endsAt", endsAt)
				.query(Integer.class)
				.single();
		return count > 0;
	}
}
