package com.gymos.selfservice.application.result;

import java.util.UUID;

public record SelfServicePlanOptionView(
		UUID id,
		UUID productId,
		String name,
		long priceCents,
		String currency,
		int durationDays,
		int graceDays
) {
}
