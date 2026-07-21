package com.gymos.bookings.api.operations;

import com.gymos.bookings.application.BookingService;
import com.gymos.bookings.application.command.CreateBookingCommand;
import com.gymos.common.security.GymOsPrincipal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class BookingOperationsAdapter implements BookingOperations {
	private final BookingService bookingService;

	BookingOperationsAdapter(BookingService bookingService) {
		this.bookingService = bookingService;
	}

	@Override
	public List<BookingResource> resources(UUID organizationId, UUID branchId) {
		return bookingService.resources(principal(organizationId, branchId)).stream()
				.map(resource -> new BookingResource(
						resource.id(),
						resource.name(),
						resource.resourceType(),
						resource.capacity(),
						resource.slotMinutes(),
						resource.status(),
						resource.opensAt(),
						resource.closesAt(),
						resource.notes(),
						resource.allowedPlanIds(),
						resource.allowedPlanNames()
				))
				.toList();
	}

	@Override
	public List<BookingAvailability> availability(UUID organizationId, UUID branchId, UUID resourceId, LocalDate date) {
		return bookingService.availability(principal(organizationId, branchId), resourceId, date).stream()
				.map(slot -> new BookingAvailability(
						slot.startsAt(),
						slot.endsAt(),
						slot.reserved(),
						slot.capacity(),
						slot.available()
				))
				.toList();
	}

	@Override
	public BookingResult createBooking(
			UUID organizationId,
			UUID branchId,
			UUID clientId,
			UUID resourceId,
			OffsetDateTime startsAt,
			OffsetDateTime endsAt,
			String notes,
			String correlationId
	) {
		var booking = bookingService.createBooking(
				principal(organizationId, branchId),
				new CreateBookingCommand(resourceId, clientId, startsAt, endsAt, notes),
				correlationId
		);
		return new BookingResult(
				booking.id(),
				booking.resourceId(),
				booking.resourceName(),
				booking.clientId(),
				booking.clientName(),
				booking.status(),
				booking.startsAt(),
				booking.endsAt(),
				booking.notes(),
				booking.bookingPaymentId(),
				booking.saleId(),
				booking.paymentStatus(),
				booking.saleNumber()
		);
	}

	private GymOsPrincipal principal(UUID organizationId, UUID branchId) {
		return new GymOsPrincipal(null, organizationId, branchId, "self-service-kiosk", "Kiosco", Set.of());
	}
}
