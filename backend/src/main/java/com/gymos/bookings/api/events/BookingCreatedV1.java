package com.gymos.bookings.api.events;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.UUID;

public record BookingCreatedV1(
		String eventKey,
		UUID organizationId,
		UUID branchId,
		UUID bookingId,
		UUID clientId,
		UUID resourceId,
		OffsetDateTime startsAt,
		OffsetDateTime endsAt,
		OffsetDateTime occurredAt
) implements Serializable {
}
