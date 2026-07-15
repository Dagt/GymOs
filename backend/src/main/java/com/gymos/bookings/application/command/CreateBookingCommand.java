package com.gymos.bookings.application.command;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CreateBookingCommand(
		UUID resourceId,
		UUID clientId,
		OffsetDateTime startsAt,
		OffsetDateTime endsAt,
		String notes
) {
}
