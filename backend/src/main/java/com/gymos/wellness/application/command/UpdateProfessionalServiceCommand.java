package com.gymos.wellness.application.command;

public record UpdateProfessionalServiceCommand(
		String name,
		String serviceType,
		int durationMinutes,
		long priceCents,
		String currency,
		String status
) {
}
