package com.gymos.wellness.application.command;

public record CreateProfessionalServiceCommand(
		String name,
		String serviceType,
		int durationMinutes,
		long priceCents,
		String currency
) {
}
