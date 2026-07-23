package com.gymos.reporting.application.result;

import java.time.LocalDate;

public record OwnerDashboardResult(
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
