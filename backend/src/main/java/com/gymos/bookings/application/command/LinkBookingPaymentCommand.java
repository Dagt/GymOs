package com.gymos.bookings.application.command;

import java.time.OffsetDateTime;
import java.util.UUID;

public record LinkBookingPaymentCommand(
		UUID saleId,
		String status,
		OffsetDateTime expiresAt
) {
}
