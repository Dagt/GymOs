package com.gymos.reporting.infrastructure;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class OwnerDashboardRepository {
	private final JdbcClient jdbcClient;

	public OwnerDashboardRepository(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	public OwnerDashboardRecord dashboard(UUID organizationId, UUID branchId, LocalDate fromDate, LocalDate toDate) {
		OffsetDateTime from = fromDate.atStartOfDay().atOffset(ZoneOffset.UTC);
		OffsetDateTime toExclusive = toDate.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC);
		return new OwnerDashboardRecord(
				fromDate,
				toDate,
				longValue("""
						SELECT COALESCE(SUM(total_cents), 0)
						FROM sale
						WHERE organization_id = :organizationId
						  AND branch_id = :branchId
						  AND status = 'PAID'
						  AND sold_at >= :from
						  AND sold_at < :toExclusive
						  AND deleted_at IS NULL
						""", organizationId, branchId, from, toExclusive),
				longValue("""
						SELECT COUNT(*)
						FROM sale
						WHERE organization_id = :organizationId
						  AND branch_id = :branchId
						  AND sold_at >= :from
						  AND sold_at < :toExclusive
						  AND deleted_at IS NULL
						""", organizationId, branchId, from, toExclusive),
				longValue("""
						SELECT COUNT(*)
						FROM client
						WHERE organization_id = :organizationId
						  AND joined_at >= :from
						  AND joined_at < :toExclusive
						  AND deleted_at IS NULL
						""", organizationId, branchId, from, toExclusive),
				longValue("""
						SELECT COUNT(*)
						FROM membership
						WHERE organization_id = :organizationId
						  AND branch_id = :branchId
						  AND status = 'ACTIVE'
						  AND deleted_at IS NULL
						""", organizationId, branchId, from, toExclusive),
				longValue("""
						SELECT COUNT(*)
						FROM booking
						WHERE organization_id = :organizationId
						  AND branch_id = :branchId
						  AND starts_at >= :from
						  AND starts_at < :toExclusive
						  AND deleted_at IS NULL
						""", organizationId, branchId, from, toExclusive),
				longValue("""
						SELECT COUNT(*)
						FROM inventory_stock
						WHERE organization_id = :organizationId
						  AND branch_id = :branchId
						  AND reorder_point > 0
						  AND quantity_on_hand <= reorder_point
						  AND deleted_at IS NULL
						""", organizationId, branchId, from, toExclusive),
				longValue("""
						SELECT COUNT(*)
						FROM workout_assignment
						WHERE organization_id = :organizationId
						  AND branch_id = :branchId
						  AND status = 'ACTIVE'
						  AND deleted_at IS NULL
						""", organizationId, branchId, from, toExclusive),
				longValue("""
						SELECT COUNT(*)
						FROM professional
						WHERE organization_id = :organizationId
						  AND branch_id = :branchId
						  AND status = 'ACTIVE'
						  AND deleted_at IS NULL
						""", organizationId, branchId, from, toExclusive),
				longValue("""
						SELECT COUNT(*)
						FROM notification_log
						WHERE organization_id = :organizationId
						  AND (branch_id = :branchId OR branch_id IS NULL)
						  AND created_at >= :from
						  AND created_at < :toExclusive
						""", organizationId, branchId, from, toExclusive),
				longValue("""
						SELECT COUNT(*)
						FROM automation_rule
						WHERE organization_id = :organizationId
						  AND (branch_id = :branchId OR branch_id IS NULL)
						  AND status = 'ENABLED'
						  AND deleted_at IS NULL
						""", organizationId, branchId, from, toExclusive)
		);
	}

	private long longValue(String sql, UUID organizationId, UUID branchId, OffsetDateTime from, OffsetDateTime toExclusive) {
		return jdbcClient.sql(sql)
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("from", from)
				.param("toExclusive", toExclusive)
				.query(Long.class)
				.single();
	}

	public record OwnerDashboardRecord(
			LocalDate fromDate,
			LocalDate toDate,
			long paidRevenueCents,
			long saleCount,
			long newClients,
			long activeMemberships,
			long bookingCount,
			long lowStockItems,
			long activeWorkoutAssignments,
			long activeProfessionals,
			long queuedNotifications,
			long enabledAutomations
	) {
	}
}
