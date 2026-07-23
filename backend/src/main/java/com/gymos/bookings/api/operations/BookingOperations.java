package com.gymos.bookings.api.operations;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface BookingOperations {
	List<BookingResource> resources(UUID organizationId, UUID branchId);

	List<BookingAvailability> availability(UUID organizationId, UUID branchId, UUID resourceId, LocalDate date);

	BookingResult createBooking(
			UUID organizationId,
			UUID branchId,
			UUID clientId,
			UUID resourceId,
			OffsetDateTime startsAt,
			OffsetDateTime endsAt,
			String notes,
			String correlationId
	);

	record BookingResource(
			UUID id,
			String name,
			String resourceType,
			int capacity,
			int slotMinutes,
			String status,
			LocalTime opensAt,
			LocalTime closesAt,
			String notes,
			List<UUID> allowedPlanIds,
			String allowedPlanNames
	) {
	}

	record BookingAvailability(
			OffsetDateTime startsAt,
			OffsetDateTime endsAt,
			int reserved,
			int capacity,
			boolean available
	) {
	}

	record BookingResult(
			UUID id,
			UUID resourceId,
			String resourceName,
			UUID clientId,
			String clientName,
			String status,
			OffsetDateTime startsAt,
			OffsetDateTime endsAt,
			String notes,
			UUID bookingPaymentId,
			UUID saleId,
			String paymentStatus,
			String saleNumber
	) {
	}
}
