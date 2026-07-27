package com.gymos.wellness.application.command;

import java.time.OffsetDateTime;

public record ConfirmWellnessAppointmentCommand(
		OffsetDateTime startsAt,
		String notes
) {
}
