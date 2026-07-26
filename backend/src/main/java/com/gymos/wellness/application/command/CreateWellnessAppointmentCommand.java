package com.gymos.wellness.application.command;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CreateWellnessAppointmentCommand(
		UUID professionalId,
		UUID serviceId,
		UUID clientId,
		OffsetDateTime startsAt,
		String notes
) {
}
